package com.mathtutor.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mathtutor.app.core.*
import com.mathtutor.app.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34], manifest = Config.NONE)
class RepositoryTest {
    private lateinit var db: StudyDatabase
    private lateinit var repository: StudyRepository
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), StudyDatabase::class.java).allowMainThreadQueries().build()
        repository = StudyRepository(db)
    }
    @After fun close() { db.close() }
    private fun millis(text: String) = Instant.parse(text).toEpochMilli()
    private suspend fun event(q: String, kind: String, at: String, text: String, student: Boolean) {
        val payload = if (kind == Kind.QUESTION) AppJson.encodeToString(QuestionContent(text)) else AppJson.encodeToString(StudentText(text))
        db.dao().insertEvent(StudyEvent(newId(), q, "s", kind, millis(at), payload, student))
    }
    @Test fun oldQuestionContinuedInRangeSurvivesLaterConversations() = runBlocking {
        db.dao().insertQuestion(QuestionEntity("old", millis("2026-10-01T00:00:00Z")))
        event("old",Kind.QUESTION,"2026-10-01T00:00:00Z","原题条件",true)
        event("old",Kind.USER,"2026-10-21T00:00:00Z","期间继续讨论",true)
        event("old",Kind.QUESTION,"2026-10-30T00:00:00Z","未来修正不能混入",true)
        event("old",Kind.USER,"2026-11-01T00:00:00Z","后来的讨论",true)
        db.dao().insertQuestion(QuestionEntity("only-system", millis("2026-10-01T00:00:00Z")))
        event("only-system",Kind.USER,"2026-10-23T00:00:00Z","自动内容不算学生交流",false)
        val range = DatedRange(LocalDate.parse("2026-10-20"), LocalDate.parse("2026-10-28"), ZoneId.of("Asia/Shanghai"))
        assertEquals(listOf("old"), db.dao().activeQuestions(range.startMillis, range.endExclusive).map { it.id })
        val snapshot = repository.view("old", range.endExclusive)
        assertEquals("原题条件", snapshot.revision.text)
        assertFalse(snapshot.history.any { it.payload.contains("未来") || it.payload.contains("后来的") })
        assertEquals(2, snapshot.history.size)
    }
    @Test fun incompleteCorrectStepsDoNotMarkTheWholeQuestionMastered() = runBlocking {
        val id = repository.create(QuestionContent("示例题"))
        repository.recordAttempt(id,"images/test.jpg",CheckResult("correct","这些步骤正确",complete=false))
        assertFalse(repository.view(id).completedAttempt)
        assertEquals(Mastery.NEEDS_WORK,repository.view(id).status)
        assertTrue(db.dao().allSchedules().isEmpty())
    }
    @Test fun guidedAndIndependentAttemptsRemainDistinct() = runBlocking {
        val id = repository.create(QuestionContent("示例题"))
        var view = repository.view(id)
        repository.append(id,view.session!!.sessionId,Kind.USER,StudentText("提示",1),true)
        repository.recordAttempt(id,"images/test.jpg",CheckResult("correct","完整正确",complete=true))
        assertEquals(Mastery.GUIDED,repository.view(id).status)
        assertEquals(3,db.dao().allSchedules().size)
        repository.startAttempt(id)
        view=repository.view(id)
        assertFalse(view.usedHints)
        assertFalse(view.completedAttempt)
        repository.recordAttempt(id,"images/test.jpg",CheckResult("correct","独立完整正确",complete=true))
        assertEquals(Mastery.INDEPENDENT,repository.view(id).status)
        assertEquals(2,repository.view(id).history.count{it.kind==Kind.ATTEMPT})
    }
    @Test fun uncertaintyDoesNotScheduleOrClaimSuccess() = runBlocking {
        val id=repository.create(QuestionContent("示例题"))
        repository.recordAttempt(id,"images/test.jpg",CheckResult("uncertain","图像不清"))
        assertEquals(Mastery.NEEDS_WORK,repository.view(id).status)
        assertTrue(db.dao().allSchedules().isEmpty())
    }
    @Test fun changingQuestionConditionsInvalidatesOldCompletionAndCard() = runBlocking {
        val id=repository.create(QuestionContent("原题"))
        repository.recordAttempt(id,"images/test.jpg",CheckResult("correct","完成",complete=true))
        val session=repository.view(id).session!!.sessionId
        repository.append(id,session,Kind.CARD,KnowledgeCard(approach="旧条件下的方法"))
        assertTrue(repository.view(id).completedAttempt)
        repository.revise(id,QuestionContent("修正后的题目"))
        val revised=repository.view(id)
        assertFalse(revised.completedAttempt);assertNull(revised.card);assertEquals(Mastery.NEEDS_WORK,revised.status)
        assertTrue(revised.history.any{it.kind==Kind.CARD})
    }
    @Test fun startingRedoDoesNotDismissAnUnfinishedReview() = runBlocking {
        val id=repository.create(QuestionContent("示例题"))
        db.dao().putSchedule(ReviewSchedule("due",id,1))
        val afterOriginalExchange=repository.view(id).history.maxOf{it.at}+1
        repository.startAttempt(id)
        assertTrue(db.dao().activeQuestions(afterOriginalExchange,Long.MAX_VALUE).isEmpty())
        assertNull(db.dao().allSchedules().first().completedAt)
        repository.recordAttempt(id,"images/test.jpg",CheckResult("correct","已完成",complete=true))
        assertNotNull(db.dao().allSchedules().find{it.id=="due"}!!.completedAt)
    }
    @Test fun deletingParentRemovesVariantsAndInvalidatesAggregates() = runBlocking {
        val id=repository.create(QuestionContent("原题"));val child=repository.create(QuestionContent("变式"),id)
        val batch=ReviewBatch("b","2026-10-20","2026-10-28","Asia/Shanghai",0,Long.MAX_VALUE,1,"done","私人总结")
        db.dao().putBatch(batch);db.dao().putItem(ReviewItem("i","b",child,"done","{}"))
        repository.deleteQuestionTree(id)
        assertTrue(db.dao().allQuestions().isEmpty());assertTrue(db.dao().allEvents().isEmpty());assertTrue(db.dao().allItems().isEmpty())
        assertEquals("",db.dao().batch("b")!!.aggregate)
    }
}
