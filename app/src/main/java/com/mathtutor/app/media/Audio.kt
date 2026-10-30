package com.mathtutor.app.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.mathtutor.app.ai.SpeechService
import com.mathtutor.app.core.newId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.RandomAccessFile

class AudioCapture(private val context: Context, private val scope: CoroutineScope) {
    private val mutableRecording = MutableStateFlow(false)
    val recording = mutableRecording.asStateFlow()
    private var recorder: AudioRecord? = null
    private var job: Job? = null
    var pending: File? = null; private set
    private var captureError: Throwable? = null
    @Suppress("MissingPermission")
    fun start() {
        require(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "请先允许麦克风权限" }
        if (mutableRecording.value) return
        pending?.delete(); captureError = null
        val dir = File(context.cacheDir, "speech").apply { mkdirs() }
        val file = File(dir, "${newId()}.wav"); pending = file
        val size = maxOf(4096, AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT))
        val audio = AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
        require(audio.state == AudioRecord.STATE_INITIALIZED) { "麦克风启动失败，请检查是否被其他应用占用" }
        recorder = audio
        audio.startRecording(); mutableRecording.value = true
        job = scope.launch(Dispatchers.IO) {
            try {
                RandomAccessFile(file, "rw").use { output ->
                    output.setLength(0); output.write(ByteArray(44))
                    val buffer = ByteArray(size); var total = 0
                    while (mutableRecording.value && isActive && total < 16000 * 2 * 120) {
                        val count = audio.read(buffer, 0, buffer.size)
                        if (count > 0) { output.write(buffer, 0, count); total += count }
                        else if (mutableRecording.value) error("录音中断，请重试")
                    }
                    output.seek(0); output.write(wavHeader(total))
                }
            } catch (e: Exception) { if (e !is CancellationException) captureError = e }
            finally { mutableRecording.value = false; runCatching { audio.stop() }; audio.release(); recorder = null }
        }
    }
    suspend fun finish(): File {
        mutableRecording.value = false
        runCatching { recorder?.stop() }
        job?.join()
        captureError?.let { throw IllegalStateException("录音未完成，请重录", it) }
        return pending?.takeIf { it.length() > 44 } ?: error("没有录到声音，请重新录音")
    }
    fun discard() { mutableRecording.value = false; runCatching { recorder?.stop() }; job?.cancel(); pending?.delete(); pending = null }
    fun clearFinished() { pending?.delete(); pending = null }
    companion object {
        fun wavHeader(length: Int): ByteArray = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(length + 36).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1).putInt(16000).putInt(32000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(length).array()
    }
}

class SpeechPlayer(context: Context) {
    private val dir = File(context.cacheDir, "speech").apply { mkdirs() }
    private val player = ExoPlayer.Builder(context).build()
    val playing = MutableStateFlow(false)
    val loading = MutableStateFlow(false)
    val active = MutableStateFlow(false)
    private var job: Job? = null
    init { player.addListener(object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { playing.value = isPlaying }
        override fun onPlaybackStateChanged(playbackState: Int) { if (playbackState == Player.STATE_ENDED) active.value = false }
    }) }
    fun speak(text: String, speed: Float, scope: CoroutineScope, service: SpeechService, onError: (String) -> Unit) {
        stop()
        if (text.isBlank()) return
        job = scope.launch {
            loading.value = true
            try {
                val files = mutableListOf<File>()
                for (part in text.chunked(200)) {
                    val audio = service.synthesize(part)
                    val file = withContext(Dispatchers.IO) { File(dir, "${newId()}.wav").also { it.writeBytes(audio) } }
                    files += file
                }
                player.setMediaItems(files.map { MediaItem.fromUri(Uri.fromFile(it)) })
                active.value = true
                player.setPlaybackSpeed(speed); player.prepare(); player.play()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onError("文字已保留，语音暂不可用：${e.message}")
            } finally { loading.value = false }
        }
    }
    fun pauseOrResume() { if (player.isPlaying) player.pause() else player.play() }
    fun pause() { player.pause() }
    fun stop() { job?.cancel(); loading.value = false; active.value = false; player.stop(); player.clearMediaItems(); playing.value = false }
    fun release() { stop(); player.release() }
}
