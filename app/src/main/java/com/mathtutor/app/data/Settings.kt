package com.mathtutor.app.data

import android.content.Context
import android.annotation.SuppressLint
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.preferences by preferencesDataStore("preferences")
data class TutorSettings(val grade: String = "高二", val demo: Boolean = true, val voice: Boolean = true,
    val speechRate: Float = 1f, val model: String = "qwen3-vl-plus", val asrModel: String = "qwen3-asr-flash", val ttsModel: String = "qwen3-tts-flash")
class SettingsStore(private val context: Context) {
    private val grade = stringPreferencesKey("grade")
    private val demo = booleanPreferencesKey("demo")
    private val voice = booleanPreferencesKey("voice")
    private val speed = floatPreferencesKey("speed")
    private val model = stringPreferencesKey("model")
    private val asr = stringPreferencesKey("asr")
    private val tts = stringPreferencesKey("tts")
    val settings = context.preferences.data.map { TutorSettings(it[grade] ?: "高二", it[demo] ?: true,
        it[voice] ?: true, it[speed] ?: 1f, it[model] ?: "qwen3-vl-plus", it[asr] ?: "qwen3-asr-flash", it[tts] ?: "qwen3-tts-flash") }
    suspend fun save(value: TutorSettings) { context.preferences.edit {
        it[grade] = value.grade; it[demo] = value.demo; it[voice] = value.voice
        it[speed] = value.speechRate.coerceIn(.6f, 1.6f); it[model] = value.model.trim()
        it[asr] = value.asrModel.trim(); it[tts] = value.ttsModel.trim()
    } }
}

class CredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("credentials", Context.MODE_PRIVATE)
    private val alias = "math-tutor-api-key-v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    @SuppressLint("ApplySharedPref") // Called on Dispatchers.IO; report success only after durable storage.
    fun save(value: String) {
        if (value.isBlank()) { check(prefs.edit().clear().commit()); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("cipher", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).commit())
    }
    fun read(): String {
        val encrypted = prefs.getString("cipher", null) ?: return ""
        return try {
            val iv = Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
            String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) { throw IllegalStateException("密钥无法解密，请在我的页面重新填写 API Key") }
    }
    fun configured(): Boolean = prefs.contains("cipher")
}
