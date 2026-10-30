@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.mathtutor.app.ui

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathtutor.app.AppViewModel
import com.mathtutor.app.core.*
import com.mathtutor.app.data.*
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString
import java.time.LocalDate

@Composable fun TutorApp(vm: AppViewModel, takePicture: () -> Unit, selectImage: () -> Unit, record: () -> Unit,
    export: () -> Unit, restore: () -> Unit) {
    val route by vm.route.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var photoMenu by remember { mutableStateOf(false) }
    val capture: (String, String) -> Unit = { mode, id -> vm.prepareCapture(mode, id); photoMenu = true }
    LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it); if (vm.notice.value == it) vm.notice.value = null } }
    BackHandler(enabled = route !in listOf("home", "review", "settings")) { vm.navigate(if (route.startsWith("batch/")) "review" else "home") }
    Scaffold(containerColor = Paper, snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        Column {
            TopAppBar(title = { Text(when {
                route == "home" -> "数学引导助手"
                route == "review" -> "我的复习"
                route == "settings" -> "我的"
                route == "photo" -> "确认照片"
                route == "recognition" -> "确认题目"
                route.startsWith("batch/") -> "这一阶段的复习"
                else -> "一起理清思路"
            }, style = MaterialTheme.typography.titleLarge) }, navigationIcon = {
                if (route !in listOf("home", "review", "settings")) IconButton(onClick = { vm.navigate(if (route.startsWith("batch/")) "review" else "home") }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper))
            if (settings.demo) Text("演示模式 · 使用固定样例，不识别或批改真实照片", Modifier.fillMaxWidth().background(Color(0xFFFFF2D7)).padding(horizontal = 20.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(progress, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = vm::cancelWork) { Text("取消等待") }
                }
            }
        }
    }, bottomBar = {
        if (route in listOf("home", "review", "settings")) NavigationBar(containerColor = Color.White) {
            listOf(Triple("home", "学习", Icons.Default.Home), Triple("review", "复习", Icons.AutoMirrored.Filled.MenuBook), Triple("settings", "我的", Icons.Default.Person)).forEach { (path, title, icon) ->
                NavigationBarItem(selected = route == path, onClick = { vm.navigate(path) }, icon = { Icon(icon, title) }, label = { Text(title) })
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                route == "home" -> HomeScreen(vm) { capture("new", "") }
                route == "review" -> ReviewScreen(vm)
                route == "settings" -> SettingsScreen(vm, export, restore)
                route == "photo" -> CaptureScreen(vm, takePicture, selectImage)
                route == "recognition" -> RecognitionScreen(vm) { vm.navigate("photo") }
                route.startsWith("question/") -> QuestionScreen(vm, route.removePrefix("question/"), capture, record)
                route.startsWith("batch/") -> BatchScreen(vm, route.removePrefix("batch/"))
            }
        }
    }
    if (photoMenu) AlertDialog(onDismissRequest = { photoMenu = false }, title = { Text("选择题目或作答照片") },
        text = { Text("拍摄或选图后，可以先裁剪并确认，再上传。") }, confirmButton = { TextButton(onClick = { photoMenu = false; takePicture() }) { Text("打开相机") } },
        dismissButton = { TextButton(onClick = { photoMenu = false; selectImage() }) { Text("从相册选择") } })
}

@Composable private fun HomeScreen(vm: AppViewModel, onCapture: () -> Unit) {
    val questions by vm.questions.collectAsStateWithLifecycle()
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val clock by produceState(now()) { while(true) { delay(60000); value=now() } }
    val due = schedules.filter { it.completedAt == null && it.dueAt <= clock }.distinctBy { it.questionId }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("把思路，一步步想清楚", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("你来思考，我来陪你找到下一步。", color = Color(0xFF657189), modifier = Modifier.padding(top = 8.dp))
        }
        item { Card(colors = CardDefaults.cardColors(containerColor = Blue), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(36.dp))
                Text("从一道题开始", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text("拍下题目，确认读对，再说说你的思路。", color = Color(0xFFDFE7FF))
                FilledTonalButton(onClick = onCapture, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("拍题开始学习") }
            }
        } }
        if (due.isNotEmpty()) {
            item { SectionTitle("今天回顾 · ${due.size} 道题") }
            items(due.take(4), key = { it.id }) { schedule -> questions.find { it.question.id == schedule.questionId }?.let { q ->
                QuestionTile(q, onClick = { vm.redo(q.question.id) }, subtitle = "先独立重做，再看历史提示")
            } }
        }
        item { SectionTitle(if (questions.isEmpty()) "开始积累自己的题库" else "继续上次学习") }
        if (questions.isEmpty()) item { Note("每道题的交流、作答和方法卡都会保存在这里。考前可以按日期挑选题目，一起回顾。") }
        items(questions.take(8), key = { it.question.id }) { q -> QuestionTile(q, { vm.openQuestion(q.question.id) }) }
        if (questions.size > 8) item { TextButton(onClick = { vm.navigate("review") }) { Text("查看全部题目") } }
    }
}

