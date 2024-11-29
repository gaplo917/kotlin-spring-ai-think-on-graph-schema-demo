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
    USER_FINANCIAL_ACTIVITY,
    USER_PORTFOLIO,
    USER_TICKETS,
    UNKNOWN;

    companion object {
        private val kts = KnowledgeType.entries.associateBy { it.name }

        val llmDescription get() = """
             Available knowledgeType=[${KnowledgeType.entries.joinToString(",")}]
        """.trimIndent()

        @JvmStatic
        @JsonCreator
        fun from(value: String) = kts.getOrDefault(value, UNKNOWN)
    }
}

data class GetUserDataRequest(
    val originalQuestion: String,
    val knowledgeType: KnowledgeType,
    val extraArguments: Map<String, String>? = null
)

data class GetUserDataResponse(
    val knowledge: String
)

const val GET_USER_DATA_TOOL = "getUserData"

@Configuration
class GetUserDataToolConfig {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    @Bean(value = [GET_USER_DATA_TOOL])
    fun getUserDataTool(
        graphAgentService: GraphAgentService,
    ): FunctionCallback {

        return FunctionCallbackWrapper.builder(BiFunction<GetUserDataRequest, ToolContext, GetUserDataResponse> { req, toolContext ->
            logger.info("[DEMO_USER_DATA_001]knowledgeType:{}, originalQuestion:{}", req.knowledgeType, req.originalQuestion)
            GetUserDataResponse(
                knowledge = when (req.knowledgeType) {
                    KnowledgeType.USER_TICKETS,
                    KnowledgeType.USER_FINANCIAL_ACTIVITY,
                    KnowledgeType.USER_PORTFOLIO -> {
                        runBlocking(Dispatchers.IO) {
                            val kpList = graphAgentService.thinkOnGraph(
                                toolContext.context["userId"] as? String ?: "",
                                req.originalQuestion
                            )
                            """Here are your user data in knowledge graph format(node{...}->relationship{...}->node{...}):${
                                kpList.joinToString("\n") { it.toLLMContext() }
                            }
                            """.trimMargin()
                        }
                    }
                    KnowledgeType.UNKNOWN -> ""
                }
            )
        }).withName(GET_USER_DATA_TOOL)
            .withDescription("""Get user data from database.${KnowledgeType.llmDescription}.""".trimIndent())
            .withInputType(GetUserDataRequest::class.java)
            .build()
    }
}
