package com.mathtutor.app

import com.mathtutor.app.ai.BailianClient
import com.mathtutor.app.core.*
import com.mathtutor.app.data.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class BailianClientTest {
    private lateinit var server:MockWebServer
    private lateinit var client:BailianClient
    private lateinit var image:File
    @Before fun setup(){server=MockWebServer();server.start();image=File.createTempFile("math-image",".jpg").apply{writeBytes(byteArrayOf(1,2,3))};client=BailianClient({"test-key"},{TutorSettings(demo=false)},{image},base=server.url("/").toString().trimEnd('/'))}
    @After fun close(){server.shutdown();image.delete()}
    private fun completion(content:String)=MockResponse().setHeader("Content-Type","application/json").setBody(buildJsonObject{putJsonArray("choices"){add(buildJsonObject{putJsonObject("message"){put("content",content)}})}}.toString())
    @Test fun recognitionSendsImageJsonModeAndNonThinking()=runBlocking{
        server.enqueue(completion(AppJson.encodeToString(Recognition(listOf(DetectedQuestion("已知函数"))))))
        assertEquals("已知函数",client.recognize("images/test.jpg").questions.first().text)
        val request=server.takeRequest();assertEquals("Bearer test-key",request.getHeader("Authorization"))
        val body=AppJson.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals(false,body["enable_thinking"]!!.jsonPrimitive.boolean)
        assertEquals("json_object",body["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertTrue(body.toString().contains("data:image/jpeg;base64,"));assertFalse(body["stream"]?.jsonPrimitive?.boolean?:false)
    }
    @Test fun unsafeGuidanceIsReplacedAndValidatedBeforeReturning()=runBlocking{
        server.enqueue(completion(AppJson.encodeToString(TutorReply("配方后直接得到最小值是负一。","最小值是负一。"))))
        server.enqueue(completion(AppJson.encodeToString(GuidanceValidation(false,"提前给出了答案"))))
        server.enqueue(completion(AppJson.encodeToString(TutorReply("你能先把二次式配方吗？","你能先配方吗？",hintLevel=2))))
        server.enqueue(completion(AppJson.encodeToString(GuidanceValidation(true))))
        val result=client.guide(QuestionContent("求二次函数最值"),emptyList(),StudentText("怎么入手",1),"高二")
        assertFalse(result.displayMarkdown.contains("负一"));assertEquals(4,server.requestCount)
    }
    @Test fun unknownReviewEvidenceCannotBecomeAnAcceptedSummary()=runBlocking{
        server.enqueue(completion(AppJson.encodeToString(ReviewSummary(stickingPoints="编造的错误",evidenceIds=listOf("invented")))))
        val record=StudyEvent("real","q","s",Kind.USER,1,AppJson.encodeToString(StudentText("我想配方")),true)
        assertTrue(runCatching{client.review(QuestionContent("题目"),listOf(record))}.isFailure)
    }
    @Test fun failedAuthenticationUsesActionableMessageWithoutLeakingTheKey()=runBlocking{
        server.enqueue(MockResponse().setResponseCode(401).setBody("secret server body"))
        val error=runCatching{client.ping()}.exceptionOrNull()!!
        assertTrue(error.message!!.contains("API Key"));assertFalse(error.message!!.contains("test-key"));assertFalse(error.message!!.contains("secret server"))
    }
    @Test fun cancellationInterruptsPendingNetworkWork()=runBlocking{
        server.enqueue(completion("{\"ok\":true}").setBodyDelay(2,TimeUnit.SECONDS))
        val start=System.nanoTime()
        assertTrue(runCatching{withTimeout(200){client.ping()}}.exceptionOrNull() is TimeoutCancellationException)
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<2000)
    }
    @Test fun speechTranscriptionUsesAudioContentAndPreservesTextForEditing()=runBlocking{
        server.enqueue(completion("我想先配方"))
        val wav=File.createTempFile("recording",".wav").apply{writeBytes(byteArrayOf(1,2))}
        try{assertEquals("我想先配方",client.transcribe(wav));val body=server.takeRequest().body.readUtf8();assertTrue(body.contains("input_audio"));assertTrue(body.contains("data:audio/wav;base64,"))}finally{wav.delete()}
    }
    @Test fun reviewOverviewKeepsDisplayMathSeparateFromSpokenChinese()=runBlocking{
        val overview=ReviewOverview("## 配方法\n看到 \\(x^2\\) 时检查二次项系数。","看到平方项时，先检查二次项系数。")
        server.enqueue(completion(AppJson.encodeToString(overview)))
        val result=client.aggregate(mapOf("题目1" to ReviewSummary(concepts=listOf("配方法"))))
        assertTrue(result.displayMarkdown.contains("x^2"))
        assertFalse(result.speechText.contains("x^2"))
        assertTrue(result.speechText.contains("平方项"))
    }
}
