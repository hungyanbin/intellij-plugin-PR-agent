package com.github.hungyanbin.pragent.domain

import ai.koog.prompt.executor.clients.anthropic.AnthropicModels.Haiku_3
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels.Haiku_3_5
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels.Opus_3
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels.Opus_4
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels.Opus_4_1
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels.Sonnet_3_5
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels.Sonnet_3_7
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels.Sonnet_4
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels.Sonnet_4_5
import ai.koog.prompt.llm.LLModel

enum class LLMProvider(
    val modelMap: Map<LLModel, String>
) {

    Anthropic(
        mapOf(
            Opus_3 to "claude-3-opus-20240229",
            Haiku_3 to "claude-3-haiku-20240307",
            Sonnet_3_5 to "claude-3-5-sonnet-20241022",
            Haiku_3_5 to "claude-3-5-haiku-20241022",
            Sonnet_3_7 to "claude-3-7-sonnet-20250219",
            Sonnet_4 to "claude-sonnet-4-20250514",
            Opus_4 to "claude-opus-4-20250514",
            Opus_4_1 to "claude-opus-4-1-20250805",
            Sonnet_4_5 to "claude-sonnet-4-5-20250929",
        )
    ),
    Google(
        mapOf()
    ),
    OpenAI(
        mapOf()
    ),
    DeepSeek(
        mapOf()
    ),
    OpenRouter(
        mapOf()
    ),
    Ollama(
        mapOf()
    ),
    Bedrock(
        mapOf()
    )
}