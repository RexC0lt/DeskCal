package com.deskcal.caldav

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

sealed class CalDavException(message: String) : Exception(message) {

    class Unreachable(message: String) : CalDavException(message)
    class Tls(message: String) : CalDavException(message)
    class Auth(message: String) : CalDavException(message)
    class NotFound(message: String) : CalDavException(message)
    class HttpError(val code: Int, message: String) : CalDavException(message)
    class Parse(message: String) : CalDavException(message)

    companion object {
        fun from(e: Throwable): CalDavException = when (e) {
            is CalDavException -> e
            is SSLException -> Tls(
                "TLS/certificate error: ${e.message}. If your server uses a self-signed " +
                    "certificate, enable \"Trust self-signed certificates\" in the settings."
            )
            is UnknownHostException -> Unreachable(
                "Server host not found. Check the URL and that the phone is on the same network."
            )
            is ConnectException -> Unreachable(
                "Connection refused. Is Radicale running on that host and port?"
            )
            is SocketTimeoutException -> Unreachable(
                "Server did not respond in time. Check the URL and network."
            )
            is IOException -> Unreachable("Server unreachable: ${e.message}")
            else -> HttpError(-1, e.message ?: "Unknown error")
        }

        fun describe(e: Throwable): String {
            val c = from(e)
            return when (c) {
                is Unreachable -> c.message ?: "Server unreachable"
                is Tls -> c.message ?: "TLS error"
                is Auth -> "Wrong username or password (HTTP 401/403)."
                is NotFound -> "Not found on the server (HTTP 404). Check the base URL."
                is HttpError -> "Server returned HTTP ${c.code}."
                is Parse -> "Could not parse the server response: ${c.message}"
            }
        }
    }
}