@Composable private fun QuestionTile(view: QuestionView, onClick: () -> Unit, subtitle: String? = null) {
    Card(onClick = onClick, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().testTag("question-${view.question.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${Mastery.label(view.status)}  ·  ${dateText(view.lastActivity)}", color = Blue, style = MaterialTheme.typography.labelMedium)
            MathText(view.revision.text)
            Text(subtitle ?: view.revision.tags.joinToString(" · ").ifBlank { "查看题目与学习记录" }, style = MaterialTheme.typography.bodySmall, color = Color(0xFF657189))
        }
    }
}

@Composable private fun CaptureScreen(vm: AppViewModel, retake: () -> Unit, select: () -> Unit) {
    val path by vm.photo.collectAsStateWithLifecycle()
    val mode by vm.captureMode.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var left by rememberSaveable(path) { mutableFloatStateOf(0f) }; var top by rememberSaveable(path) { mutableFloatStateOf(0f) }
    var right by rememberSaveable(path) { mutableFloatStateOf(1f) }; var bottom by rememberSaveable(path) { mutableFloatStateOf(1f) }
    val region = Region(left, top, right, bottom)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Note(if (mode == "attempt") "请保留题号和完整推导。系统会检查过程，不只看最后答案。" else "拖动选框四角，保留题干与图形。可以裁掉姓名和学校；点击下方确认后才上传。")
        if (path.isNotBlank()) PhotoView(vm.app.media, path, region = region, onRegionChange = { left=it.left;top=it.top;right=it.right;bottom=it.bottom })
        TextButton(onClick = { left=0f;top=0f;right=1f;bottom=1f }) { Text("恢复完整图片") }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = retake, enabled = !busy, modifier = Modifier.weight(1f)) { Text("重新拍摄") }
            OutlinedButton(onClick = select, enabled = !busy, modifier = Modifier.weight(1f)) { Text("重新选图") }
        }
        Button(onClick = { when (mode) { "attempt" -> vm.checkPhoto(region); "supplement" -> vm.supplementPhoto(region); else -> vm.recognizePhoto(region) } }, enabled = !busy && path.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(when (mode) { "attempt" -> "确认并检查作答"; "supplement" -> "确认补充图片"; else -> "确认并识题" })
        }
    }
}

@Composable private fun RecognitionScreen(vm: AppViewModel, back: () -> Unit) {
    val raw by vm.recognition.collectAsStateWithLifecycle(); val path by vm.photo.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val result = remember(raw) { runCatching { AppJson.decodeFromString<Recognition>(raw) }.getOrNull() }
    if (result == null || result.questions.isEmpty()) { Column(Modifier.padding(20.dp)) { Note("识题结果暂不可用，请返回图片重新识别。"); TextButton(onClick=back) { Text("返回图片") } }; return }
    var selected by rememberSaveable(raw) { mutableIntStateOf(0) }
    val candidate = result.questions[selected]
    var text by rememberSaveable(raw, selected) { mutableStateOf(candidate.text) }
    var acknowledged by rememberSaveable(raw, selected) { mutableStateOf(false) }
    var box by remember(selected, raw) { mutableStateOf(candidate.region) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Note("先确认读对，再开始引导。图片里有 ${result.questions.size} 道候选题，一次选择一道学习。")
        if (result.note.isNotBlank()) Text(result.note, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            result.questions.forEachIndexed { index, _ -> FilterChip(selected == index, { selected = index }, label = { Text("题目 ${index+1}") }) }
        }
        PhotoView(vm.app.media, path, region = box, onRegionChange = { box = it })
        OutlinedTextField(text, { text=it }, label = { Text("识别题干 · 可修改") }, minLines = 4, modifier = Modifier.fillMaxWidth())
        Text("公式预览", style = MaterialTheme.typography.labelMedium); MathText(text)
        if (candidate.needsClarification) {
            Note(candidate.clarification.ifBlank { "存在不清楚或缺失的条件，请修改题干或重新补拍。" }, true)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(acknowledged, { acknowledged=it }); Text("我已补全并核对这些条件") }
        }
        Button(onClick = { vm.confirmQuestion(candidate.copy(region=box), text, acknowledged) }, enabled = !busy && text.isNotBlank() && (!candidate.needsClarification || acknowledged), modifier = Modifier.fillMaxWidth()) { Text("题目无误，开始学习") }
        TextButton(onClick = back) { Text("返回图片，重新裁剪或补拍") }
    }
}

