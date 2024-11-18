package com.gaplo917.demo.agents

import com.gaplo917.demo.tools.GET_EXTERNAL_KNOWLEDGE_TOOL_NAME
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
import org.springframework.ai.chat.memory.InMemoryChatMemory
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.model.function.FunctionCallback
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service

interface GeneralAssistantAgentService {
    fun chat(prompt: String, withMemory: Boolean = false): String
}

@Service
class GeneralAssistantAgentServiceImpl @Autowired constructor(
    @Qualifier("generalAssistantAgentModel") private val model: ChatModel,
    @Value("classpath:/prompts/assistant-react-prompt.st") private val assistantSReactPTRes: Resource,
    @Value("classpath:/prompts/assistant-system-prompt.st") private val assistantSPTRes: Resource,
    @Value("classpath:/prompts/assistant-user-prompt.st") private val assistantPTRes: Resource,
    @Qualifier(GET_EXTERNAL_KNOWLEDGE_TOOL_NAME) private val getExternalKnowledgeTool: FunctionCallback,

) : GeneralAssistantAgentService {
    private val memory = InMemoryChatMemory()
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val chatClientWithFunction by lazy {
        ChatClient.builder(model)
            .defaultUser(assistantPTRes)
            .defaultSystem(assistantSPTRes)
            .defaultAdvisors(SimpleLoggerAdvisor())
            .defaultFunctions(getExternalKnowledgeTool)
            .build()
    }

    private val reactClient by lazy {
        ChatClient.builder(model)
            .defaultUser(assistantSReactPTRes)
            .defaultAdvisors(SimpleLoggerAdvisor())
            .build()
    }

    override fun chat(prompt: String, withMemory: Boolean): String {
        val content = reactClient.prompt()
            .user { t ->
                t.params(mapOf(
                    "prompt" to prompt,
                    "tools" to """
                        {
                            "type": "function",
                            "function": {
                                "name": "${getExternalKnowledgeTool.name}",
                                "description": "${getExternalKnowledgeTool.description}",
                                "schema": ${getExternalKnowledgeTool.inputTypeSchema}
                            }
                        }
                    """.trimIndent()
                ))
            }
            .call()
            .content() ?: ""

        val rationaleTagRe = """<rationale>([\s\S]*?)</rationale>""".toRegex()
        val rationale = rationaleTagRe.find(content)?.groupValues?.get(1)?.trim() ?: ""

        logger.info("[AGENT01] rationale: {}", rationale)

        return chatClientWithFunction.prompt()
            .system {
                it.params(
                    mapOf(
                        "prompt" to prompt,
                        "rationale" to rationale
                    )
                )
            }
            .user {
                it.params(
                    mapOf(
                        "prompt" to prompt
                    )
                )
            }
            .also {
                if (withMemory) {
                    it.advisors(MessageChatMemoryAdvisor(memory, "user123", 10))
                }
            }
            // TODO: get it by session / jwt
            .toolContext(mapOf("userId" to "USER123"))
            .call()
            .content() ?: ""
    }

}
