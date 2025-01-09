package com.gaplo917.demo.agents

import com.gaplo917.demo.advisors.MessageChatMemoryAdvisorKtFix
import com.gaplo917.demo.interfaces.LLMOutputExtraction
import com.gaplo917.demo.tools.GET_USER_DATA_TOOL
import com.gaplo917.demo.tools.GET_COIN_PRICE_LIST
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
import org.springframework.ai.chat.memory.InMemoryChatMemory
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.model.function.FunctionCallback
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service

interface AssistantService {
    fun chat(userId: String, prompt: String, withMemory: Boolean = false): String
}

@Service
class AssistantServiceImpl @Autowired constructor(
    @Qualifier("assistantModel") private val model: ChatModel,
    @Value("classpath:/prompts/assistant-react-prompt.st") private val assistantSReactPTRes: Resource,
    @Value("classpath:/prompts/assistant-system-prompt.st") private val assistantSPTRes: Resource,
    @Value("classpath:/prompts/assistant-user-prompt.st") private val assistantPTRes: Resource,
    @Qualifier(GET_USER_DATA_TOOL) private val getExternalKnowledgeTool: FunctionCallback,
    @Qualifier(GET_COIN_PRICE_LIST) private val getWeatherTool: FunctionCallback
) : AssistantService, LLMOutputExtraction {
    private val longLiveMemory = InMemoryChatMemory()
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val tools = listOf(getExternalKnowledgeTool, getWeatherTool)

    private val supervisorClient by lazy {
        ChatClient.builder(model)
            .defaultUser(assistantPTRes)
            .defaultSystem(assistantSPTRes)
            .defaultAdvisors(SimpleLoggerAdvisor())
            .defaultFunctions(*tools.toTypedArray())
            .build()
    }

    private val reactClient by lazy {
        ChatClient.builder(model)
            .defaultUser(assistantSReactPTRes)
            .defaultAdvisors(SimpleLoggerAdvisor())
            .build()
    }

    val toolsContext by lazy {
        "[" + tools.joinToString(",") {
            """
            {
                "type": "function",
                "function": {
                    "name": "${it.name}",
                    "description": "${it.description}",
                    "schema": ${it.inputTypeSchema}
                }
            }
            """.trimIndent()
        } + "]"
    }

    private val chatHistoryWindowSize = 10

    override fun chat(userId: String, prompt: String, withMemory: Boolean): String {
        val memory = if(withMemory) longLiveMemory else InMemoryChatMemory()
        val content = reactClient.prompt()
            .user { t ->
                t.params(
                    mapOf(
                        "prompt" to prompt,
                        "tools" to toolsContext,
                        "history" to if (withMemory) {
                            longLiveMemory.toConversationHistory(userId, chatHistoryWindowSize)
                        } else ""
                    )
                )
            }
            .call()
            .content() ?: ""

        logger.info("[DEMO_REACT_001] ReAct response: {}", content)

        val thinking = content.extractXmlTag("thinking")
        val rationale = content.extractXmlTag("rationale")
        val askUserInput = content.extractXmlTag("ask-user-input")?.lowercase() == "true"
        val followUpQuestion = content.extractXmlTag("follow-up-question")

        logger.info("[DEMO_REACT_002] thinking:{}, rationale: {}", thinking, rationale)

        if (askUserInput && followUpQuestion != null) {
            logger.info("[DEMO_REACT_003a] LLM determine to ask user input: {}", followUpQuestion)
            // conditionally store followup question to memory
            memory.add(userId, UserMessage(prompt))
            memory.add(userId, AssistantMessage(followUpQuestion))
            return memory.toConversationHistory(userId, chatHistoryWindowSize)
        } else {

            supervisorClient.prompt()
                .system { it.params(mapOf("prompt" to prompt, "rationale" to rationale, "thinking" to thinking)) }
                .user { it.params(mapOf("prompt" to prompt)) }
                .also {
                    it.advisors(MessageChatMemoryAdvisorKtFix(memory, userId, chatHistoryWindowSize))
                }
                .toolContext(mapOf("userId" to userId))
                .call()
                .content()?.also {
                    logger.info("[DEMO_REACT_003b] final response: {}", it)
                }

            return memory.toConversationHistory(userId, chatHistoryWindowSize)
        }
    }

}