@Composable private fun QuestionScreen(vm: AppViewModel, id: String, capture: (String,String) -> Unit, record: () -> Unit) {
    val questions by vm.questions.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    val recording by vm.audio.recording.collectAsStateWithLifecycle(); val hasAudio by vm.hasAudio.collectAsStateWithLifecycle()
    val playing by vm.player.playing.collectAsStateWithLifecycle(); val voiceLoading by vm.player.loading.collectAsStateWithLifecycle()
    val voiceActive by vm.player.active.collectAsStateWithLifecycle(); val settings by vm.settings.collectAsStateWithLifecycle()
    val draft by vm.draft(id).collectAsStateWithLifecycle()
    val view = questions.find { it.question.id == id }
    if (view == null) { Box(Modifier.padding(20.dp)) { Text("正在读取题目…") }; return }
    var showHistory by rememberSaveable(id, view.session?.sessionId) { mutableStateOf(false) }
    var showImages by rememberSaveable(id) { mutableStateOf(false) }
    var showCard by rememberSaveable(id, view.session?.sessionId) { mutableStateOf(false) }
    var editQuestion by remember { mutableStateOf(false) }; var editedQuestion by remember(view.revision.text) { mutableStateOf(view.revision.text) }
    var reflection by rememberSaveable(id) { mutableStateOf("") }; var reflectionDialog by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }; var cardEdit by remember { mutableStateOf(false) }
    var cardApproach by remember(view.card) { mutableStateOf(view.card?.approach.orEmpty()) }
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(recording) { seconds=0; while(recording) { delay(1000); seconds++ } }
    val session = view.session?.sessionId
    val events = view.history.filter { it.kind in setOf(Kind.USER,Kind.ASSISTANT,Kind.ATTEMPT) && (showHistory || it.sessionId == session) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Card(shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(Mastery.label(view.status), color=Blue, style=MaterialTheme.typography.labelMedium, modifier=Modifier.weight(1f))
                    IconButton(onClick={vm.setVoiceEnabled(!settings.voice)}) { Icon(if(settings.voice) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,if(settings.voice) "关闭语音" else "开启语音") }
                }
                MathText(view.revision.text)
                view.question.parentId?.let { parent -> TextButton(onClick={vm.openQuestion(parent)}) { Text("回到这道变式的原题") } }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick={showImages=!showImages}) { Text(if(showImages) "收起图片" else "查看原图") }
                    TextButton(onClick={editQuestion=true}, enabled=!busy) { Text("纠正题目") }
                    TextButton(onClick={capture("supplement",id)}, enabled=!busy) { Text("补充条件图") }
                }
                if(showImages) view.revision.images.distinct().forEach { PhotoView(vm.app.media,it) }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={capture("attempt",id)}, enabled=!busy && !recording, modifier=Modifier.weight(1f)) { Text("拍照检查步骤") }
                    OutlinedButton(onClick={vm.redo(id)}, enabled=!busy && !recording, modifier=Modifier.weight(1f)) { Text("独立重做") }
                }
            } } }
            item { Text(if(view.usedHints) "正在一起理清思路" else "先试着自己想一想", color=Color(0xFF657189), style=MaterialTheme.typography.labelMedium) }
            items(events, key={it.id}) { event -> EventBubble(event, vm) }
            item { Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick={ showHistory=!showHistory; if(showHistory) vm.markReferenceUse(id,"查看历史学习记录") }) { Text(if(showHistory) "收起历史记录" else "查看历史学习记录") }
                TextButton(onClick={vm.retryGuide(id)},enabled=!busy) { Text("重试上次引导") }
                TextButton(onClick={vm.challengeCheck(id)},enabled=!busy && view.sessionEvents.any{it.kind==Kind.ATTEMPT}) { Text("这一步判断有误") }
            } }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick={vm.send(id,"给我一点提示",1,"hint")},enabled=!busy) { Text("给一点提示") }
                    OutlinedButton(onClick={vm.send(id,"请再具体一点，只推进一个局部步骤",minOf(3,(view.sessionEvents.filter{it.kind==Kind.ASSISTANT}.maxOfOrNull{it.content<TutorReply>().hintLevel}?:0)+1),"hint")},enabled=!busy) { Text("再具体一点") }
                    OutlinedButton(onClick={vm.send(id,"这一步没听懂，请换一种说法",0,"rephrase")},enabled=!busy) { Text("这一步没听懂") }
                    OutlinedButton(onClick={vm.send(id,"我没学过这个知识点，请先补基础，再回到原题",0,"foundation")},enabled=!busy) { Text("补补基础") }
                }
            }
            item { Card { Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("做完以后，把方法留下来",style=MaterialTheme.typography.titleMedium)
                if(!view.completedAttempt) Text("作答确认完成后，先用自己的话说出突破口，再整理方法卡。",style=MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick={reflectionDialog=true},enabled=!busy && view.completedAttempt) { Text("复述并生成方法卡") }
                    TextButton(onClick={showCard=!showCard;if(showCard)vm.markReferenceUse(id,"查看方法卡")},enabled=view.card!=null) { Text(if(showCard) "收起方法卡" else "查看方法卡") }
                    TextButton(onClick={vm.practice(id)},enabled=!busy && view.completedAttempt) { Text("做一道相似题") }
                }
                if(showCard && view.card!=null) {
                    KnowledgeCardView(requireNotNull(view.card))
                    TextButton(onClick={vm.speak(view.card!!.speechText)},enabled=view.card!!.speechText.isNotBlank()){Text("朗读方法卡")}
                    TextButton(onClick={cardEdit=true},enabled=!busy) { Text("修改方法总结") }
                }
            } } }
            item { TextButton(onClick={delete=true},enabled=!busy) { Text("删除本题及关联记录",color=MaterialTheme.colorScheme.error) } }
        }
        Surface(shadowElevation=6.dp) { Column(Modifier.padding(horizontal=16.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            if(voiceActive || voiceLoading) Row(verticalAlignment=Alignment.CenterVertically) {
                Text(if(voiceLoading) "正在准备语音…" else if(playing) "正在讲解" else "语音已暂停",modifier=Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
                TextButton(onClick={vm.player.pauseOrResume()}) { Text("暂停／继续") }
                TextButton(onClick={vm.player.stop()}) { Text("停止") }
            }
            OutlinedTextField(draft,{vm.setDraft(id,it)},modifier=Modifier.fillMaxWidth(),placeholder={Text("说说你的思路，或者卡在哪里…")},minLines=1,maxLines=5,enabled=!recording)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={if(recording)vm.finishRecording(id) else record()},enabled=!busy) { Icon(if(recording)Icons.Default.Stop else Icons.Default.Mic,null,Modifier.size(18.dp));Text(if(recording) "结束 $seconds 秒" else "录音") }
                if(hasAudio && !recording) {
                    TextButton(onClick={vm.finishRecording(id)},enabled=!busy) { Text("重试转写") }
                    TextButton(onClick=vm::discardRecording,enabled=!busy) { Text("丢弃") }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick={vm.send(id,draft)},enabled=!busy && !recording && draft.isNotBlank()) { Text("发送") }
            }
            if(recording) Text("最长 2 分钟；转写后可以修改，再确认发送。",style=MaterialTheme.typography.labelSmall)
        } }
    }
    if(editQuestion) AlertDialog(onDismissRequest={editQuestion=false},title={Text("纠正题目条件")},text={OutlinedTextField(editedQuestion,{editedQuestion=it},minLines=5)},confirmButton={TextButton(onClick={editQuestion=false;vm.correctQuestion(id,editedQuestion)}){Text("保存并重新开始")}},dismissButton={TextButton(onClick={editQuestion=false}){Text("取消")}})
    if(reflectionDialog) AlertDialog(onDismissRequest={reflectionDialog=false},title={Text("关键突破口是什么？")},text={Column { Text("用自己的话说说：下次看到什么特征，会想到这个方法？");OutlinedTextField(reflection,{reflection=it},minLines=4) }},confirmButton={TextButton(onClick={reflectionDialog=false;vm.summarize(id,reflection)},enabled=reflection.isNotBlank()){Text("整理方法卡")}},dismissButton={TextButton(onClick={reflectionDialog=false}){Text("再想一想")}})
    if(cardEdit) AlertDialog(onDismissRequest={cardEdit=false},title={Text("修改方法总结")},text={OutlinedTextField(cardApproach,{cardApproach=it},minLines=5)},confirmButton={TextButton(onClick={cardEdit=false;view.card?.let{vm.editCard(id,it.copy(approach=cardApproach,speechText=""))}}){Text("保存新版本")}},dismissButton={TextButton(onClick={cardEdit=false}){Text("取消")}})
    if(delete) AlertDialog(onDismissRequest={delete=false},title={Text("删除这道题？")},text={Text("本题、关联变式题、照片、对话和复习记录将被删除。可先到我的页面导出备份。")},confirmButton={TextButton(onClick={delete=false;vm.delete(id)}){Text("删除")}},dismissButton={TextButton(onClick={delete=false}){Text("保留")}})
}

