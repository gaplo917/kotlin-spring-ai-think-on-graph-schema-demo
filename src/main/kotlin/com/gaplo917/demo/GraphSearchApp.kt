package com.gaplo917.demo

import com.gaplo917.demo.agents.AssistantService
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
    private val assistantService: AssistantService,
) {

    // TODO: it can be captured by session or jwt token.
    val userId = "USER123"

    @PostMapping("/chat")
    fun generate(
        @RequestBody body: ChatBody
    ): String {
        return assistantService.chat(
            userId = userId,
            prompt = body.prompt ?: TODO("throw bad request")
        )
    }

    @PostMapping("/chatWithMemory")
    fun chatMemory(
        @RequestBody body: ChatBody
    ): String {
        return assistantService.chat(
            userId = userId,
            prompt = body.prompt ?: TODO("throw bad request"),
            withMemory = true
        )
    }

}
