package com.mathtutor.app

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mathtutor.app.core.*
import com.mathtutor.app.data.*
import com.mathtutor.app.media.MediaFiles
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34],manifest=Config.NONE)
class BackupTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    @Test fun roundTripRestoresHistoryAndImagesWithoutCredentialsOrDuplicates() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,StudyDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo=StudyRepository(db);val media=MediaFiles(context);val manager=BackupManager(context,repo,media)
            val image="images/${newId()}.jpg";media.file(image).writeBytes(byteArrayOf(1,2,3,4))
            val id=repo.create(QuestionContent("备份题目",listOf(image),listOf("函数")))
            val output=File(context.cacheDir,"${newId()}.zip")
            manager.export(Uri.fromFile(output))
            ZipFile(output).use{zip->assertEquals(setOf("study.json",image),zip.entries().asSequence().map{it.name}.toSet());assertFalse(zip.getInputStream(zip.getEntry("study.json")).reader().readText().contains("apiKey"))}
            db.dao().clearBatches();db.dao().clearQuestions();media.file(image).delete()
            manager.restore(Uri.fromFile(output));manager.restore(Uri.fromFile(output))
            assertEquals(1,db.dao().allQuestions().size);assertEquals("备份题目",repo.view(id).revision.text)
            assertArrayEquals(byteArrayOf(1,2,3,4),media.file(image).readBytes())
            assertEquals(3,db.dao().allEvents().size)
        } finally {db.close()}
    }
    @Test fun traversalBackupIsRejectedBeforeChangingTheDatabase() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,StudyDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo=StudyRepository(db);repo.create(QuestionContent("应保留的记录"))
            val file=File(context.cacheDir,"${newId()}.zip")
            ZipOutputStream(file.outputStream()).use{it.putNextEntry(ZipEntry("../escape"));it.write("bad".toByteArray());it.closeEntry()}
            val result=runCatching{BackupManager(context,repo,MediaFiles(context)).restore(Uri.fromFile(file))}
            assertTrue(result.isFailure);assertEquals(1,db.dao().allQuestions().size)
        }finally{db.close()}
    }
    @Test fun brokenReferencesAreRejected() {
        val archive=StudyArchive(questions=emptyList(),events=listOf(StudyEvent("x","missing","s",Kind.USER,1,"{}")),batches=emptyList(),items=emptyList(),schedules=emptyList())
        assertTrue(runCatching{BackupManager.validateArchive(archive)}.isFailure)
        assertFalse(BackupManager.validEntry("images/../../key"))
        assertFalse(BackupManager.validEntry("credentials.xml"))
    }
}