@Composable private fun EventBubble(event: StudyEvent, vm: AppViewModel) {
    val user = event.kind == Kind.USER
    Card(colors=CardDefaults.cardColors(containerColor=if(user)Color(0xFFE8EEFF) else Color.White),shape=RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("${if(user)"我的思路" else if(event.kind==Kind.ATTEMPT)"作答检查" else "学习引导"} · ${dateText(event.at)}",style=MaterialTheme.typography.labelSmall,color=Color(0xFF657189))
            when(event.kind) {
                Kind.USER -> MathText(event.content<StudentText>().text)
                Kind.ASSISTANT -> { val reply=event.content<TutorReply>();MathText(reply.displayMarkdown);TextButton(onClick={vm.speak(reply.speechText)}){Icon(Icons.AutoMirrored.Filled.VolumeUp,null,Modifier.size(16.dp));Text("朗读") } }
                Kind.ATTEMPT -> { val attempt=event.content<AttemptContent>();Text(when(attempt.result.verdict){"correct"->if(attempt.result.complete)"整道题的作答检查正确" else "已提交的步骤正确，可以继续";"needs_revision"->"有一步需要重新检查";else->"暂时无法确认，请补充信息"},fontWeight=FontWeight.SemiBold)
                    MathText(attempt.result.feedback);if(attempt.result.firstIssue.isNotBlank())MathText(attempt.result.firstIssue)
                    TextButton(onClick={vm.speak(attempt.result.speechText)},enabled=attempt.result.speechText.isNotBlank()){Text("朗读检查反馈")}
                    var expanded by rememberSaveable(event.id){mutableStateOf(false)}
                    TextButton(onClick={expanded=!expanded}){Text(if(expanded)"收起作答" else "查看本次作答与识别文字")}
                    if(expanded){PhotoView(vm.app.media,attempt.image);if(attempt.result.recognizedWork.isNotBlank())MathText(attempt.result.recognizedWork)}
                }
            }
        }
    }
}
@Composable private fun KnowledgeCardView(card: KnowledgeCard) {
    Text(card.concepts.joinToString(" · "),color=Blue,style=MaterialTheme.typography.labelLarge)
    listOf("识别特征" to card.features,"入手方法与适用条件" to card.approach,"这次的卡点" to card.stickingPoints,"我的复述" to card.studentReflection).forEach { (label,value) -> if(value.isNotBlank()){SectionTitle(label);MathText(value)} }
    if(card.nextQuestions.isNotEmpty()){SectionTitle("下次先问自己");MathText(card.nextQuestions.joinToString("\n"){"- $it"})}
    if(card.transfer.isNotBlank()){SectionTitle("和原题的共同思路");MathText(card.transfer)}
}

