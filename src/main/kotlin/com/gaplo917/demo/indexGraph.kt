package com.gaplo917.demo

import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.prompt.Prompt

fun indexGraph(chatModel: ChatModel, prompt: Prompt): ChatResponse {
    return chatModel.call(prompt)
}


fun abc() {}
