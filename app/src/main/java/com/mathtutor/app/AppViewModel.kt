package com.mathtutor.app

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mathtutor.app.core.*
import com.mathtutor.app.data.*
import com.mathtutor.app.media.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.time.LocalDate
import java.time.ZoneId

class AppViewModel(val app: AppContainer, private val saved: SavedStateHandle) : ViewModel() {
    val repository = app.repository
    val questions = repository.views.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val batches = repository.dao.batches().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val reviewItems = repository.dao.items().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val schedules = repository.dao.schedules().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings = app.settings
    val route = saved.getStateFlow("route", "home")
    val photo = saved.getStateFlow("photo", "")
    val captureMode = saved.getStateFlow("captureMode", "new")
    val captureQuestion = saved.getStateFlow("captureQuestion", "")
    val recognition = saved.getStateFlow("recognition", "")
    val busy = MutableStateFlow(false)
    val progress = MutableStateFlow("")
    val notice = MutableStateFlow<String?>(null)
    val rangeViews = MutableStateFlow<List<QuestionView>>(emptyList())
    val selected = MutableStateFlow<Set<String>>(emptySet())
    val rangeStart = saved.getStateFlow("rangeStart", LocalDate.now().minusDays(7).toString())
    val rangeEnd = saved.getStateFlow("rangeEnd", LocalDate.now().toString())
    val hasAudio = MutableStateFlow(false)
    val audio = AudioCapture(app.app, viewModelScope)
    val player = SpeechPlayer(app.app)
    private var job: Job? = null

