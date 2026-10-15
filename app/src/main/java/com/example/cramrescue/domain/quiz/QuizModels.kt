package com.example.cramrescue.domain.quiz

data class DraftQuizQuestion(
    val stem: String,
    val options: List<String>,
    val correctOptionIndex: Int,
    val explanation: String,
    val sourceBlockIds: List<String>
)

data class QuizDraft(
    val title: String,
    val questions: List<DraftQuizQuestion>
)

data class QuizPrompt(
    val systemInstruction: String,
    val input: String
)

interface QuizClient {
    val modelId: String
    suspend fun generateQuiz(apiKey: String, prompt: QuizPrompt): QuizDraft
}
