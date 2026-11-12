package com.deskcal.caldav

import okhttp3.HttpUrl
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

fun parseXml(xml: String): Document {
    if ("<!DOCTYPE" in xml.uppercase()) {
        throw IllegalArgumentException("DOCTYPE declarations are not allowed")
    }
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = true
    try {
        factory.isXIncludeAware = false
    } catch (_: Exception) {
    }
    try {
        factory.isExpandEntityReferences = false
    } catch (_: Exception) {
    }
    try {
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    } catch (_: Exception) {
    }
    try {
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    } catch (_: Exception) {
    }
    try {
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    } catch (_: Exception) {
    }
    val builder = factory.newDocumentBuilder()
    return builder.parse(InputSource(StringReader(xml)))
}

fun Node.descendantElements(localName: String): List<Element> {
    val list: NodeList = when (this) {
        is Document -> getElementsByTagNameNS("*", localName)
        is Element -> getElementsByTagNameNS("*", localName)
        else -> (ownerDocument ?: return emptyList()).getElementsByTagNameNS("*", localName)
    }
    return (0 until list.length).map { list.item(it) as Element }
}

fun Element.childElements(localName: String): List<Element> =
    childNodes.toElementList().filter { it.localName == localName }

fun NodeList.toElementList(): List<Element> =
    (0 until length).mapNotNull { item(it) as? Element }

fun Element.descendantText(localName: String): String? =
    descendantElements(localName).firstOrNull()?.textContent

fun extractPropHref(doc: Document, propLocalName: String): String? {
    val prop = doc.descendantElements(propLocalName).firstOrNull() ?: return null
    return prop.descendantElements("href").firstOrNull()?.textContent?.trim()
}

fun parseCalendarCollections(doc: Document, homeUrl: HttpUrl): List<DiscoveredCalendar> {
    val homePath = homeUrl.encodedPath.trimEnd('/')
    return doc.descendantElements("response").mapNotNull { response ->
        val href = response.childElements("href").firstOrNull()?.textContent?.trim().orEmpty()
        if (href.isEmpty()) return@mapNotNull null
        if (href.trimEnd('/') == homePath) return@mapNotNull null
        val resourceType = response.descendantElements("resourcetype").firstOrNull()
        val isCalendar = resourceType?.descendantElements("calendar")?.isNotEmpty() == true
        if (!isCalendar) return@mapNotNull null
        val url = homeUrl.resolve(href) ?: return@mapNotNull null
        val displayName =
            response.descendantElements("displayname").firstOrNull()?.textContent?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: href.trim('/').substringAfterLast('/')
        val color = response.descendantElements("calendar-color").firstOrNull()?.textContent?.trim()
            ?.takeIf { it.isNotBlank() }
        DiscoveredCalendar(
            path = url.encodedPath,
            url = url,
            displayName = displayName,
            color = color,
        )
    }
}
