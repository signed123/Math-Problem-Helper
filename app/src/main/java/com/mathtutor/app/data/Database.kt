package com.mathtutor.app.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable @Entity(tableName = "questions")
data class QuestionEntity(@PrimaryKey val id: String, val createdAt: Long, val parentId: String? = null)

@Serializable @Entity(tableName = "events", foreignKeys = [ForeignKey(entity = QuestionEntity::class,
    parentColumns = ["id"], childColumns = ["questionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("questionId"), Index(value = ["questionId", "at"]), Index(value = ["studentActivity", "at"])])
data class StudyEvent(@PrimaryKey val id: String, val questionId: String, val sessionId: String,
    val kind: String, val at: Long, val payload: String, val studentActivity: Boolean = false)

@Serializable @Entity(tableName = "review_batches")
data class ReviewBatch(@PrimaryKey val id: String, val startDate: String, val endDate: String,
    val zoneId: String, val startMillis: Long, val endExclusive: Long, val createdAt: Long,
    val status: String = "pending", val aggregate: String = "")

@Serializable @Entity(tableName = "review_items", foreignKeys = [
    ForeignKey(entity = ReviewBatch::class, parentColumns = ["id"], childColumns = ["batchId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = QuestionEntity::class, parentColumns = ["id"], childColumns = ["questionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("batchId"), Index("questionId")])
data class ReviewItem(@PrimaryKey val id: String, val batchId: String, val questionId: String,
    val status: String = "pending", val summary: String = "",
    @ColumnInfo(defaultValue = "''") val error: String = "")

@Serializable @Entity(tableName = "schedules", foreignKeys = [ForeignKey(entity = QuestionEntity::class,
    parentColumns = ["id"], childColumns = ["questionId"], onDelete = ForeignKey.CASCADE)], indices = [Index("questionId")])
data class ReviewSchedule(@PrimaryKey val id: String, val questionId: String, val dueAt: Long, val completedAt: Long? = null)

@Dao interface StudyDao {
    @Query("SELECT * FROM questions ORDER BY createdAt DESC") fun questions(): Flow<List<QuestionEntity>>
    @Query("SELECT * FROM events ORDER BY at ASC, id ASC") fun events(): Flow<List<StudyEvent>>
    @Query("SELECT * FROM review_batches ORDER BY createdAt DESC") fun batches(): Flow<List<ReviewBatch>>
    @Query("SELECT * FROM review_items ORDER BY id") fun items(): Flow<List<ReviewItem>>
    @Query("SELECT * FROM schedules ORDER BY dueAt") fun schedules(): Flow<List<ReviewSchedule>>
    @Query("SELECT * FROM questions") suspend fun allQuestions(): List<QuestionEntity>
    @Query("SELECT * FROM events ORDER BY at, id") suspend fun allEvents(): List<StudyEvent>
    @Query("SELECT * FROM review_batches") suspend fun allBatches(): List<ReviewBatch>
    @Query("SELECT * FROM review_items") suspend fun allItems(): List<ReviewItem>
    @Query("SELECT * FROM schedules") suspend fun allSchedules(): List<ReviewSchedule>
    @Query("SELECT * FROM events WHERE questionId=:id AND at<:cutoff ORDER BY at,id")
    suspend fun history(id: String, cutoff: Long = Long.MAX_VALUE): List<StudyEvent>
    @Query("SELECT DISTINCT q.* FROM questions q JOIN events e ON q.id=e.questionId WHERE e.studentActivity=1 AND e.at>=:start AND e.at<:end ORDER BY q.createdAt DESC")
    suspend fun activeQuestions(start: Long, end: Long): List<QuestionEntity>
    @Query("SELECT * FROM review_batches WHERE id=:id") suspend fun batch(id: String): ReviewBatch?
    @Query("SELECT * FROM review_items WHERE batchId=:id ORDER BY questionId") suspend fun batchItems(id: String): List<ReviewItem>
    @Insert suspend fun insertQuestion(question: QuestionEntity)
    @Insert suspend fun insertEvent(event: StudyEvent)
    @Upsert suspend fun putBatch(batch: ReviewBatch)
    @Upsert suspend fun putItem(item: ReviewItem)
    @Upsert suspend fun putSchedule(schedule: ReviewSchedule)
    @Query("UPDATE schedules SET completedAt=:at WHERE questionId=:id AND completedAt IS NULL AND dueAt<=:at")
    suspend fun completeDue(id: String, at: Long)
    @Query("DELETE FROM questions WHERE id IN (:ids)") suspend fun deleteQuestions(ids: List<String>)
    @Query("DELETE FROM review_batches") suspend fun clearBatches()
    @Query("DELETE FROM questions") suspend fun clearQuestions()
}

@Database(entities = [QuestionEntity::class, StudyEvent::class, ReviewBatch::class, ReviewItem::class, ReviewSchedule::class], version = 2, exportSchema = true)
abstract class StudyDatabase : RoomDatabase() {
    abstract fun dao(): StudyDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE review_items ADD COLUMN error TEXT NOT NULL DEFAULT ''")
            }
        }
        fun open(context: Context) = Room.databaseBuilder(context, StudyDatabase::class.java, "study.db")
            .addMigrations(MIGRATION_1_2).build()
    }
}