@Composable private fun ReviewScreen(vm: AppViewModel) {
    val all by vm.questions.collectAsStateWithLifecycle();val range by vm.rangeViews.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle();val batches by vm.batches.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle();val savedStart by vm.rangeStart.collectAsStateWithLifecycle();val savedEnd by vm.rangeEnd.collectAsStateWithLifecycle()
    var start by rememberSaveable(savedStart){mutableStateOf(savedStart)};var end by rememberSaveable(savedEnd){mutableStateOf(savedEnd)}
    var mode by rememberSaveable{mutableIntStateOf(0)};var search by rememberSaveable{mutableStateOf("")};var status by rememberSaveable{mutableStateOf("")}
    val context=LocalContext.current
    fun datePicker(value:String,set:(String)->Unit){val d=LocalDate.parse(value);DatePickerDialog(context,{_,y,m,day->set(LocalDate.of(y,m+1,day).toString())},d.year,d.monthValue-1,d.dayOfMonth).show()}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){FilterChip(mode==0,{mode=0},label={Text("按时间复习")});FilterChip(mode==1,{mode=1},label={Text("全部题目与方法卡")})} }
        if(mode==0){
            item{Note("选择期间内交流过的题目，包括旧题续聊。回顾只使用截至结束日期的记录。")}
            item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={datePicker(start){start=it}},modifier=Modifier.weight(1f)){Text(start)};Text("至",Modifier.align(Alignment.CenterVertically));OutlinedButton(onClick={datePicker(end){end=it}},modifier=Modifier.weight(1f)){Text(end)}}}
            item{Button(onClick={vm.loadRange(start,end)},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("查找这段时间的题目")}}
            if(start!=savedStart || end!=savedEnd)item{Text("日期已修改，请重新查找后生成复习。",style=MaterialTheme.typography.bodySmall)}
            item{Row(verticalAlignment=Alignment.CenterVertically){Text("已选 ${selected.size} / ${range.size} 道题",Modifier.weight(1f));TextButton(onClick={vm.selected.value=range.map{it.question.id}.toSet()}){Text("全选")};TextButton(onClick={vm.selected.value=emptySet()}){Text("清空")}}}
            items(range,key={it.question.id}){q->Row(verticalAlignment=Alignment.Top){Checkbox(q.question.id in selected,{vm.toggleSelection(q.question.id)},enabled=!busy);Box(Modifier.weight(1f)){QuestionTile(q,{vm.toggleSelection(q.question.id)},"纳入本次复习")}}}
            item{Button(onClick=vm::generateReview,enabled=!busy && selected.isNotEmpty() && start==savedStart && end==savedEnd,modifier=Modifier.fillMaxWidth()){Text("生成这段时间的复习")}}
        }else{
            item{OutlinedTextField(search,{search=it},label={Text("搜索题干、知识点或方法卡")},modifier=Modifier.fillMaxWidth())}
            item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                listOf("" to "全部",Mastery.NEEDS_WORK to "仍需巩固",Mastery.GUIDED to "提示后完成",Mastery.INDEPENDENT to "已独立验证").forEach{(key,label)->FilterChip(status==key,{status=key},label={Text(label)})}
            }}
            val filtered=all.filter{(status.isBlank()||it.status==status) && (search.isBlank()||listOf(it.revision.text,it.revision.tags.joinToString(),it.card?.approach.orEmpty(),it.card?.stickingPoints.orEmpty()).any{value->value.contains(search,true)})}
            if(filtered.isEmpty())item{Note("还没有符合条件的题目。可以先拍一道题开始学习。")}
            items(filtered,key={it.question.id}){q->QuestionTile(q,{vm.openQuestion(q.question.id)})}
        }
        if(batches.isNotEmpty())item{SectionTitle("已保存的复习")}
        items(batches,key={it.id}){batch->Card(onClick={vm.navigate("batch/${batch.id}")}){Column(Modifier.fillMaxWidth().padding(16.dp)){Text("${batch.startDate} — ${batch.endDate}",fontWeight=FontWeight.SemiBold);Text(when(batch.status){"done"->"已生成";"running"->"处理中，可查看进度";"partial"->"部分完成，可继续重试";else->"待继续"},color=Blue,modifier=Modifier.padding(top=6.dp))}}}
    }
}

