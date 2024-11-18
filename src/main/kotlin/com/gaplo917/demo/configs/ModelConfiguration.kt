package com.gaplo917.demo.configs

import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.ollama.api.OllamaApi
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ModelConfiguration {
    @Bean(name = ["generalAssistantAgentModel"])
    fun generalAssistantAgent(
        ollamaApi: OllamaApi
    ): ChatModel {
        return OllamaChatModel.builder()
            .withOllamaApi(ollamaApi)
            .withDefaultOptions(
                OllamaOptions()
                    .withModel("gaplo917/gemma2-tools:9b")
                    .withNumCtx(8192)
                    .withTemperature(0.0)
            ).build()
    }

    @Bean(name = ["graphAgentModel"])
    fun graphAgentModel(
        ollamaApi: OllamaApi
    ): ChatModel {
        return OllamaChatModel.builder()
            .withOllamaApi(ollamaApi)
            .withDefaultOptions(
                OllamaOptions()
                    .withModel("gemma2")
                    .withNumCtx(8192)
                    .withTemperature(0.0)
            ).build()
    }
}
