package com.github.hungyanbin.pragent.repository

import com.github.hungyanbin.pragent.domain.LLMProvider
import com.github.hungyanbin.pragent.service.ErrorLogger
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.util.PropertiesComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SecretRepository {

    private val anthropicApiKeyAttribute = CredentialAttributes(
        "anthropic",
        "anthropic_api_key"
    )

    private val googleApiKeyAttribute = CredentialAttributes(
        "google",
        "google_api_key"
    )

    private val openaiApiKeyAttribute = CredentialAttributes(
        "openai",
        "openai_api_key"
    )

    private val deepseekApiKeyAttribute = CredentialAttributes(
        "deepseek",
        "deepseek_api_key"
    )

    private val openrouterApiKeyAttribute = CredentialAttributes(
        "openrouter",
        "openrouter_api_key"
    )

    private val ollamaApiKeyAttribute = CredentialAttributes(
        "ollama",
        "ollama_api_key"
    )

    private val bedrockApiKeyAttribute = CredentialAttributes(
        "bedrock",
        "bedrock_api_key"
    )

    private val githubPatAttribute = CredentialAttributes(
        "github",
        "github_pat"
    )

    companion object {
        private const val LLM_PROVIDER_KEY = "com.github.hungyanbin.pragent.llm.provider"
        private const val LLM_MODEL_KEY = "com.github.hungyanbin.pragent.llm.model"
        private const val INCLUDE_PR_TEMPLATE_KEY = "com.github.hungyanbin.pragent.github.include_pr_template"
    }

    private fun getCredentialAttributesByProvider(provider: LLMProvider): CredentialAttributes {
        return when (provider) {
            LLMProvider.Anthropic -> anthropicApiKeyAttribute
            LLMProvider.Google -> googleApiKeyAttribute
            LLMProvider.OpenAI -> openaiApiKeyAttribute
            LLMProvider.DeepSeek -> deepseekApiKeyAttribute
            LLMProvider.OpenRouter -> openrouterApiKeyAttribute
            LLMProvider.Ollama -> ollamaApiKeyAttribute
            LLMProvider.Bedrock -> bedrockApiKeyAttribute
        }
    }

    suspend fun storeKeyByLLMProvider(provider: LLMProvider, apiKey: String) = withContext(Dispatchers.IO) {
        val credentialAttributes = getCredentialAttributesByProvider(provider)
        PasswordSafe.instance.setPassword(credentialAttributes, apiKey)
    }

    suspend fun getKeyByCurrentLLMProvider(): String? = withContext(Dispatchers.IO) {
        val providerName = getLLMProvider() ?: return@withContext null
        val provider = try {
            LLMProvider.valueOf(providerName)
        } catch (e: Throwable) {
            ErrorLogger.getInstance().logError("Failed to getLLMProvider ${e.message}", e)
            return@withContext null
        }

        return@withContext getKeyByLLMProvider(provider)
    }

    suspend fun getKeyByLLMProvider(provider: LLMProvider): String? = withContext(Dispatchers.IO) {
        val credentialAttributes = getCredentialAttributesByProvider(provider)
        return@withContext try {
            PasswordSafe.instance.getPassword(credentialAttributes)
        } catch (e: Exception) {
            ErrorLogger.getInstance().logError("Failed to get key ${e.message}", e)
            null
        }
    }

    suspend fun storeGithubPat(pat: String) = withContext(Dispatchers.IO) {
        PasswordSafe.instance.setPassword(githubPatAttribute, pat)
    }

    suspend fun getGithubPat(): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val result = PasswordSafe.instance.getPassword(githubPatAttribute)
            result
        } catch (e: Exception) {
            ErrorLogger.getInstance().logError("Failed to getGithubPat ${e.message}", e)
            null
        }
    }

    suspend fun clearGithubPat() = withContext(Dispatchers.IO) {
        PasswordSafe.instance.setPassword(githubPatAttribute, null)
    }

    // Store and retrieve LLM provider name (non-sensitive)
    fun storeLLMProvider(providerName: String) {
        PropertiesComponent.getInstance().setValue(LLM_PROVIDER_KEY, providerName)
    }

    fun getLLMProvider(): String? {
        return PropertiesComponent.getInstance().getValue(LLM_PROVIDER_KEY)
    }

    // Store and retrieve LLM model name (non-sensitive)
    fun storeLLMModel(modelName: String) {
        PropertiesComponent.getInstance().setValue(LLM_MODEL_KEY, modelName)
    }

    fun getLLMModel(): String? {
        return PropertiesComponent.getInstance().getValue(LLM_MODEL_KEY)
    }

    // Store and retrieve include PR template setting (non-sensitive)
    fun storeIncludePRTemplate(include: Boolean) {
        PropertiesComponent.getInstance().setValue(INCLUDE_PR_TEMPLATE_KEY, include)
    }

    fun getIncludePRTemplate(): Boolean {
        return PropertiesComponent.getInstance().getBoolean(INCLUDE_PR_TEMPLATE_KEY, true)
    }
}