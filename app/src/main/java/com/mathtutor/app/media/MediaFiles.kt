package com.mathtutor.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.mathtutor.app.core.Region
import com.mathtutor.app.core.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaFiles(val context: Context) {
    val imageDir = File(context.filesDir, "images").apply { mkdirs() }
    private val captureDir = File(context.cacheDir, "capture").apply { mkdirs() }
    fun file(path: String): File {
        require(Regex("images/[a-zA-Z0-9-]+\\.jpg").matches(path)) { "图片路径无效" }
        return File(context.filesDir, path)
    }
    fun cameraUri(): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(captureDir, "${newId()}.jpg"))
    fun preview(path: String): Bitmap? {
        val target=file(path)
        val options=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeFile(target.absolutePath,options)
        if(options.outWidth<=0 || options.outHeight<=0) return null
        var sample=1
        while(maxOf(options.outWidth,options.outHeight)/sample>1600) sample*=2
        return BitmapFactory.decodeFile(target.absolutePath,BitmapFactory.Options().apply { inSampleSize=sample })
    }
    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取这张图片" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 3000) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) } ?: error("图片解码失败")
        val orientation = context.contentResolver.openInputStream(uri).use { stream ->
            stream?.let { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }
        val matrix = Matrix().apply { when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
        } }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        val path = save(rotated)
        if (rotated !== bitmap) rotated.recycle()
        bitmap.recycle()
        path
    }
    suspend fun crop(path: String, region: Region): String = withContext(Dispatchers.IO) {
        require(region.valid()) { "请保留一个有效的题目区域" }
        val source = BitmapFactory.decodeFile(file(path).absolutePath) ?: error("原图不存在，请重新拍照")
        val left = (region.left * source.width).toInt().coerceIn(0, source.width - 1)
        val top = (region.top * source.height).toInt().coerceIn(0, source.height - 1)
        val width = ((region.right - region.left) * source.width).toInt().coerceIn(1, source.width - left)
        val height = ((region.bottom - region.top) * source.height).toInt().coerceIn(1, source.height - top)
        val cropped = Bitmap.createBitmap(source, left, top, width, height)
        val result = save(cropped)
        if (cropped !== source) cropped.recycle()
        source.recycle()
        result
    }
    private fun save(bitmap: Bitmap): String {
        val relative = "images/${newId()}.jpg"
        file(relative).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
        return relative
    }
    fun cleanTemporary() {
        captureDir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000 }?.forEach { it.delete() }
        File(context.cacheDir, "speech").listFiles()?.forEach { it.delete() }
    }
}
