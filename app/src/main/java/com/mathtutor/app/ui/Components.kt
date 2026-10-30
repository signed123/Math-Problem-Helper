package com.mathtutor.app.ui

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.mathtutor.app.core.Region
import com.mathtutor.app.media.MediaFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val Ink = Color(0xFF17233D)
val Blue = Color(0xFF3155C5)
val Paper = Color(0xFFF5F7FC)

@Composable fun TutorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Blue, background = Paper, surface = Color.White,
        onSurface = Ink, secondary = Color(0xFF37786A), surfaceContainer = Color(0xFFEDF0F8)), content = content)
}
@Composable fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.padding(vertical = 8.dp)) }
@Composable fun Note(text: String, error: Boolean = false) {
    Surface(color = if (error) Color(0xFFFFEDEA) else Color(0xFFEAF0FF), shape = MaterialTheme.shapes.medium) {
        Text(text, Modifier.fillMaxWidth().padding(14.dp), color = if (error) Color(0xFF9E342B) else Color(0xFF294A9C), style = MaterialTheme.typography.bodyMedium)
    }
}
fun dateText(at: Long): String = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))

/** Local libraries only; no JS bridge or external resources can read private app data. */
@SuppressLint("SetJavaScriptEnabled")
@Composable fun MathText(text: String, modifier: Modifier = Modifier) {
    var height by remember(text) { mutableFloatStateOf(24f) }
    val safe = JSONObject.quote(text).replace("<", "\\u003c")
    val html = remember(text) { """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <link rel="stylesheet" href="katex.min.css"><style>
        html,body{margin:0;padding:0;background:transparent;color:#17233d;font:16px/1.75 sans-serif;overflow-wrap:anywhere}
        p{margin:0 0 10px}p:last-child{margin-bottom:0}h1,h2,h3{font-size:18px;margin:10px 0 6px}
        ul,ol{padding-left:22px}pre{white-space:pre-wrap}table{border-collapse:collapse}td,th{border:1px solid #dce2ef;padding:6px}
        .katex-display{overflow-x:auto;overflow-y:hidden;margin:10px 0}.katex{font-size:1.12em}a{color:#3155c5}
        </style></head><body><main id="content">${android.text.TextUtils.htmlEncode(text)}</main>
        <script src="marked.js"></script><script src="purify.min.js"></script>
        <script src="katex.min.js"></script><script src="contrib/auto-render.min.js"></script>
        <script>
        const source=$safe;
        // Protect TeX before Markdown can interpret backslashes or emphasis inside it.
        const formulas=[];
        const protectedText=source.replace(/\$\$[\s\S]*?\$\$|\\\[[\s\S]*?\\\]|\\\([\s\S]*?\\\)|\$[^\n$]+\$/g,m=>{formulas.push(m);return 'MATHPLACEHOLDER'+(formulas.length-1)+'END';});
        let rendered=DOMPurify.sanitize(marked.parse(protectedText),{FORBID_TAGS:['img','iframe','form','input','style'],FORBID_ATTR:['style']});
        rendered=rendered.replace(/MATHPLACEHOLDER(\d+)END/g,(_,i)=>formulas[+i].replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'));
        document.getElementById('content').innerHTML=rendered;
        renderMathInElement(document.getElementById('content'),{throwOnError:false,trust:false,delimiters:[
        {left:'${"$$"}',right:'${"$$"}',display:true},{left:'\\[',right:'\\]',display:true},
        {left:'\\(',right:'\\)',display:false},{left:'${"$"}',right:'${"$"}',display:false}]});
        </script></body></html>
    """.trimIndent() }
    AndroidView(modifier = modifier.fillMaxWidth().height(height.dp), factory = { context ->
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false; settings.allowContentAccess = false; settings.blockNetworkLoads = true
            settings.domStorageEnabled = false
            isVerticalScrollBarEnabled = false
            val loader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context)).build()
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? = loader.shouldInterceptRequest(request.url)
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
                override fun onPageFinished(view: WebView, url: String?) {
                    fun measure() { view.evaluateJavascript("Math.ceil(document.getElementById('content').getBoundingClientRect().height)") { result ->
                        result.toFloatOrNull()?.let { height = (it + 4).coerceIn(24f, 16000f) }
                    } }
                    view.postDelayed({ measure() }, 100); view.postDelayed({ measure() }, 500)
                }
            }
        }
    }, update = { view -> if (view.tag != html) { view.tag = html; view.loadDataWithBaseURL("https://appassets.androidplatform.net/assets/math/", html, "text/html", "UTF-8", null) } },
        onRelease = { it.stopLoading(); it.destroy() })
}