@Composable private fun BatchScreen(vm: AppViewModel,id:String) {
    val batches by vm.batches.collectAsStateWithLifecycle();val allItems by vm.reviewItems.collectAsStateWithLifecycle();val questions by vm.questions.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle()
    val batch=batches.find{it.id==id}?:return
    val items=allItems.filter{it.batchId==id}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{Text("${batch.startDate} — ${batch.endDate}",style=MaterialTheme.typography.titleLarge);Text("按 ${batch.zoneId} 日期回顾 · 已完成 ${items.count{it.status=="done"}}/${items.size} 题",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=6.dp))}
        if(batch.status!="done")item{Button(onClick={vm.retryReview(id)},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("继续生成／重试失败项")}}
        if(batch.aggregate.isNotBlank())item{
            val overview=runCatching{AppJson.decodeFromString<ReviewOverview>(batch.aggregate)}.getOrElse{ReviewOverview(batch.aggregate,"")}
            Card{Column(Modifier.padding(18.dp)){SectionTitle("这一阶段怎么复习");MathText(overview.displayMarkdown)
                TextButton(onClick={vm.speak(overview.speechText)},enabled=overview.speechText.isNotBlank()){Text("朗读复习提纲")}
            }}
        }
        itemsIndexed(items,key={_,item->item.id}){index,item->
            val current=questions.find{it.question.id==item.questionId}
            val historical=current?.let{QuestionView(it.question,it.history.filter{e->e.at<batch.endExclusive})}
            Card{Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("题目${index+1}",style=MaterialTheme.typography.labelLarge,color=Blue)
                historical?.let{MathText(it.revision.text)}
                if(item.status=="done"){
                    val summary=runCatching{AppJson.decodeFromString<ReviewSummary>(item.summary)}.getOrNull()
                    summary?.let{s->
                        Text(s.concepts.joinToString(" · "),color=Blue)
                        TextButton(onClick={vm.speak(s.speechText)},enabled=s.speechText.isNotBlank()){Text("朗读本题回顾")}
                        listOf("当时的思路" to s.originalThinking,"主要卡点" to s.stickingPoints,"突破方法" to s.approach,"下一步复习" to s.nextSteps).forEach{(label,value)->if(value.isNotBlank()){SectionTitle(label);MathText(value)}}
                        var evidence by rememberSaveable(item.id){mutableStateOf(false)}
                        TextButton(onClick={evidence=!evidence}){Text(if(evidence)"收起记录依据" else "查看总结依据（${s.evidenceIds.size} 条）")}
                        if(evidence)historical?.history?.filter{it.id in s.evidenceIds}?.forEach{EventEvidence(it)}
                    }
                }else Note(if(item.error.isNotBlank())item.error else "本题回顾尚未完成",item.status=="failed")
                Row {TextButton(onClick={vm.redo(item.questionId,id)},enabled=!busy){Text("独立重做")};TextButton(onClick={vm.openQuestion(item.questionId)}){Text("打开当前题目")}}
            }}
        }
    }
}
@Composable private fun EventEvidence(event:StudyEvent){
    val text=when(event.kind){Kind.USER->event.content<StudentText>().text;Kind.ASSISTANT->event.content<TutorReply>().displayMarkdown;Kind.ATTEMPT->event.content<AttemptContent>().result.let{it.recognizedWork+"\n"+it.feedback};Kind.QUESTION->event.content<QuestionContent>().text;Kind.CARD->event.content<KnowledgeCard>().approach;Kind.STATUS->Mastery.label(event.content<StatusContent>().status);else->"本次学习开始"}
    Surface(color=Paper,shape=MaterialTheme.shapes.small){Column(Modifier.fillMaxWidth().padding(10.dp)){Text(dateText(event.at),style=MaterialTheme.typography.labelSmall);MathText(text)}}
}

