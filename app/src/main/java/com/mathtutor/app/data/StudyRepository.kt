package com.mathtutor.app.data

import androidx.room.withTransaction
import com.mathtutor.app.core.*
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong

inline fun <reified T> StudyEvent.content(): T = AppJson.decodeFromString(payload)

data class QuestionView(val question: QuestionEntity, val history: List<StudyEvent>) {
    private val revisionEvent get() = history.lastOrNull { it.kind == Kind.QUESTION }
    val revision get() = revisionEvent?.content<QuestionContent>()
        ?: QuestionContent("尚未确认的题目")
    val status get() = history.lastOrNull { it.kind == Kind.STATUS && it.at >= (revisionEvent?.at ?: 0) }?.content<StatusContent>()?.status ?: Mastery.NEEDS_WORK
    val session get() = history.lastOrNull { it.kind == Kind.SESSION }
    val sessionEvents get() = history.filter { it.sessionId == session?.sessionId }
    val usedHints get() = sessionEvents.any {
        (it.kind == Kind.USER && it.content<StudentText>().requestedHint > 0) ||
            (it.kind == Kind.ASSISTANT && it.content<TutorReply>().hintLevel > 0)
    }
    val completedAttempt get() = sessionEvents.lastOrNull { it.kind == Kind.ATTEMPT && it.at > (revisionEvent?.at ?: 0) }?.content<AttemptContent>()?.result?.let { it.verdict == "correct" && it.complete } == true
    val card get() = history.lastOrNull { it.kind == Kind.CARD && it.at > (revisionEvent?.at ?: 0) }?.content<KnowledgeCard>()
    val lastActivity get() = history.lastOrNull { it.studentActivity }?.at ?: question.createdAt
}

class StudyRepository(val db: StudyDatabase) {
    val dao = db.dao()
    private val lastTime = AtomicLong(0)
    fun time(): Long = lastTime.updateAndGet { previous -> maxOf(now(), previous + 1) }
    val views = combine(dao.questions(), dao.events()) { questions, events ->
        val grouped = events.groupBy { it.questionId }
        questions.map { QuestionView(it, grouped[it.id].orEmpty()) }.sortedByDescending { it.lastActivity }
    }
    suspend inline fun <reified T> append(questionId: String, sessionId: String, kind: String, data: T, activity: Boolean = false): StudyEvent {
        val event = StudyEvent(newId(), questionId, sessionId, kind, time(), AppJson.encodeToString(data), activity)
        dao.insertEvent(event)
        return event
    }
    suspend fun create(content: QuestionContent, parentId: String? = null): String {
        require(content.text.isNotBlank()) { "请先确认题目内容" }
        val id = newId(); val session = newId()
        db.withTransaction {
            dao.insertQuestion(QuestionEntity(id, time(), parentId))
            append(id, session, Kind.QUESTION, content, true)
            append(id, session, Kind.SESSION, SessionContent(if (parentId == null) "guided" else "independent"))
            append(id, session, Kind.ASSISTANT, TutorReply("我已读到这道题。说说你的思路、已经做到哪一步，或者具体卡在哪里？", "我已读到这道题。说说你的思路，或者你卡在哪里？"))
        }
        return id
    }
    suspend fun view(id: String, cutoff: Long = Long.MAX_VALUE): QuestionView {
        val question = dao.allQuestions().firstOrNull { it.id == id } ?: error("题目已删除")
        return QuestionView(question, dao.history(id, cutoff))
    }
    suspend fun startAttempt(id: String, batchId: String? = null) {
        val session = newId()
        db.withTransaction {
            append(id, session, Kind.SESSION, SessionContent("independent", batchId))
            // Deliberately do not preload the old solution or tips into a fresh attempt.
            append(id, session, Kind.ASSISTANT, TutorReply("先独立尝试这道题。你可以提交作答照片，需要帮助时再点提示。", "先独立尝试，需要帮助时再点提示。"))
        }
    }
    suspend fun revise(id: String, content: QuestionContent) {
        val session = newId()
        db.withTransaction {
            append(id, session, Kind.QUESTION, content, true)
            append(id, session, Kind.SESSION, SessionContent())
            append(id, session, Kind.STATUS, StatusContent(Mastery.NEEDS_WORK, "题目条件已修正"))
            append(id, session, Kind.ASSISTANT, TutorReply("题目已更新。按现在的条件，你准备从哪里入手？", "题目已更新，你准备从哪里入手？"))
        }
    }
    suspend fun recordAttempt(id: String, image: String, result: CheckResult) {
        val view = view(id); val session = requireNotNull(view.session).sessionId
        db.withTransaction {
            append(id, session, Kind.ATTEMPT, AttemptContent(image, result, view.usedHints), true)
            if (result.verdict == "correct" && result.complete) {
                dao.completeDue(id, time())
                val state = if (view.usedHints) Mastery.GUIDED else Mastery.INDEPENDENT
                append(id, session, Kind.STATUS, StatusContent(state, "本次作答检查正确；是否使用提示：${view.usedHints}"))
                val zone = ZoneId.systemDefault()
                val day = Instant.ofEpochMilli(time()).atZone(zone).toLocalDate()
                for (offset in listOf(1, 3, 7)) {
                    dao.putSchedule(ReviewSchedule("$id:$session:$offset", id,
                        day.plusDays(offset.toLong()).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()))
                }
            } else if (result.verdict != "correct") {
                append(id, session, Kind.STATUS, StatusContent(Mastery.NEEDS_WORK, result.verdict))
            }
        }
    }
    suspend fun newBatch(range: DatedRange, ids: List<String>): String {
        val eligible = dao.activeQuestions(range.startMillis, range.endExclusive).map { it.id }.toSet()
        val selected = ids.distinct().filter { it in eligible }
        require(selected.isNotEmpty()) { "请先选择需要复习的题目" }
        val id = newId()
        db.withTransaction {
            dao.putBatch(ReviewBatch(id, range.start.toString(), range.end.toString(), range.zone.id,
                range.startMillis, range.endExclusive, time()))
            selected.forEach { q -> dao.putItem(ReviewItem("$id:$q", id, q)) }
        }
        return id
    }
    suspend fun deleteQuestionTree(id: String) {
        val all = dao.allQuestions(); val deleted = mutableSetOf(id)
        do { val before = deleted.size; deleted += all.filter { it.parentId in deleted }.map { it.id } } while (deleted.size != before)
        db.withTransaction {
            dao.deleteQuestions(deleted.toList())
            // Aggregates may quote deleted content; invalidate them instead of retaining stale personal data.
            dao.allBatches().forEach { dao.putBatch(it.copy(status = "pending", aggregate = "")) }
        }
    }
}

fun eventSource(event: StudyEvent): String = "[${event.id}] ${Instant.ofEpochMilli(event.at)} ${event.kind}: ${event.payload}"
