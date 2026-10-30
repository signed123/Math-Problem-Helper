package com.mathtutor.app.ai

import com.mathtutor.app.core.*
import com.mathtutor.app.data.*

interface MathService {
    suspend fun recognize(image: String): Recognition
    suspend fun guide(question: QuestionContent, history: List<StudyEvent>, request: StudentText, grade: String): TutorReply
    suspend fun check(question: QuestionContent, image: String, history: List<StudyEvent>): CheckResult
    suspend fun summarize(question: QuestionContent, history: List<StudyEvent>, reflection: String, comparison: QuestionContent? = null): KnowledgeCard
    suspend fun practice(question: QuestionContent, card: KnowledgeCard?): PracticeDraft
    suspend fun validatePractice(draft: PracticeDraft): PracticeValidation
    suspend fun review(question: QuestionContent, history: List<StudyEvent>): ReviewSummary
    suspend fun aggregate(summaries: Map<String, ReviewSummary>): ReviewOverview
    suspend fun ping(): String
}
interface SpeechService {
    suspend fun transcribe(audio: java.io.File): String
    suspend fun synthesize(text: String): ByteArray
}

/** Demonstration fixtures never claim to recognize or grade an uploaded photo. */
class DemoMathService : MathService {
    override suspend fun recognize(image: String) = Recognition(listOf(DetectedQuestion(
        "【演示题】已知函数 \\(f(x)=x^2-4x+3\\)，求其最小值，并说明理由。", tags = listOf("二次函数", "最值"))), "演示模式不识别照片。这里显示固定示例题；填写 API Key 后可识别真实图片。")
    override suspend fun guide(question: QuestionContent, history: List<StudyEvent>, request: StudentText, grade: String): TutorReply {
        val text = when {
            request.action == "foundation" || "没学过" in request.text -> "我们先补一个概念：配方是把二次式写成一个平方项与常数的和。平方项能取到的最小值是多少？"
            request.action == "rephrase" -> "先只看一个问题：这个函数的二次项系数是正数，图像开口向哪边？"
            request.requestedHint >= 3 -> "可以先将 \\(x^2-4x\\) 补成完全平方：需要加上什么数？补进去之后，原式的值应该怎样保持不变？"
            request.requestedHint >= 2 -> "想想配方法。怎样把题目里的二次式写成一个平方项加常数？"
            else -> "这是演示引导，只围绕固定示例题。要求二次函数最值，你准备从图像还是配方入手？先说说原因。"
        }
        return TutorReply(text, text.replace(Regex("\\\\[()]"), ""), "question", maxOf(1, request.requestedHint))
    }
    override suspend fun check(question: QuestionContent, image: String, history: List<StudyEvent>) = CheckResult("uncertain", "演示模式无法读取或判断你的作答。请配置百炼服务后检查真实照片。")
    override suspend fun summarize(question: QuestionContent, history: List<StudyEvent>, reflection: String, comparison: QuestionContent?) = KnowledgeCard(
        listOf("演示总结"), "观察题型和已知条件", "先明确目标，再选择已经学过的方法。", "演示内容不代表真实学情。", listOf("我为什么选择这个方法？"), history.filter { it.kind == Kind.USER }.map { it.id }, reflection)
    override suspend fun practice(question: QuestionContent, card: KnowledgeCard?) = PracticeDraft("【演示变式】求函数 \\(g(x)=x^2-6x+5\\) 的最小值，并说明理由。", listOf("二次函数"), "配方后检查平方项的最小值", "仅供内部校验")
    override suspend fun validatePractice(draft: PracticeDraft) = PracticeValidation("verified", "固定演示样例已预先核对；这不是对任意生成题目的数学保证。")
    override suspend fun review(question: QuestionContent, history: List<StudyEvent>) = ReviewSummary(listOf("演示回顾"), "演示模式仅展示复习结构，不分析真实学习记录。", "需要真实模型才能归纳个人卡点。", "结合原题与交流记录回顾。", "配置服务后重新生成。", history.take(3).map { it.id })
    override suspend fun aggregate(summaries: Map<String, ReviewSummary>) = ReviewOverview("## 演示复习提纲\n\n共选取 ${summaries.size} 道题。这里展示的是演示结构，请配置百炼后生成真实的知识点归纳和卡点分析。", "这是演示复习提纲，请配置服务后生成真实回顾。")
    override suspend fun ping() = "演示模式可用；未调用真实模型"
}
