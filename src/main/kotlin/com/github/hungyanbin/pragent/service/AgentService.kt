package com.github.hungyanbin.pragent.service

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
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
                    promptExecutor = getPromptExecutor(apiKey),
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
                val errorMessage = "Error generating PR notes: ${e.message}"
                ErrorLogger.getInstance().logError(errorMessage, e)
                errorMessage
            }
        }
    }

    suspend fun generatePRTitle(apiKey: String, prNote: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val agent = AIAgent(
                    promptExecutor = getPromptExecutor(apiKey),
                    systemPrompt = """
                        You are an expert at creating concise, informative pull request titles.
                        Your goal is to summarize the main purpose of code changes in a single clear sentence.

                        Focus on:
                        - Being concise (under 100 characters)
                        - Using action verbs (Add, Fix, Update, Refactor, etc.)
                        - Highlighting the key feature or change
                        - Being specific and informative

                        Avoid:
                        - Being too verbose or detailed
                        - Including unnecessary punctuation or formatting
                        - Starting with "PR:" or similar prefixes
                        - Using markdown or special characters
                    """.trimIndent(),
                    llmModel = getLLMModel()
                )

                val prompt = """
                    Based on the following pull request description, generate a concise title (under 100 characters).
                    Only respond with the title text, nothing else.

                    Pull Request Description:
                    $prNote
                """.trimIndent()

                agent.run(prompt)
            } catch (e: Exception) {
                val errorMessage = "Error generating PR title: ${e.message}"
                ErrorLogger.getInstance().logError(errorMessage, e)
                ""
            }
        }
    }

    private fun getPromptExecutor(apiKey: String) = when (getCurrentProvider()) {
        LLMProvider.Anthropic -> simpleAnthropicExecutor(apiKey)
        LLMProvider.Google -> simpleGoogleAIExecutor(apiKey)
        LLMProvider.OpenAI -> simpleOpenAIExecutor(apiKey)
        LLMProvider.OpenRouter -> simpleOpenRouterExecutor(apiKey)
        LLMProvider.Ollama -> simpleOllamaAIExecutor()
        LLMProvider.DeepSeek -> throw UnsupportedOperationException("DeepSeek provider is not yet supported")
        LLMProvider.Bedrock -> throw UnsupportedOperationException("Bedrock provider is not yet supported")
    }

    private fun getCurrentProvider(): LLMProvider {
        val llmProvider = secretRepository.getLLMProvider() ?: throw IllegalStateException("LLMProvider is not set")
        return LLMProvider.valueOf(llmProvider)
    }

    private fun getLLMModel(): LLModel {
        val currentProvider = getCurrentProvider()
        val model = secretRepository.getLLMModel() ?: throw IllegalStateException("LLModel is not set")
        return currentProvider.modelMap.filterValues { modelName -> modelName == model }
            .keys.first()
    }
}