@Composable fun PhotoView(media: MediaFiles, path: String, modifier: Modifier = Modifier,
    region: Region? = null, onRegionChange: ((Region) -> Unit)? = null) {
    var bitmap by remember(path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var loaded by remember(path) { mutableStateOf(false) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) { runCatching { media.preview(path) }.getOrNull() }
        loaded = true
    }
    val source = bitmap
    if (source == null) { Box(modifier.fillMaxWidth().height(100.dp)) { Text(if(loaded) "图片暂不可读，请补拍或从备份恢复" else "正在读取图片…") }; return }
    val image = remember(source) { source.asImageBitmap() }
    val currentRegion by rememberUpdatedState(region ?: Region())
    val onChange by rememberUpdatedState(onRegionChange)
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    fun imageBounds(): Pair<Offset, Offset> {
        val scale = minOf(canvasSize.width.toFloat() / source.width, canvasSize.height.toFloat() / source.height)
        val size = Offset(source.width * scale, source.height * scale)
        return Offset((canvasSize.width - size.x) / 2, (canvasSize.height - size.y) / 2) to size
    }
    Canvas(modifier.fillMaxWidth().height(300.dp).background(Color(0xFFE9EDF4), MaterialTheme.shapes.medium)
        .onSizeChanged { canvasSize = it }.pointerInput(source, onRegionChange != null) {
            if (onChange != null) {
                var corner = 0
                detectDragGestures(onDragStart = { position ->
                    val (origin, size) = imageBounds(); val normalized = Offset((position.x-origin.x)/size.x, (position.y-origin.y)/size.y)
                    val r = currentRegion
                    corner = (if (normalized.x > (r.left+r.right)/2) 1 else 0) + (if (normalized.y > (r.top+r.bottom)/2) 2 else 0)
                }) { change, delta ->
                    change.consume(); val (_, size) = imageBounds(); val r = currentRegion
                    val dx = delta.x / size.x; val dy = delta.y / size.y
                    val updated = r.copy(
                        left = if (corner % 2 == 0) (r.left+dx).coerceIn(0f,r.right-.02f) else r.left,
                        right = if (corner % 2 == 1) (r.right+dx).coerceIn(r.left+.02f,1f) else r.right,
                        top = if (corner < 2) (r.top+dy).coerceIn(0f,r.bottom-.02f) else r.top,
                        bottom = if (corner >= 2) (r.bottom+dy).coerceIn(r.top+.02f,1f) else r.bottom)
                    onChange?.invoke(updated)
                }
            }
        }) {
        val (origin, imageSize) = imageBounds()
        drawImage(image, dstOffset = IntOffset(origin.x.toInt(), origin.y.toInt()), dstSize = IntSize(imageSize.x.toInt(), imageSize.y.toInt()))
        if (region != null) {
            val r = currentRegion
            val left = origin.x + r.left*imageSize.x; val top = origin.y+r.top*imageSize.y
            val width = (r.right-r.left)*imageSize.x; val height = (r.bottom-r.top)*imageSize.y
            drawRect(Blue, Offset(left, top), androidx.compose.ui.geometry.Size(width,height), style = Stroke(3.dp.toPx()))
            listOf(Offset(left,top),Offset(left+width,top),Offset(left,top+height),Offset(left+width,top+height)).forEach { drawCircle(Color.White, 6.dp.toPx(), it); drawCircle(Blue, 6.dp.toPx(), it, style = Stroke(2.dp.toPx())) }
        }
    }
}
