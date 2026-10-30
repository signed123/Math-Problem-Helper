package com.mathtutor.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mathtutor.app.core.*
import com.mathtutor.app.data.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Runs production Activity/Compose/ViewModel/Room together, without substituting a web prototype. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=TutorApplication::class,qualifiers="w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class UiRuntimeTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=(compose.activity.application as TutorApplication).container
    @After fun close(){
        val container=app
        compose.activityRule.scenario.close()
        container.scope.cancel()
        container.repository.db.close()
    }
    @Test fun mainNavigationWorksWithoutCredentials(){
        compose.onNodeWithText("拍题开始学习").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("学习档案").assertIsDisplayed()
        compose.onNodeWithText("复习").performClick()
        compose.onNodeWithText("查找这段时间的题目").assertIsDisplayed()
    }
    @Test fun typedLearningPersistsAcrossActivityRecreation(){
        val id=runBlocking{app.repository.create(QuestionContent("求函数最值",tags=listOf("二次函数")))}
        compose.waitUntil(15000){compose.onAllNodesWithText("继续上次学习").fetchSemanticsNodes().isNotEmpty()}
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("question-$id"))
        compose.onNodeWithTag("question-$id").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("我想先看图像开口")
        compose.onNodeWithText("发送").performClick()
        compose.waitUntil(15000){runBlocking{app.repository.dao.history(id).count{it.kind==Kind.ASSISTANT}>=2}}
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("拍照检查步骤").assertExists()
        assertTrue(runBlocking{app.repository.dao.history(id).any{it.kind==Kind.USER&&it.content<StudentText>().text=="我想先看图像开口"}})
    }
}
