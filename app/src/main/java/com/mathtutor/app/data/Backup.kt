package com.mathtutor.app.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.mathtutor.app.core.*
import com.mathtutor.app.media.MediaFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable data class StudyArchive(val formatVersion: Int = 1, val exportedAt: Long = now(),
    val questions: List<QuestionEntity>, val events: List<StudyEvent>, val batches: List<ReviewBatch>,
    val items: List<ReviewItem>, val schedules: List<ReviewSchedule>)

class BackupManager(private val context: Context, private val repository: StudyRepository, private val media: MediaFiles) {
    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val dao = repository.dao
        val archive = repository.db.withTransaction { StudyArchive(questions = dao.allQuestions(), events = dao.allEvents(), batches = dao.allBatches(), items = dao.allItems(), schedules = dao.allSchedules()) }
        val referenced = imageReferences(archive.events)
        context.contentResolver.openOutputStream(uri)?.use { stream -> ZipOutputStream(stream).use { zip ->
            zip.putNextEntry(ZipEntry("study.json")); zip.write(AppJson.encodeToString(archive).toByteArray()); zip.closeEntry()
            for (path in referenced) {
                val file = media.file(path); check(file.exists()) { "图片缺失，请检查本地记录后再备份" }
                zip.putNextEntry(ZipEntry(path)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        } } ?: error("无法写入备份文件")
    }
    suspend fun restore(uri: Uri) = withContext(Dispatchers.IO) {
        val stage = File(context.cacheDir, "restore-${newId()}").apply { mkdirs() }
        try {
            var total = 0L; val names = mutableSetOf<String>()
            context.contentResolver.openInputStream(uri)?.use { input -> ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    require(validEntry(name) && names.add(name) && !entry.isDirectory) { "备份包含无效或重复路径" }
                    require(names.size <= 10000) { "备份条目过多" }
                    val target = File(stage, name); target.parentFile?.mkdirs()
                    target.outputStream().use { out ->
                        val buffer = ByteArray(8192); var n = zip.read(buffer)
                        while (n != -1) { total += n; require(total <= 512L * 1024 * 1024) { "备份超过 512 MB 限制" }; out.write(buffer, 0, n); n = zip.read(buffer) }
                    }
                    zip.closeEntry(); entry = zip.nextEntry
                }
            } } ?: error("无法读取备份")
            val archive = AppJson.decodeFromString<StudyArchive>(File(stage, "study.json").readText())
            validateArchive(archive)
            val refs = imageReferences(archive.events)
            require(refs.all { File(stage, it).isFile }) { "备份缺少题目图片" }
            // Merge by stable IDs. Existing records are retained; conflicting IDs abort before mutation.
            val dao = repository.dao
            val oldEvents = dao.allEvents().associateBy { it.id }
            require(archive.events.all { oldEvents[it.id] == null || oldEvents[it.id] == it }) { "备份中的记录与本机同名记录冲突" }
            val oldQuestions = dao.allQuestions().associateBy { it.id }
            require(archive.questions.all { oldQuestions[it.id] == null || oldQuestions[it.id] == it }) { "备份题目与本机记录冲突" }
            refs.forEach { path ->
                val target = media.file(path); val source = File(stage, path)
                if (target.exists()) require(target.readBytes().contentEquals(source.readBytes())) { "备份图片与本机文件冲突" }
            }
            val added = mutableListOf<File>()
            try {
                refs.forEach { path -> val target = media.file(path); if (!target.exists()) { File(stage, path).copyTo(target); added += target } }
                repository.db.withTransaction {
                    val oldBatchIds = dao.allBatches().map { it.id }.toSet()
                    val oldScheduleIds = dao.allSchedules().map { it.id }.toSet()
                    val oldItemIds = dao.allItems().map { it.id }.toSet()
                    archive.questions.filter { it.id !in oldQuestions }.forEach { dao.insertQuestion(it) }
                    archive.events.filter { it.id !in oldEvents }.forEach { dao.insertEvent(it) }
                    archive.batches.filter { it.id !in oldBatchIds }.forEach { dao.putBatch(it.copy(status = if (it.status == "running") "pending" else it.status)) }
                    archive.items.filter { it.id !in oldItemIds }.forEach { dao.putItem(it.copy(status = if (it.status == "running") "pending" else it.status)) }
                    archive.schedules.filter { it.id !in oldScheduleIds }.forEach { dao.putSchedule(it) }
                }
            } catch (e: Exception) { added.forEach { it.delete() }; throw e }
        } finally { stage.deleteRecursively() }
    }
    companion object {
        fun validEntry(name: String) = name == "study.json" || Regex("images/[a-zA-Z0-9-]+\\.jpg").matches(name)
        fun imageReferences(events: List<StudyEvent>): Set<String> = events.flatMap { event -> when (event.kind) {
            Kind.QUESTION -> event.content<QuestionContent>().images
            Kind.ATTEMPT -> listOf(event.content<AttemptContent>().image)
            else -> emptyList()
        } }.toSet()
        fun validateArchive(a: StudyArchive) {
            require(a.formatVersion == 1) { "不支持此备份格式" }
            fun <T> unique(values: List<T>, id: (T) -> String) = values.map(id).toSet().size == values.size
            require(unique(a.questions) { it.id } && unique(a.events) { it.id } && unique(a.batches) { it.id } && unique(a.items) { it.id } && unique(a.schedules) { it.id }) { "备份包含重复记录" }
            val qs = a.questions.map { it.id }.toSet(); val bs = a.batches.map { it.id }.toSet()
            require(a.events.all { it.questionId in qs } && a.schedules.all { it.questionId in qs } && a.items.all { it.questionId in qs && it.batchId in bs }) { "备份记录关联不完整" }
            require(a.questions.all { it.parentId == null || it.parentId in qs }) { "变式题关联不完整" }
            require(imageReferences(a.events).all(::validEntry)) { "备份图片路径不合法" }
            a.events.forEach { e -> when (e.kind) {
                Kind.QUESTION -> require(e.content<QuestionContent>().text.isNotBlank())
                Kind.USER -> e.content<StudentText>()
                Kind.ASSISTANT -> e.content<TutorReply>()
                Kind.ATTEMPT -> require(e.content<AttemptContent>().result.verdict in setOf("correct", "needs_revision", "uncertain"))
                Kind.CARD -> e.content<KnowledgeCard>()
                Kind.STATUS -> require(e.content<StatusContent>().status in setOf(Mastery.NEEDS_WORK, Mastery.GUIDED, Mastery.INDEPENDENT))
                Kind.SESSION -> e.content<SessionContent>()
                Kind.ERROR -> Unit
                else -> error("未知的学习记录类型")
            } }
        }
    }
}
