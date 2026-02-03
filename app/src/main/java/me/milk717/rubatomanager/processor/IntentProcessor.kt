package me.milk717.rubatomanager.processor

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.serialization.json.Json
import me.milk717.rubatomanager.data.model.MemoData

class IntentProcessor(private val apiKey: String) {

    private val json = Json { ignoreUnknownKeys = true }

    private val defaultSystemInstruction = """
        당신은 음성 명령을 분석하여 메모의 유형을 분류하고, 핵심 내용만 추출하는 AI입니다.

        ## 작업 1: 의도 분류 (Type Classification)
        - "데일리", "일기", "오늘 기록", "하루 정리", "오늘 할 일", "오늘의" 등 일상 기록과 관련된 내용이면 type을 "dailynote"로 분류합니다.
        - 그 외 일반적인 메모, 아이디어, 할 일, 정보 기록 등은 type을 "plain"으로 분류합니다.
        ## 작업 2: 텍스트 정제 (Content Refinement)
        다음과 같은 불필요한 서술어/접두어를 제거하고 핵심 메모 내용만 추출합니다:
        - "메모해줘", "기록해줘", "저장해줘", "적어줘"
        - "말해줘", "전달해줘", "알려줘"
        - "~이라고", "~라고"
        - 기타 명령형 어미

        ## 출력 형식
        반드시 아래 JSON 형식으로만 응답하세요. 다른 텍스트는 포함하지 마세요.
        {"type": "dailynote 또는 plain", "content": "정제된 메모 내용"}

        ## 예시
        입력: "오늘 회의에서 프로젝트 일정 확정됐다고 메모해줘"
        출력: {"type": "dailynote", "content": "오늘 회의에서 프로젝트 일정 확정됨"}

        입력: "우유 사야 한다고 기록해줘"
        출력: {"type": "plain", "content": "우유 사야 함"}

        입력: "오늘 운동 30분 했다고 일기에 적어줘"
        출력: {"type": "dailynote", "content": "운동 30분 완료"}
    """.trimIndent()

    private var cachedPrompt: String? = null
    private var generativeModel: GenerativeModel = createModel(defaultSystemInstruction)

    fun updatePrompt(newPrompt: String) {
        if (newPrompt.isNotBlank() && newPrompt != cachedPrompt) {
            cachedPrompt = newPrompt
            generativeModel = createModel(newPrompt)
        }
    }

    fun getCurrentPrompt(): String = cachedPrompt ?: defaultSystemInstruction

    private fun createModel(systemInstruction: String): GenerativeModel {
        return GenerativeModel(
            modelName = "gemini-2.0-flash",
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.1f
                maxOutputTokens = 256
            },
            systemInstruction = content { text(systemInstruction) }
        )
    }

    suspend fun process(inputText: String): Result<MemoData> {
        return try {
            val response = generativeModel.generateContent(inputText)
            val responseText = response.text?.trim() ?: throw Exception("Empty response from Gemini")

            val cleanedJson = extractJson(responseText)
            val memoData = json.decodeFromString<MemoData>(cleanedJson)

            Result.success(memoData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractJson(text: String): String {
        val startIndex = text.indexOf('{')
        val endIndex = text.lastIndexOf('}')

        return if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            text.substring(startIndex, endIndex + 1)
        } else {
            text
        }
    }
}
