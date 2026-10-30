package com.mathtutor.app

import com.mathtutor.app.ai.sourceChunks
import com.mathtutor.app.core.*
import com.mathtutor.app.data.StudyEvent
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CoreTest {
    @Test fun dateRangeIncludesBothLocalDatesButNotNextMidnight() {
        val range = DatedRange(LocalDate.parse("2026-10-20"), LocalDate.parse("2026-10-28"), ZoneId.of("Asia/Shanghai"))
        assertTrue(range.contains(Instant.parse("2026-10-19T16:00:00Z").toEpochMilli()))
        assertTrue(range.contains(Instant.parse("2026-10-28T15:59:59.999Z").toEpochMilli()))
        assertFalse(range.contains(Instant.parse("2026-10-28T16:00:00Z").toEpochMilli()))
    }
    @Test fun dateRangeUsesCalendarDaysAcrossDaylightSaving() {
        val range = DatedRange(LocalDate.parse("2026-03-08"), LocalDate.parse("2026-03-08"), ZoneId.of("America/New_York"))
        assertEquals(23 * 60 * 60 * 1000L, range.endExclusive - range.startMillis)
    }
    @Test(expected = IllegalArgumentException::class) fun reversedRangeIsRejected() {
        DatedRange(LocalDate.parse("2026-10-28"), LocalDate.parse("2026-10-20"), ZoneId.of("Asia/Shanghai"))
    }
    @Test fun longHistoryRetainsLastRecordAndEveryEvidenceId() {
        val events = (1..80).map { index -> StudyEvent("message-$index", "q", "s", Kind.USER, index.toLong(), "x".repeat(1000)) }
        val chunks = sourceChunks(events)
        assertTrue(chunks.size > 1)
        val source = chunks.flatten().joinToString()
        events.forEach { assertTrue(source.contains("[${it.id}]")) }
        assertTrue(source.contains("message-80"))
    }
    @Test fun singleVeryLongMessageIsSegmentedWithoutLosingItsTail() {
        val event = StudyEvent("long-id", "q", "s", Kind.USER, 1, "a".repeat(40000) + "IMPORTANT_TAIL")
        val chunks = sourceChunks(listOf(event))
        assertTrue(chunks.size >= 4)
        assertTrue(chunks.all { it.sumOf(String::length) < 12000 })
        assertTrue(chunks.last().last().contains("IMPORTANT_TAIL"))
    }
    @Test fun unknownEvidenceIdsAreNeverAccepted() {
        assertEquals(listOf("real"), sanitizeEvidence(listOf("invented", "real", "real"), setOf("real")))
    }
    @Test fun directAnswerIsBlockedBeforeDisplay() {
        assertFalse(safeGuidance(TutorReply("最终答案是：3", "答案是3")))
        assertFalse(safeGuidance(TutorReply("x=3", "x等于3", givesFinalAnswer = true)))
        assertTrue(safeGuidance(TutorReply("你准备用哪个条件？", "你准备用哪个条件？")))
    }
    @Test fun invalidCropCoordinatesAreRejected() {
        assertFalse(Region(right = Float.NaN).valid())
        assertFalse(Region(left = .9f, right = .2f).valid())
        assertTrue(Region(.1f, .2f, .9f, .8f).valid())
    }
}
