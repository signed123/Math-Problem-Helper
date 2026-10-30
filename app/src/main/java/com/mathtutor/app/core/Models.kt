package com.mathtutor.app.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
fun newId(): String = UUID.randomUUID().toString()
fun now(): Long = Instant.now().toEpochMilli()

object Kind {
    const val QUESTION = "question"
    const val SESSION = "session"
    const val USER = "user"
    const val ASSISTANT = "assistant"
    const val ATTEMPT = "attempt"
    const val CARD = "card"
    const val STATUS = "status"
    const val ERROR = "error"
}
object Mastery {
    const val NEEDS_WORK = "needs_work"
    const val GUIDED = "guided"
    const val INDEPENDENT = "independent"
    fun label(value: String) = when (value) {
        GUIDED -> "提示后完成"
        INDEPENDENT -> "已独立验证"
        else -> "仍需巩固"
    }
}
@Serializable data class Region(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    fun valid(): Boolean = listOf(left, top, right, bottom).all { it.isFinite() && it in 0f..1f } && right - left >= .02f && bottom - top >= .02f
}
@Serializable data class DetectedQuestion(val text: String, val region: Region = Region(), val tags: List<String> = emptyList(), val needsClarification: Boolean = false, val clarification: String = "")
@Serializable data class Recognition(val questions: List<DetectedQuestion>, val note: String = "")
@Serializable data class QuestionContent(val text: String, val images: List<String> = emptyList(), val tags: List<String> = emptyList())
@Serializable data class SessionContent(val mode: String = "guided", val reviewBatchId: String? = null)
@Serializable data class StudentText(val text: String, val requestedHint: Int = 0, val action: String = "message")
@Serializable data class TutorReply(val displayMarkdown: String, val speechText: String, val action: String = "question", val hintLevel: Int = 0, val evidenceIds: List<String> = emptyList(), val givesFinalAnswer: Boolean = false)
@Serializable data class CheckResult(val verdict: String, val feedback: String, val firstIssue: String = "", val recognizedWork: String = "", val evidenceIds: List<String> = emptyList(), val complete: Boolean = false, val speechText: String = "")
@Serializable data class AttemptContent(val image: String, val result: CheckResult, val usedHints: Boolean)
@Serializable data class KnowledgeCard(val concepts: List<String> = emptyList(), val features: String = "", val approach: String = "", val stickingPoints: String = "", val nextQuestions: List<String> = emptyList(), val evidenceIds: List<String> = emptyList(), val studentReflection: String = "", val transfer: String = "", val speechText: String = "")
@Serializable data class StatusContent(val status: String, val reason: String)
@Serializable data class PracticeDraft(val text: String, val tags: List<String> = emptyList(), val referenceWork: String, val referenceAnswer: String)
@Serializable data class PracticeValidation(val verdict: String, val reason: String)
@Serializable data class GuidanceValidation(val safe: Boolean, val reason: String = "")
@Serializable data class ReviewSummary(val concepts: List<String> = emptyList(), val originalThinking: String = "", val stickingPoints: String = "", val approach: String = "", val nextSteps: String = "", val evidenceIds: List<String> = emptyList(), val speechText: String = "")
@Serializable data class ReviewOverview(val displayMarkdown: String, val speechText: String)

data class DatedRange(val start: LocalDate, val end: LocalDate, val zone: ZoneId) {
    init { require(!end.isBefore(start)) { "结束日期不能早于开始日期" } }
    val startMillis get() = start.atStartOfDay(zone).toInstant().toEpochMilli()
    val endExclusive get() = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    fun contains(time: Long) = time >= startMillis && time < endExclusive
}

/** Every source record is processed; the model context limit never trims persistent history. */
fun <T> chunkByBudget(items: List<T>, maxChars: Int = 12000, text: (T) -> String): List<List<T>> {
    require(maxChars > 0)
    val chunks = mutableListOf<List<T>>()
    var bucket = mutableListOf<T>()
    var size = 0
    for (item in items) {
        val length = text(item).length
        if (bucket.isNotEmpty() && size + length > maxChars) { chunks += bucket; bucket = mutableListOf(); size = 0 }
        bucket += item; size += length
    }
    if (bucket.isNotEmpty()) chunks += bucket
    return chunks
}

fun sanitizeEvidence(ids: List<String>, allowed: Set<String>) = ids.distinct().filter { it in allowed }
fun safeGuidance(reply: TutorReply): Boolean = reply.displayMarkdown.isNotBlank() && reply.speechText.isNotBlank() &&
    !reply.givesFinalAnswer && !Regex("(?:最终答案|标准答案|完整解答)\\s*[:：为是]").containsMatchIn(reply.displayMarkdown)
