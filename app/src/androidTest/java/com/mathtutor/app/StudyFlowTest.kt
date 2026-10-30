package com.mathtutor.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mathtutor.app.core.*
import com.mathtutor.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val container get() = (compose.activity.application as TutorApplication).container
    @Before fun clear() = runBlocking {
        container.repository.dao.clearBatches(); container.repository.dao.clearQuestions()
        container.settingsStore.save(TutorSettings(demo=true,voice=false))
    }
    private fun seed(): String = runBlocking { container.repository.create(QuestionContent("已知函数 \\(f(x)=x^2-4x+3\\)，求最小值。",tags=listOf("二次函数"))) }
    @Test fun homeAndSettingsAreUsableWithoutAnApiKey() {
        compose.onNodeWithText("拍题开始学习").assertIsDisplayed()
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("学习档案").assertIsDisplayed()
        compose.onNodeWithText("年级").assertExists()
        compose.onNodeWithText("服务连接").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("演示模式").assertExists()
    }
    @Test fun typedGuidanceIsSavedAndSurvivesActivityRecreation() {
        val id=seed()
        compose.waitUntil(60000){compose.onAllNodesWithTag("question-$id").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("question-$id").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("我想先看图像开口")
        compose.onNodeWithText("发送").performClick()
        compose.waitUntil(60000){runBlocking{container.repository.dao.history(id).count{it.kind==Kind.ASSISTANT}>=2}}
        compose.onNodeWithText("正在一起理清思路").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("拍照检查步骤").assertExists()
        val history=runBlocking{container.repository.dao.history(id)}
        assertTrue(history.any{it.kind==Kind.USER&&it.content<StudentText>().text=="我想先看图像开口"})
        assertTrue(history.any{it.kind==Kind.ASSISTANT&&it.content<TutorReply>().hintLevel>0})
    }
    @Test fun selectedDateRangeCreatesAndPersistsAReviewBatch() {
        seed()
        compose.onNodeWithText("复习").performClick()
        compose.onNodeWithText("查找这段时间的题目").performClick()
        compose.waitUntil(60000){compose.onAllNodesWithText("已选 1 / 1 道题").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("生成这段时间的复习").performScrollTo().performClick()
        compose.waitUntil(60000){runBlocking{container.repository.dao.allBatches().any{it.status=="done"}}}
        compose.onNodeWithText("这一阶段的复习").assertIsDisplayed()
        val batch=runBlocking{container.repository.dao.allBatches().single()}
        assertEquals(1,runBlocking{container.repository.dao.batchItems(batch.id).size})
        assertTrue(batch.aggregate.isNotBlank())
    }
}
