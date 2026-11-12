package com.deskcal.caldav

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

data class DiscoveredCalendar(
    val path: String,
    val url: HttpUrl,
    val displayName: String,
    val color: String?,
)

class CalDavClient private constructor(
    val baseUrl: HttpUrl,
    private val username: String,
    private val password: String,
    trustSelfSigned: Boolean,
) {

    private val client: OkHttpClient

    init {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            // DAV discovery relies on redirects (e.g. /.well-known/caldav -> /user/).
            // OkHttp converts a redirected PROPFIND/REPORT into a GET, which makes the
            // server answer with HTML instead of XML. Redirects are therefore followed
            // manually, preserving the original request method.
            .followRedirects(false)
            .followSslRedirects(false)
        if (trustSelfSigned) {
            val trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf(trustManager), SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustManager)
            builder.hostnameVerifier { _, _ -> true }
        }
        client = builder.build()
    }

    fun resolve(path: String): HttpUrl? = baseUrl.resolve(path)

    suspend fun discover(): List<DiscoveredCalendar> = withContext(Dispatchers.IO) {
        val rootWellKnown = HttpUrl.Builder()
            .scheme(baseUrl.scheme)
            .host(baseUrl.host)
            .port(baseUrl.port)
            .addPathSegments(".well-known/caldav")
            .build()
        val baseWellKnown = baseUrl.newBuilder().addPathSegments(".well-known/caldav").build()

        var principalUrl: HttpUrl? = null
        for (candidate in listOf(rootWellKnown, baseWellKnown)) {
            try {
                val doc = propfind(candidate, depth = 0)
                principalUrl = extractPropHref(doc, "current-user-principal")?.let { baseUrl.resolve(it) }
                if (principalUrl != null) break
            } catch (e: CalDavException) {
                if (e is CalDavException.Auth || e is CalDavException.Tls) throw e
            }
        }
        if (principalUrl == null) principalUrl = baseUrl

        val homeUrl: HttpUrl = try {
            val doc = propfind(principalUrl, depth = 0)
            extractPropHref(doc, "calendar-home-set")?.let { baseUrl.resolve(it) } ?: principalUrl
        } catch (e: CalDavException) {
            if (e is CalDavException.Auth || e is CalDavException.Tls) throw e
            principalUrl
        }

        val homeDoc = propfind(homeUrl, depth = 1)
        parseCalendarCollections(homeDoc, homeUrl)
    }

    suspend fun fetchEvents(
        calendarUrl: HttpUrl,
        rangeStart: Long,
        rangeEnd: Long,
    ): List<String> = withContext(Dispatchers.IO) {
        val body = calendarQueryBody(rangeStart, rangeEnd).toRequestBody(XML_MEDIA_TYPE)
        var currentUrl = calendarUrl
        var redirects = 0
        var xml = ""
        while (true) {
            val request = newRequest(currentUrl)
                .method("REPORT", body)
                .header("Depth", "1")
                .build()
            val (code, location, text) = executeRaw(request)
            if (code == 207 || code == 200) {
                xml = text
                break
            }
            if (code in 300..399 && redirects < MAX_REDIRECTS) {
                val target = location?.let { currentUrl.resolve(it) }
                if (target == null) {
                    throw CalDavException.HttpError(code, "Redirect without valid location (HTTP $code)")
                }
                currentUrl = target
                redirects++
                continue
            }
            throw httpError(code, currentUrl)
        }
        val doc = try {
            parseXml(xml)
        } catch (e: Exception) {
            throw CalDavException.Parse("Invalid XML in REPORT response: ${e.message}")
        }
        doc.descendantElements("response").mapNotNull { response ->
            response.descendantText("calendar-data")?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    private suspend fun propfind(url: HttpUrl, depth: Int): org.w3c.dom.Document =
        withContext(Dispatchers.IO) {
            var currentUrl = url
            var redirects = 0
            var xml = ""
            while (true) {
                val body = PROPFIND_BODY.toRequestBody(XML_MEDIA_TYPE)
                val request = newRequest(currentUrl)
                    .method("PROPFIND", body)
                    .header("Depth", depth.toString())
                    .build()
                val (code, location, text) = executeRaw(request)
                if (code == 207 || code == 200) {
                    xml = text
                    break
                }
                if (code in 300..399 && redirects < MAX_REDIRECTS) {
                    val target = location?.let { currentUrl.resolve(it) }
                    if (target == null) {
                        throw CalDavException.HttpError(code, "Redirect without valid location (HTTP $code)")
                    }
                    currentUrl = target
                    redirects++
                    continue
                }
                throw httpError(code, currentUrl)
            }
            try {
                parseXml(xml)
            } catch (e: Exception) {
                throw CalDavException.Parse("Invalid XML in PROPFIND response: ${e.message}")
            }
        }

    private fun executeRaw(request: Request): Triple<Int, String?, String> {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            return Triple(response.code, response.header("Location"), text)
        }
    }

    private fun httpError(code: Int, url: HttpUrl): CalDavException = when {
        code == 401 || code == 403 -> CalDavException.Auth("HTTP $code")
        code == 404 -> CalDavException.NotFound("HTTP 404 for $url")
        else -> CalDavException.HttpError(code, "HTTP $code for $url")
    }

    private fun newRequest(url: HttpUrl): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(username, password))
            .header("Accept", "application/xml, text/xml, */*")

    companion object {
        private val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
        private const val MAX_REDIRECTS = 5

        fun create(
            serverUrl: String,
            username: String,
            password: String,
            trustSelfSigned: Boolean,
        ): CalDavClient {
            var normalized = serverUrl.trim().trimEnd('/') + "/"
            if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
                normalized = "https://$normalized"
            }
            return CalDavClient(normalized.toHttpUrl(), username, password, trustSelfSigned)
        }

        private const val PROPFIND_BODY =
            """<?xml version="1.0" encoding="utf-8"?>""" +
                """<D:propfind xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">""" +
                """<D:prop><D:current-user-principal/><C:calendar-home-set/>""" +
                """<D:resourcetype/><D:displayname/></D:prop></D:propfind>"""

        fun calendarQueryBody(rangeStart: Long, rangeEnd: Long): String {
            val start = icalUtc(rangeStart)
            val end = icalUtc(rangeEnd)
            return """<?xml version="1.0" encoding="utf-8"?>""" +
                """<C:calendar-query xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">""" +
                """<D:prop><D:getetag/><C:calendar-data/></D:prop>""" +
                """<C:filter><C:comp-filter name="VCALENDAR"><C:comp-filter name="VEVENT">""" +
                """<C:time-range start="$start" end="$end"/>""" +
                """</C:comp-filter></C:comp-filter></C:filter></C:calendar-query>"""
        }

        fun icalUtc(millis: Long): String {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            cal.timeInMillis = millis
            fun two(n: Int) = n.toString().padStart(2, '0')
            return buildString {
                append(cal.get(Calendar.YEAR))
                append(two(cal.get(Calendar.MONTH) + 1))
                append(two(cal.get(Calendar.DAY_OF_MONTH)))
                append('T')
                append(two(cal.get(Calendar.HOUR_OF_DAY)))
                append(two(cal.get(Calendar.MINUTE)))
                append(two(cal.get(Calendar.SECOND)))
                append('Z')
            }
        }
    }
}
