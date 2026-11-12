package com.deskcal.caldav

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFailsWith
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class XmlDavTest {

    private val multistatus = """
        <?xml version='1.0' encoding='utf-8'?>
        <D:multistatus xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
          <D:response>
            <D:href>/alice/</D:href>
            <D:propstat>
              <D:prop>
                <D:resourcetype><D:collection/></D:resourcetype>
                <D:displayname>alice</D:displayname>
                <D:current-user-principal><D:href>/alice/</D:href></D:current-user-principal>
                <C:calendar-home-set><D:href>/alice/</D:href></C:calendar-home-set>
              </D:prop>
              <D:status>HTTP/1.1 200 OK</D:status>
            </D:propstat>
          </D:response>
          <D:response>
            <D:href>/alice/personal/</D:href>
            <D:propstat>
              <D:prop>
                <D:resourcetype><D:collection/><C:calendar/></D:resourcetype>
                <D:displayname>Personal</D:displayname>
              </D:prop>
              <D:status>HTTP/1.1 200 OK</D:status>
            </D:propstat>
          </D:response>
          <D:response>
            <D:href>/alice/work/</D:href>
            <D:propstat>
              <D:prop>
                <D:resourcetype><D:collection/><C:calendar/></D:resourcetype>
                <D:displayname>Work</D:displayname>
              </D:prop>
              <D:status>HTTP/1.1 200 OK</D:status>
            </D:propstat>
          </D:response>
          <D:response>
            <D:href>/alice/notes.txt</D:href>
            <D:propstat>
              <D:prop><D:resourcetype/></D:prop>
              <D:status>HTTP/1.1 200 OK</D:status>
            </D:propstat>
          </D:response>
        </D:multistatus>
    """.trimIndent()

    @Test
    fun parsesMultistatusAndExtractsPrincipalHref() {
        val doc = parseXml(multistatus)
        assertEquals("/alice/", extractPropHref(doc, "current-user-principal"))
        assertEquals("/alice/", extractPropHref(doc, "calendar-home-set"))
    }

    @Test
    fun listsOnlyCalendarCollectionsUnderHome() {
        val doc = parseXml(multistatus)
        val home = "http://192.168.1.50:5232/alice/".toHttpUrl()
        val calendars = parseCalendarCollections(doc, home)
        assertEquals(2, calendars.size)
        assertEquals("/alice/personal/", calendars[0].path)
        assertEquals("Personal", calendars[0].displayName)
        assertEquals("/alice/work/", calendars[1].path)
        assertEquals("Work", calendars[1].displayName)
    }

    @Test
    fun extractsCalendarDataFromReportResponse() {
        val report = """
            <?xml version='1.0' encoding='utf-8'?>
            <D:multistatus xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
              <D:response>
                <D:href>/alice/personal/event1.ics</D:href>
                <D:propstat>
                  <D:prop>
                    <C:calendar-data>BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:e1
            DTSTART:20240610T090000Z
            SUMMARY:Standup
            END:VEVENT
            END:VCALENDAR</C:calendar-data>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()
        val doc = parseXml(report)
        val bodies = doc.descendantElements("response").mapNotNull { it.descendantText("calendar-data") }
        assertEquals(1, bodies.size)
        assertTrue(bodies[0].contains("BEGIN:VEVENT"))
        assertTrue(bodies[0].contains("SUMMARY:Standup"))
    }

    @Test
    fun rejectsDoctypeDeclarations() {
        assertFailsWith<IllegalArgumentException> {
            parseXml("<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><D:multistatus xmlns:D=\"DAV:\"/>")
        }
    }

    @Test
    fun calendarQueryBodyUsesUtcTimeRange() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(2024, Calendar.JUNE, 1, 0, 0, 0)
        val start = cal.timeInMillis
        cal.set(2024, Calendar.JULY, 1, 0, 0, 0)
        val end = cal.timeInMillis
        val body = CalDavClient.calendarQueryBody(start, end)
        assertTrue(body.contains("<C:time-range start=\"20240601T000000Z\" end=\"20240701T000000Z\"/>"))
        assertTrue(body.contains("calendar-query"))
        assertTrue(body.contains("comp-filter name=\"VEVENT\""))
    }
}
