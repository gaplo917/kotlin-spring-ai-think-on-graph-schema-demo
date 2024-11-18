package com.gaplo917.demo

import com.gaplo917.demo.agents.GeneralAssistantAgentService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.bind.annotation.*

@SpringBootApplication
class GraphSearchApp

fun main(args: Array<String>) {
    runApplication<GraphSearchApp>(*args)
}

data class ChatBody(
    var prompt: String? = null
)

@RestController
class ChatController @Autowired constructor(
    private val generalAssistantAgentService: GeneralAssistantAgentService,
) {

    @PostMapping("/chat")
    fun generate(
        @RequestBody body: ChatBody
    ): String {
        return generalAssistantAgentService.chat(
            prompt = body.prompt ?: ""
        )
    }

    @GetMapping("/ai/chatWithMemory")
    fun chatMemory(
        @RequestParam(
            value = "message",
            defaultValue = ""
        ) message: String
    ): String {
        return generalAssistantAgentService.chat(
            prompt = message,
            withMemory = true
        )
    }

}
