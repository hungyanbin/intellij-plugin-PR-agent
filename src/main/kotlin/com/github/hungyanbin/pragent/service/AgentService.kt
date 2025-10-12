package com.github.hungyanbin.pragent.service

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.llm.LLModel
import com.github.hungyanbin.pragent.domain.LLMProvider
import com.github.hungyanbin.pragent.repository.SecretRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AgentService(
    private val secretRepository: SecretRepository
) {

    suspend fun generatePRNotes(apiKey: String, prompt: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val agent = AIAgent(
                    promptExecutor = simpleAnthropicExecutor(apiKey),
                    systemPrompt = """
                        You are an expert code reviewer and technical writer specializing in creating clear,
                        concise pull request descriptions. Your goal is to help developers quickly understand
                        code changes by analyzing git diffs, commit messages, and branch information.

                        Focus on:
                        - Summarizing the main purpose and impact of changes
                        - Identifying key modifications and their rationale
                        - Highlighting breaking changes or important considerations
                        - Using clear, professional language that reviewers can scan quickly

                        Avoid:
                        - Implementation details that are evident from the code
                        - Verbose explanations or unnecessary context
                        - Testing checklists or procedural steps
                        - Repeating information already in commit messages
                    """.trimIndent(),
                    llmModel = getLLMModel()
                )

                agent.run(prompt)
            } catch (e: Exception) {
                "Error generating PR notes: ${e.message}"
            }
        }
    }

    private fun getLLMModel(): LLModel {
        val llmProvider = secretRepository.getLLMProvider() ?: throw IllegalStateException("LLMProvider is not set")
        val currentProvider = LLMProvider.valueOf(llmProvider)
        val model = secretRepository.getLLMModel() ?: throw IllegalStateException("LLModel is not set")
        return currentProvider.modelMap.filterValues { modelName -> modelName == model }
            .keys.first()
    }
}