    fun navigate(value: String) { if (value != route.value) { player.stop(); saved["route"] = value } }
    fun openQuestion(id: String) { navigate("question/$id") }
    fun draft(id: String) = saved.getStateFlow("draft-$id", "")
    fun setDraft(id: String, text: String) { saved["draft-$id"] = text }
    fun message(value: String) { notice.value = value }
    private fun work(label: String, block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true; progress.value = label
        job = viewModelScope.launch {
            try { block() }
            catch (e: Exception) { if (e is CancellationException) throw e; notice.value = e.message ?: "操作未完成，请重试" }
            finally { busy.value = false; progress.value = "" }
        }
    }
    fun cancelWork() { job?.cancel(); player.stop() }
    fun prepareCapture(mode: String, questionId: String = "") {
        saved["captureMode"] = mode; saved["captureQuestion"] = questionId
        saved["photo"] = ""; saved["recognition"] = ""
    }
    fun cameraUri(): Uri = app.media.cameraUri().also { saved["cameraUri"] = it.toString() }
    fun importCamera() { saved.get<String>("cameraUri")?.let { importImage(Uri.parse(it)) } }
    fun importImage(uri: Uri) = work("正在读取图片") {
        saved["photo"] = app.media.import(uri); saved["recognition"] = ""; navigate("photo")
    }
    fun recognizePhoto(region: Region) = work("正在读题，请稍候") {
        val cropped = app.media.crop(photo.value, region)
        saved["photo"] = cropped
        saved["recognition"] = AppJson.encodeToString(app.math.recognize(cropped))
        navigate("recognition")
    }
    fun confirmQuestion(candidate: DetectedQuestion, text: String, conditionConfirmed: Boolean) = work("正在保存题目") {
        require(text.isNotBlank()) { "请确认题干内容" }
        require(!candidate.needsClarification || conditionConfirmed) { "请补充缺失条件，确认完整后再开始" }
        val image = app.media.crop(photo.value, candidate.region)
        val content = QuestionContent(text.trim(), listOf(photo.value, image).distinct(), candidate.tags)
        val id = repository.create(content)
        saved["photo"] = ""; saved["recognition"] = ""; openQuestion(id)
    }
    fun correctQuestion(id: String, text: String, image: String? = null) = work("正在更新题目") {
        require(text.isNotBlank()) { "题目不能为空" }
        val view = repository.view(id)
        repository.revise(id, view.revision.copy(text = text, images = (view.revision.images + listOfNotNull(image)).distinct()))
        openQuestion(id)
    }
    fun send(id: String, text: String, level: Int = 0, action: String = "message") = work("正在思考下一步提示") {
        require(text.isNotBlank()) { "先说说你的想法吧" }
        val view = repository.view(id); val session = requireNotNull(view.session).sessionId
        val request = StudentText(text.trim(), level, action)
        val pending = saved.get<String>("pending-$id")?.let { eventId -> view.history.find { it.id == eventId } }
        val event = if (pending?.payload == AppJson.encodeToString(request)) pending
            else repository.append(id, session, Kind.USER, request, true).also { saved["pending-$id"] = it.id }
        val history = repository.dao.history(id).filter { it.sessionId == session && it.id != event.id }
        val reply = app.math.guide(view.revision, history + event, request, settings.value.grade)
        repository.append(id, session, Kind.ASSISTANT, reply.copy(hintLevel = if (reply.action == "clarify") reply.hintLevel else maxOf(1, reply.hintLevel)))
        saved["pending-$id"] = null; setDraft(id, "")
        autoplay(reply.speechText)
    }
    fun retryGuide(id: String) {
        val eventId = saved.get<String>("pending-$id")
        val event = questions.value.find { it.question.id == id }?.history?.find { it.id == eventId }
        if (event == null) message("请重新发送需要帮助的内容") else event.content<StudentText>().let { send(id, it.text, it.requestedHint, it.action) }
    }
    fun markReferenceUse(id: String, source: String) = work("正在记录参考使用情况") {
        val view = repository.view(id)
        if (!view.completedAttempt) repository.append(id, requireNotNull(view.session).sessionId, Kind.USER,
            StudentText(source, requestedHint = 3, action = "reference"), true)
    }
    fun challengeCheck(id: String) = work("正在重新检查上次作答") {
        val view = repository.view(id)
        val attempt = view.sessionEvents.lastOrNull { it.kind == Kind.ATTEMPT }?.content<AttemptContent>() ?: error("请先提交作答")
        repository.append(id, requireNotNull(view.session).sessionId, Kind.USER,
            StudentText("我认为刚才的作答判断可能有误，请重新核对原图、条件和每一步，并考虑不同的正确解法。", action = "challenge"), true)
        val result = app.math.check(view.revision, attempt.image, repository.dao.history(id).filter { it.sessionId == view.session?.sessionId })
        repository.recordAttempt(id, attempt.image, result)
        autoplay(result.speechText)
    }
    fun checkPhoto(region: Region) = work("正在检查作答过程") {
        val id = captureQuestion.value
        val image = app.media.crop(photo.value, region)
        val view = repository.view(id)
        val result = app.math.check(view.revision, image, view.sessionEvents)
        repository.recordAttempt(id, image, result)
        saved["photo"] = ""; openQuestion(id); autoplay(result.speechText)
    }
    fun supplementPhoto(region: Region) = work("正在保存补充图片") {
        val id = captureQuestion.value; val view = repository.view(id)
        val image = app.media.crop(photo.value, region)
        repository.append(id, requireNotNull(view.session).sessionId, Kind.QUESTION,
            view.revision.copy(images = view.revision.images + image), true)
        saved["photo"] = ""; openQuestion(id); message("已补充图片；你也可以点“纠正题目”完善条件")
    }
    fun summarize(id: String, reflection: String) = work("正在整理你的方法卡") {
        val view = repository.view(id)
        require(view.completedAttempt) { "先提交作答并确认完成，再整理本题的方法卡" }
        require(reflection.isNotBlank()) { "先用自己的话说说关键突破口" }
        val session = requireNotNull(view.session).sessionId
        repository.append(id, session, Kind.USER, StudentText(reflection, action = "reflection"), true)
        val comparison = view.question.parentId?.let { repository.view(it).revision }
        val card = app.math.summarize(view.revision, repository.dao.history(id), reflection, comparison)
        repository.append(id, session, Kind.CARD, card)
        message("方法卡已保存，可以继续做一道变式题")
        autoplay(card.speechText)
    }
    fun editCard(id: String, card: KnowledgeCard) = work("正在保存方法卡") {
        val view = repository.view(id)
        repository.append(id, requireNotNull(view.session).sessionId, Kind.CARD, card)
    }
    fun practice(id: String) = work("正在生成并独立核对变式题") {
        val view = repository.view(id)
        var valid: PracticeDraft? = null
        repeat(2) {
            if (valid == null) {
                val draft = app.math.practice(view.revision, view.card)
                val result = app.math.validatePractice(draft)
                if (draft.text.isNotBlank() && result.verdict == "verified") valid = draft
            }
        }
        val draft = valid ?: error("暂时无法确认变式题正确，已停止出题。请稍后重试。")
        // Internal reference solution stays in memory and is never put in the student question record.
        val child = repository.create(QuestionContent(draft.text, tags = draft.tags), id)
        openQuestion(child)
    }
    fun redo(id: String, batchId: String? = null) = work("正在开始独立重做") {
        repository.startAttempt(id, batchId); setDraft(id, ""); openQuestion(id)
    }
    fun loadRange(start: String, end: String) = work("正在查找这段时间交流过的题目") {
        val range = DatedRange(LocalDate.parse(start), LocalDate.parse(end), ZoneId.systemDefault())
        saved["rangeStart"] = start; saved["rangeEnd"] = end
        saved["rangeZone"] = range.zone.id
        val eligible = repository.dao.activeQuestions(range.startMillis, range.endExclusive)
        rangeViews.value = eligible.map { repository.view(it.id, range.endExclusive) }
        selected.value = eligible.map { it.id }.toSet()
    }
    fun toggleSelection(id: String) { selected.value = if (id in selected.value) selected.value - id else selected.value + id }
    fun generateReview() = work("正在建立复习任务") {
        val range = DatedRange(LocalDate.parse(rangeStart.value), LocalDate.parse(rangeEnd.value), ZoneId.of(saved.get<String>("rangeZone") ?: ZoneId.systemDefault().id))
        val id = repository.newBatch(range, selected.value.toList())
        navigate("batch/$id"); processReview(id)
    }
    fun retryReview(id: String) = work("正在继续复习任务") { processReview(id) }
    private suspend fun processReview(id: String) {
        val batch = repository.dao.batch(id) ?: error("复习任务不存在")
        repository.dao.putBatch(batch.copy(status = "running"))
        val items = repository.dao.batchItems(id)
        items.forEachIndexed { index, item ->
            if (item.status != "done") {
                progress.value = "正在回顾第 ${index + 1}/${items.size} 题"
                repository.dao.putItem(item.copy(status = "running", error = ""))
                try {
                    val view = repository.view(item.questionId, batch.endExclusive)
                    val summary = app.math.review(view.revision, view.history)
                    repository.dao.putItem(item.copy(status = "done", summary = AppJson.encodeToString(summary), error = ""))
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    repository.dao.putItem(item.copy(status = "failed", error = e.message ?: "本题回顾未完成"))
                }
            }
        }
        val finished = repository.dao.batchItems(id)
        val completed = finished.filter { it.status == "done" }
        if (completed.isEmpty()) {
            repository.dao.putBatch(batch.copy(status = "partial")); message("各题回顾未完成，可查看原因并重试"); return
        }
        progress.value = "正在按知识点整理复习提纲"
        try {
            val summaries = completed.associate { "题目${finished.indexOf(it)+1}" to AppJson.decodeFromString<ReviewSummary>(it.summary) }
            val aggregate = app.math.aggregate(summaries)
            val prefix = if (completed.size < finished.size) "仅包含已完成的 ${completed.size}/${finished.size} 道题，其余请重试。\n\n" else ""
            val overview = aggregate.copy(displayMarkdown = prefix + aggregate.displayMarkdown)
            repository.dao.putBatch(batch.copy(status = if (completed.size == finished.size) "done" else "partial", aggregate = AppJson.encodeToString(overview)))
            if (route.value == "batch/$id") autoplay(overview.speechText)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            repository.dao.putBatch(batch.copy(status = "partial")); message("逐题回顾已保存，提纲合并失败，可继续重试")
        }
    }
    fun saveSettings(value: TutorSettings, apiKey: String, replaceKey: Boolean) = work("正在保存设置") {
        if (replaceKey) withContext(Dispatchers.IO) { app.credentials.save(apiKey) }
        if (!value.demo) require(app.credentials.configured()) { "先填写 API Key，或保留演示模式" }
        app.settingsStore.save(value)
        if (!value.voice) player.stop()
        message("设置已保存")
    }
    fun testConnection() = work("正在检查模型连接") { message(app.math.ping()) }
    fun setVoiceEnabled(enabled: Boolean) {
        if (!enabled) player.stop()
        viewModelScope.launch { app.settingsStore.save(settings.value.copy(voice = enabled)) }
    }
    fun speak(text: String) {
        if (audio.recording.value) { message("请先结束录音，再播放讲解"); return }
        if (!settings.value.voice) { message("语音已关闭，可在我的页面开启"); return }
        if (settings.value.demo) { message("演示模式未调用云端语音，请配置服务后试听"); return }
        player.speak(text, settings.value.speechRate, viewModelScope, app.live, ::message)
    }
    private fun autoplay(text: String) { if (settings.value.voice && !settings.value.demo && !audio.recording.value) speak(text) }
    fun beginRecording() { try { player.stop(); audio.start(); hasAudio.value = true } catch (e: Exception) { message(e.message ?: "录音启动失败") } }
    fun finishRecording(id: String, transcribe: Boolean = true) = work(if (transcribe) "正在转写语音" else "正在保存录音草稿") {
        val file = audio.finish()
        if (transcribe) {
            val text = if (settings.value.demo) "【演示转写】我想先整理已知条件，但还不确定怎样选择方法。" else app.live.transcribe(file)
            setDraft(id, listOf(draft(id).value, text).filter { it.isNotBlank() }.joinToString("\n"))
            audio.clearFinished(); hasAudio.value = false
            if (settings.value.demo) message("演示转写为固定样例，没有识别录音")
        }
    }
    fun discardRecording() { audio.discard(); hasAudio.value = false }
    fun export(uri: Uri) = work("正在导出学习备份") { app.backup.export(uri); message("备份已导出，不包含 API Key 和录音") }
    fun restore(uri: Uri) = work("正在校验并恢复学习备份") { app.backup.restore(uri); message("备份已合并恢复，已有记录保留") }
    fun delete(id: String) = work("正在删除题目及关联记录") {
        val before = BackupManager.imageReferences(repository.dao.allEvents())
        repository.deleteQuestionTree(id)
        withContext(Dispatchers.IO) {
            val referenced = BackupManager.imageReferences(repository.dao.allEvents())
            (before - referenced).forEach { app.media.file(it).delete() }
        }
        navigate("home"); message("题目、关联变式题及学习记录已删除")
    }
    override fun onCleared() { audio.discard(); player.release(); super.onCleared() }
}
