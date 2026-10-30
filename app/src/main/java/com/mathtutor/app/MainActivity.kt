package com.mathtutor.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mathtutor.app.ui.TutorApp
import com.mathtutor.app.ui.TutorTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels { viewModelFactory { initializer {
        AppViewModel((application as TutorApplication).container, createSavedStateHandle())
    } } }
    private val capture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) vm.importCamera() else vm.message("拍照已取消，可以重拍或选择相册")
    }
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(vm::importImage) }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) capture.launch(vm.cameraUri()) else vm.message("相机权限未开启，仍可从相册选图")
    }
    private val microphone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.beginRecording() else vm.message("麦克风权限未开启，仍可输入文字")
    }
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(vm::export) }
    private val restore = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::restore) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TutorTheme { TutorApp(vm,
            takePicture = { if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) capture.launch(vm.cameraUri()) else cameraPermission.launch(Manifest.permission.CAMERA) },
            selectImage = { pickImage.launch("image/*") },
            record = { if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.beginRecording() else microphone.launch(Manifest.permission.RECORD_AUDIO) },
            export = { export.launch("数学学习备份-${LocalDate.now()}.zip") }, restore = { restore.launch(arrayOf("application/zip", "application/octet-stream")) }) } }
    }
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            vm.player.pause()
            if (vm.audio.recording.value) vm.finishRecording(vm.route.value.removePrefix("question/"), false)
        }
    }
}