@Composable private fun SettingsScreen(vm:AppViewModel,export:()->Unit,restore:()->Unit){
    val settings by vm.settings.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle()
    var grade by rememberSaveable(settings.grade){mutableStateOf(settings.grade)}
    var demo by rememberSaveable(settings.demo){mutableStateOf(settings.demo)}
    var voice by rememberSaveable(settings.voice){mutableStateOf(settings.voice)}
    var speed by rememberSaveable(settings.speechRate){mutableFloatStateOf(settings.speechRate)}
    // API key deliberately is not rememberSaveable: no credential goes into saved-instance state.
    var apiKey by remember{mutableStateOf("")};var replaceKey by remember{mutableStateOf(false)}
    var advanced by rememberSaveable{mutableStateOf(false)}
    var model by rememberSaveable(settings.model){mutableStateOf(settings.model)}
    var asr by rememberSaveable(settings.asrModel){mutableStateOf(settings.asrModel)}
    var tts by rememberSaveable(settings.ttsModel){mutableStateOf(settings.ttsModel)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{SectionTitle("学习档案");OutlinedTextField(grade,{grade=it},label={Text("年级")},modifier=Modifier.fillMaxWidth());Text("不要求教材版本。遇到新方法时，会先确认你是否学过。",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=8.dp))}
        item{SectionTitle("语音讲解");Row(verticalAlignment=Alignment.CenterVertically){Text("同时播放语音",Modifier.weight(1f));Switch(voice,{voice=it;if(!it)vm.player.stop()})};Text("语速：${"%.1f".format(speed)} 倍");Slider(speed,{speed=it},valueRange=.6f..1.6f,steps=9)}
        item{SectionTitle("服务连接");Row(verticalAlignment=Alignment.CenterVertically){Text("演示模式",Modifier.weight(1f));Switch(demo,{demo=it})};Note("演示模式使用固定样例，不会识别或判断真实照片。关闭演示模式后，确认提交的照片、文字和录音会发送到百炼处理。")}
        item{OutlinedTextField(apiKey,{apiKey=it;replaceKey=true},label={Text(if(vm.app.credentials.configured())"API Key 已保存，输入可替换" else "北京地域百炼 API Key")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth());Text("仅在本机加密保存，不包含在备份中。",style=MaterialTheme.typography.bodySmall)}
        item{TextButton(onClick={advanced=!advanced}){Text(if(advanced)"收起模型设置" else "模型设置")}}
        if(advanced)item{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){OutlinedTextField(model,{model=it},label={Text("图片与辅导模型")},modifier=Modifier.fillMaxWidth());OutlinedTextField(asr,{asr=it},label={Text("语音识别模型")},modifier=Modifier.fillMaxWidth());OutlinedTextField(tts,{tts=it},label={Text("语音合成模型")},modifier=Modifier.fillMaxWidth())}}
        item{Button(onClick={vm.saveSettings(TutorSettings(grade,demo,voice,speed,model,asr,tts),apiKey,replaceKey);apiKey="";replaceKey=false},enabled=!busy&&grade.isNotBlank()&&model.isNotBlank()&&asr.isNotBlank()&&tts.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("保存设置")}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){OutlinedButton(onClick=vm::testConnection,enabled=!busy,modifier=Modifier.weight(1f)){Text("测试已保存的连接")};OutlinedButton(onClick={vm.speak("我们先想一想，这道题给了哪些条件？")},enabled=!busy,modifier=Modifier.weight(1f)){Text("试听讲解")}}}
        item{SectionTitle("学习记录与备份");Note("记录保存在这台手机。更换手机或卸载前，请先导出备份。恢复会合并记录；同名冲突时停止，不覆盖本机内容。")}
        item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick=export,enabled=!busy,modifier=Modifier.weight(1f)){Text("导出学习备份")};OutlinedButton(onClick=restore,enabled=!busy,modifier=Modifier.weight(1f)){Text("恢复备份")}}}
        item{Text("备份包含题目、照片、交流、方法卡和复习进度，不包含密钥及录音。删除单题请在该题页面操作。",style=MaterialTheme.typography.bodySmall)}
        item{SectionTitle("关于");Text("数学引导助手 0.1.0\n个人学习试用版 · 通过问题和提示帮助自己思考。\n图片模糊或判断不确定时，请补充说明，也可以指出系统的判断有误。",style=MaterialTheme.typography.bodyMedium)}
    }
}
