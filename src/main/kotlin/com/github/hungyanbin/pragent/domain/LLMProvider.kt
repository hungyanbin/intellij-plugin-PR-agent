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
import ai.koog.prompt.executor.clients.bedrock.BedrockModels.AI21JambaLarge
import ai.koog.prompt.executor.clients.bedrock.BedrockModels.AI21JambaMini
import ai.koog.prompt.executor.clients.bedrock.BedrockModels.AmazonNovaLite
import ai.koog.prompt.executor.clients.bedrock.BedrockModels.AmazonNovaMicro
import ai.koog.prompt.executor.clients.bedrock.BedrockModels.AmazonNovaPremier
import ai.koog.prompt.executor.clients.bedrock.BedrockModels.AmazonNovaPro
import ai.koog.prompt.executor.clients.bedrock.BedrockModels.AnthropicClaude21
import ai.koog.prompt.executor.clients.bedrock.BedrockModels.AnthropicClaude35Haiku
import ai.koog.prompt.executor.clients.bedrock.BedrockModels.AnthropicClaude35SonnetV2
import ai.koog.prompt.executor.clients.bedrock.BedrockModels.AnthropicClaude3Haiku
import ai.koog.prompt.executor.clients.bedrock.BedrockModels.AnthropicClaude3Opus
import ai.koog.prompt.executor.clients.bedrock.BedrockModels.AnthropicClaude3Sonnet
import ai.koog.prompt.executor.clients.bedrock.BedrockModels.AnthropicClaude41Opus
import ai.koog.prompt.executor.clients.bedrock.BedrockModels.AnthropicClaude4Opus
import ai.koog.prompt.executor.clients.bedrock.BedrockModels.AnthropicClaude4Sonnet
import ai.koog.prompt.executor.clients.bedrock.BedrockModels.AnthropicClaude4_5Sonnet
import ai.koog.prompt.executor.clients.bedrock.BedrockModels.AnthropicClaudeInstant
import ai.koog.prompt.executor.clients.bedrock.BedrockModels.MetaLlama3_0_70BInstruct
import ai.koog.prompt.executor.clients.bedrock.BedrockModels.MetaLlama3_0_8BInstruct
import ai.koog.prompt.executor.clients.bedrock.BedrockModels.MetaLlama3_1_405BInstruct
import ai.koog.prompt.executor.clients.bedrock.BedrockModels.MetaLlama3_1_70BInstruct
import ai.koog.prompt.executor.clients.bedrock.BedrockModels.MetaLlama3_1_8BInstruct
import ai.koog.prompt.executor.clients.bedrock.BedrockModels.MetaLlama3_2_11BInstruct
import ai.koog.prompt.executor.clients.bedrock.BedrockModels.MetaLlama3_2_1BInstruct
import ai.koog.prompt.executor.clients.bedrock.BedrockModels.MetaLlama3_2_3BInstruct
import ai.koog.prompt.executor.clients.bedrock.BedrockModels.MetaLlama3_2_90BInstruct
import ai.koog.prompt.executor.clients.bedrock.BedrockModels.MetaLlama3_3_70BInstruct
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels.DeepSeekChat
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels.DeepSeekReasoner
import ai.koog.prompt.executor.clients.google.GoogleModels.Gemini2_0Flash
import ai.koog.prompt.executor.clients.google.GoogleModels.Gemini2_0Flash001
import ai.koog.prompt.executor.clients.google.GoogleModels.Gemini2_0FlashLite
import ai.koog.prompt.executor.clients.google.GoogleModels.Gemini2_0FlashLite001
import ai.koog.prompt.executor.clients.google.GoogleModels.Gemini2_5Flash
import ai.koog.prompt.executor.clients.google.GoogleModels.Gemini2_5FlashLite
import ai.koog.prompt.executor.clients.google.GoogleModels.Gemini2_5Pro
import ai.koog.prompt.executor.clients.openai.OpenAIModels.Audio.GPT4oAudio
import ai.koog.prompt.executor.clients.openai.OpenAIModels.Audio.GPT4oMiniAudio
import ai.koog.prompt.executor.clients.openai.OpenAIModels.Audio.GptAudio
import ai.koog.prompt.executor.clients.openai.OpenAIModels.Chat.GPT4_1
import ai.koog.prompt.executor.clients.openai.OpenAIModels.Chat.GPT4o
import ai.koog.prompt.executor.clients.openai.OpenAIModels.Chat.GPT5
import ai.koog.prompt.executor.clients.openai.OpenAIModels.Chat.GPT5Mini
import ai.koog.prompt.executor.clients.openai.OpenAIModels.Chat.GPT5Nano
import ai.koog.prompt.executor.clients.openai.OpenAIModels.CostOptimized.GPT4_1Mini
import ai.koog.prompt.executor.clients.openai.OpenAIModels.CostOptimized.GPT4_1Nano
import ai.koog.prompt.executor.clients.openai.OpenAIModels.CostOptimized.GPT4oMini
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.OllamaModels.Alibaba.QWEN_2_5_05B
import ai.koog.prompt.llm.OllamaModels.Alibaba.QWEN_3_06B
import ai.koog.prompt.llm.OllamaModels.Alibaba.QWEN_CODER_2_5_32B
import ai.koog.prompt.llm.OllamaModels.Alibaba.QWQ_32B
import ai.koog.prompt.llm.OllamaModels.Granite.GRANITE_3_2_VISION
import ai.koog.prompt.llm.OllamaModels.Groq.LLAMA_3_GROK_TOOL_USE_70B
import ai.koog.prompt.llm.OllamaModels.Groq.LLAMA_3_GROK_TOOL_USE_8B
import ai.koog.prompt.llm.OllamaModels.Meta.LLAMA_3_2
import ai.koog.prompt.llm.OllamaModels.Meta.LLAMA_3_2_3B
import ai.koog.prompt.llm.OllamaModels.Meta.LLAMA_4_SCOUT
import ai.koog.prompt.llm.OllamaModels.Meta.LLAMA_GUARD_3
import ai.koog.prompt.executor.clients.openai.OpenAIModels.Reasoning.O3Mini as ReasoningO3Mini
import ai.koog.prompt.executor.clients.openai.OpenAIModels.Reasoning.O4Mini as ReasoningO4Mini
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.Claude3Haiku as ORClaude3Haiku
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.Claude3Opus as ORClaude3Opus
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.Claude3Sonnet as ORClaude3Sonnet
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.Claude3VisionHaiku as ORClaude3VisionHaiku
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.Claude3VisionOpus as ORClaude3VisionOpus
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.Claude3VisionSonnet as ORClaude3VisionSonnet
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.Claude3_5Sonnet as ORClaude3_5Sonnet
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.Claude3_7Sonnet as ORClaude3_7Sonnet
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.Claude4Sonnet as ORClaude4Sonnet
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.Claude4_1Opus as ORClaude4_1Opus
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.DeepSeekV30324 as ORDeepSeekV30324
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.GPT35Turbo as ORGPT35Turbo
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.GPT4 as ORGPT4
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.GPT4Turbo as ORGPT4Turbo
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.GPT4o as ORGPT4o
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.GPT4oMini as ORGPT4oMini
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.GPT5 as ORGPT5
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.GPT5Chat as ORGPT5Chat
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.GPT5Mini as ORGPT5Mini
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.GPT5Nano as ORGPT5Nano
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.GPT_OSS_120b as ORGPT_OSS_120b
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.Gemini2_5Flash as ORGemini2_5Flash
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.Gemini2_5FlashLite as ORGemini2_5FlashLite
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.Gemini2_5Pro as ORGemini2_5Pro
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.Llama3 as ORLlama3
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.Llama3Instruct as ORLlama3Instruct
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.Mistral7B as ORMistral7B
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.Mixtral8x7B as ORMixtral8x7B
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels.Phi4Reasoning as ORPhi4Reasoning

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
        mapOf(
            Gemini2_0Flash to "gemini-2.0-flash",
            Gemini2_0Flash001 to "gemini-2.0-flash-001",
            Gemini2_0FlashLite to "gemini-2.0-flash-lite",
            Gemini2_0FlashLite001 to "gemini-2.0-flash-lite-001",
            Gemini2_5Pro to "gemini-2.5-pro",
            Gemini2_5Flash to "gemini-2.5-flash",
            Gemini2_5FlashLite to "gemini-2.5-flash-lite",
        )
    ),
    OpenAI(
        mapOf(
            // Reasoning models (Medium speed)
            ReasoningO4Mini to "o4-mini",
            ReasoningO3Mini to "o3-mini",

            // Chat models
            GPT4o to "gpt-4o",
            GPT4_1 to "gpt-4.1",
            GPT5 to "gpt-5",
            GPT5Mini to "gpt-5-mini",
            GPT5Nano to "gpt-5-nano",

            // Audio models
            GptAudio to "gpt-audio",
            GPT4oMiniAudio to "gpt-4o-mini-audio-preview",
            GPT4oAudio to "gpt-4o-audio-preview",

            // Cost-optimized models
            GPT4_1Nano to "gpt-4.1-nano",
            GPT4_1Mini to "gpt-4.1-mini",
            GPT4oMini to "gpt-4o-mini",
        )
    ),
    DeepSeek(
        mapOf(
            DeepSeekChat to "deepseek-chat",
            DeepSeekReasoner to "deepseek-reasoner",
        )
    ),
    OpenRouter(
        mapOf(
            // Free model
            ORPhi4Reasoning to "microsoft/phi-4-reasoning:free",

            // Claude models
            ORClaude3Opus to "anthropic/claude-3-opus",
            ORClaude3Sonnet to "anthropic/claude-3-sonnet",
            ORClaude3Haiku to "anthropic/claude-3-haiku",
            ORClaude3_5Sonnet to "anthropic/claude-3.5-sonnet",
            ORClaude3_7Sonnet to "anthropic/claude-3.7-sonnet",
            ORClaude4Sonnet to "anthropic/claude-sonnet-4",
            ORClaude4_1Opus to "anthropic/claude-opus-4.1",

            // Claude Vision models
            ORClaude3VisionSonnet to "anthropic/claude-3-sonnet-vision",
            ORClaude3VisionOpus to "anthropic/claude-3-opus-vision",
            ORClaude3VisionHaiku to "anthropic/claude-3-haiku-vision",

            // OpenAI models
            ORGPT35Turbo to "openai/gpt-3.5-turbo",
            ORGPT4 to "openai/gpt-4",
            ORGPT4o to "openai/gpt-4o",
            ORGPT4Turbo to "openai/gpt-4-turbo",
            ORGPT4oMini to "openai/gpt-4o-mini",
            ORGPT5 to "openai/gpt-5",
            ORGPT5Chat to "openai/gpt-5-chat",
            ORGPT5Mini to "openai/gpt-5-mini",
            ORGPT5Nano to "openai/gpt-5-nano",
            ORGPT_OSS_120b to "openai/gpt-oss-120b",

            // Google Gemini models
            ORGemini2_5FlashLite to "google/gemini-2.5-flash-lite",
            ORGemini2_5Flash to "google/gemini-2.5-flash",
            ORGemini2_5Pro to "google/gemini-2.5-pro",

            // DeepSeek models
            ORDeepSeekV30324 to "deepseek/deepseek-chat-v3-0324",

            // Meta Llama models
            ORLlama3 to "meta/llama-3-70b",
            ORLlama3Instruct to "meta/llama-3-70b-instruct",

            // Mistral models
            ORMistral7B to "mistral/mistral-7b",
            ORMixtral8x7B to "mistral/mixtral-8x7b",
        )
    ),
    Ollama(
        mapOf(
            // Groq models
            LLAMA_3_GROK_TOOL_USE_8B to "llama3-groq-tool-use:8b",
            LLAMA_3_GROK_TOOL_USE_70B to "llama3-groq-tool-use:70b",

            // Meta models
            LLAMA_3_2_3B to "llama3.2:3b",
            LLAMA_3_2 to "llama3.2:latest",
            LLAMA_4_SCOUT to "llama4:latest",
            LLAMA_GUARD_3 to "llama-guard3:latest",

            // Alibaba models
            QWEN_2_5_05B to "qwen2.5:0.5b",
            QWEN_3_06B to "qwen3:0.6b",
            QWQ_32B to "qwq:32b",
            QWEN_CODER_2_5_32B to "qwen2.5-coder:32b",

            // Granite models
            GRANITE_3_2_VISION to "granite3.2-vision",
        )
    ),
    Bedrock(
        mapOf(
            // Anthropic Claude models
            AnthropicClaude3Opus to "us.anthropic.claude-3-opus-20240229-v1:0",
            AnthropicClaude4Opus to "us.anthropic.claude-opus-4-20250514-v1:0",
            AnthropicClaude41Opus to "us.anthropic.claude-opus-4-1-20250805-v1:0",
            AnthropicClaude4Sonnet to "us.anthropic.claude-sonnet-4-20250514-v1:0",
            AnthropicClaude4_5Sonnet to "us.anthropic.claude-sonnet-4-5-20250929-v1:0",
            AnthropicClaude3Sonnet to "us.anthropic.claude-3-sonnet-20240229-v1:0",
            AnthropicClaude35SonnetV2 to "us.anthropic.claude-3-5-sonnet-20241022-v2:0",
            AnthropicClaude35Haiku to "us.anthropic.claude-3-5-haiku-20241022-v1:0",
            AnthropicClaude3Haiku to "us.anthropic.claude-3-haiku-20240307-v1:0",
            AnthropicClaude21 to "us.anthropic.claude-v2:1",
            AnthropicClaudeInstant to "us.anthropic.claude-instant-v1",

            // Amazon Nova models
            AmazonNovaMicro to "us.amazon.nova-micro-v1:0",
            AmazonNovaLite to "us.amazon.nova-lite-v1:0",
            AmazonNovaPro to "us.amazon.nova-pro-v1:0",
            AmazonNovaPremier to "us.amazon.nova-premier-v1:0",

            // AI21 Jamba models
            AI21JambaLarge to "us.ai21.jamba-1-5-large-v1:0",
            AI21JambaMini to "us.ai21.jamba-1-5-mini-v1:0",

            // Meta Llama models
            MetaLlama3_0_8BInstruct to "us.meta.llama3-8b-instruct-v1:0",
            MetaLlama3_0_70BInstruct to "us.meta.llama3-70b-instruct-v1:0",
            MetaLlama3_1_8BInstruct to "us.meta.llama3-1-8b-instruct-v1:0",
            MetaLlama3_1_70BInstruct to "us.meta.llama3-1-70b-instruct-v1:0",
            MetaLlama3_1_405BInstruct to "us.meta.llama3-1-405b-instruct-v1:0",
            MetaLlama3_2_1BInstruct to "us.meta.llama3-2-1b-instruct-v1:0",
            MetaLlama3_2_3BInstruct to "us.meta.llama3-2-3b-instruct-v1:0",
            MetaLlama3_2_11BInstruct to "us.meta.llama3-2-11b-instruct-v1:0",
            MetaLlama3_2_90BInstruct to "us.meta.llama3-2-90b-instruct-v1:0",
            MetaLlama3_3_70BInstruct to "us.meta.llama3-3-70b-instruct-v1:0",
        )
    )
}