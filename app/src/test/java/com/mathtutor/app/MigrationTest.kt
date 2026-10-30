package com.mathtutor.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mathtutor.app.core.newId
import com.mathtutor.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],manifest=Config.NONE)
class MigrationTest {
    @Test fun versionOneMigrationRetainsQuestionsAndReviewProgress()=runBlocking{
        val context=ApplicationProvider.getApplicationContext<Context>();val name="migration-${newId()}.db"
        fun open()=Room.databaseBuilder(context,StudyDatabase::class.java,name).addMigrations(StudyDatabase.MIGRATION_1_2).allowMainThreadQueries().build()
        val initial=open()
        initial.dao().insertQuestion(QuestionEntity("q",1))
        initial.dao().putBatch(ReviewBatch("b","2026-10-20","2026-10-28","Asia/Shanghai",1,10,1))
        initial.dao().putItem(ReviewItem("i","b","q","done","summary"))
        initial.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).absolutePath,null,SQLiteDatabase.OPEN_READWRITE).use{old->
            old.execSQL("PRAGMA foreign_keys=OFF")
            old.execSQL("ALTER TABLE review_items RENAME TO review_items_v2")
            old.execSQL("CREATE TABLE review_items (id TEXT NOT NULL PRIMARY KEY, batchId TEXT NOT NULL, questionId TEXT NOT NULL, status TEXT NOT NULL, summary TEXT NOT NULL, FOREIGN KEY(batchId) REFERENCES review_batches(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(questionId) REFERENCES questions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            old.execSQL("INSERT INTO review_items (id,batchId,questionId,status,summary) SELECT id,batchId,questionId,status,summary FROM review_items_v2")
            old.execSQL("DROP TABLE review_items_v2")
            old.execSQL("CREATE INDEX index_review_items_batchId ON review_items(batchId)")
            old.execSQL("CREATE INDEX index_review_items_questionId ON review_items(questionId)")
            old.version=1
        }
        val migrated=open()
        try{assertEquals(1,migrated.dao().allQuestions().size);val item=migrated.dao().allItems().single();assertEquals("done",item.status);assertEquals("summary",item.summary);assertEquals("",item.error)}finally{migrated.close();context.deleteDatabase(name)}
    }
}
