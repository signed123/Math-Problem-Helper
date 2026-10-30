package com.mathtutor.app

import android.app.Application
import com.mathtutor.app.ai.*
import com.mathtutor.app.data.*
import com.mathtutor.app.media.MediaFiles
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class TutorApplication : Application() {
    lateinit var container: AppContainer; private set
    override fun onCreate() { super.onCreate(); container = AppContainer(this) }
}
class AppContainer(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val media = MediaFiles(app)
    val repository = StudyRepository(StudyDatabase.open(app))
    val settingsStore = SettingsStore(app)
    val settings = settingsStore.settings.stateIn(scope, SharingStarted.Eagerly, TutorSettings())
    val credentials = CredentialStore(app)
    val live = BailianClient(credentials::read, { settings.value }, media::file)
    val demo = DemoMathService()
    val math: MathService get() = if (settings.value.demo) demo else live
    val backup = BackupManager(app, repository, media)
    init { scope.launch(Dispatchers.IO) { media.cleanTemporary() } }
}
