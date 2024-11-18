package com.gaplo917.demo.tools

import com.fasterxml.jackson.annotation.JsonCreator
import com.gaplo917.demo.agents.GraphAgentService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.model.ToolContext
import org.springframework.ai.model.function.FunctionCallback
import org.springframework.ai.model.function.FunctionCallbackWrapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.function.BiFunction

enum class KnowledgeType {
    USER_DATA,
    USER_FINANCIAL_ACTIVITY,
    USER_PORTFOLIO,
    USER_JOKE_PREFERENCE,
    NEWS,
    WEATHER_FORECAST,
    UNKNOWN;

    companion object {
        val kts = KnowledgeType.entries.associateBy { it.name }

        @JvmStatic
        @JsonCreator
        fun from(value: String) = kts.getOrDefault(value, UNKNOWN)
    }
}

data class GetExternalKnowledgeToolRequest(
    val originalQuestion: String,
    val knowledgeType: KnowledgeType
)

data class GetExternalKnowledgeToolResponse(
    val knowledge: String
)

const val GET_EXTERNAL_KNOWLEDGE_TOOL_NAME = "getExternalKnowledge"

@Configuration
class GetUserDataToolConfig {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    @Bean(value = [GET_EXTERNAL_KNOWLEDGE_TOOL_NAME])
    fun getUserDataTool(graphAgentService: GraphAgentService): FunctionCallback {

        return FunctionCallbackWrapper.builder(BiFunction<GetExternalKnowledgeToolRequest, ToolContext, GetExternalKnowledgeToolResponse> { req, toolContext ->
            logger.info("[TOOL001]knowledgeType:{}, originalQuestion:{}", req.knowledgeType, req.originalQuestion)
            GetExternalKnowledgeToolResponse(
                knowledge = when (req.knowledgeType) {
                    KnowledgeType.USER_DATA, KnowledgeType.USER_FINANCIAL_ACTIVITY, KnowledgeType.USER_PORTFOLIO -> {
                        runBlocking(Dispatchers.IO) {
                            val data = graphAgentService.thinkOnGraph(
                                toolContext.context["userId"] as? String ?: "",
                                req.originalQuestion
                            )
                            """Here are your user data in knowledge graph format(node{...}->relationship{...}->node{...}):${
                                data.joinToString("\n")
                            }
                            """.trimMargin()
                        }
                    }

                    KnowledgeType.USER_JOKE_PREFERENCE -> """
                        Here is your user joke preference:
                        John really likes jokes about cats. He likes British Shorthair.
                        """.trimIndent()
                    KnowledgeType.NEWS -> """
                        Here is the latest news:
                        - Bitcoin is 100k USD now!
                        - Elon Musk is going to buy Twitter
                        """.trimIndent()
                    KnowledgeType.UNKNOWN -> ""
                    KnowledgeType.WEATHER_FORECAST -> """[
                        {
                            "date": "2024-11-24",
                            "temperature": "20°C",
                            "description": "Sunny"
                        },
                        {
                            "date": "2024-11-25",
                            "temperature": "18°C",
                            "description": "Cloudy"
                        },
                        {
                            "date": "2024-11-26",
                            "temperature": "22°C",
                            "description": "Rainy"
                        },
                        {
                            "date": "2024-11-27",
                            "temperature": "25°C",
                            "description": "Sunny"
                        },
                        {
                            "date": "2024-11-28",
                            "temperature": "19°C",
                            "description": "Cloudy"
                        },
                        {
                            "date": "2024-11-29",
                            "temperature": "21°C",
                            "description": "Sunny"
                        },
                        {
                            "date": "2024-11-30",
                            "temperature": "23°C",
                            "description": "Sunny"
                        }
                    ]""".trimIndent()
                }
            )
        }).withName(GET_EXTERNAL_KNOWLEDGE_TOOL_NAME)
            .withDescription("""Get external knowledge""".trimIndent())
            .withInputType(GetExternalKnowledgeToolRequest::class.java)
            .build()
    }
}
