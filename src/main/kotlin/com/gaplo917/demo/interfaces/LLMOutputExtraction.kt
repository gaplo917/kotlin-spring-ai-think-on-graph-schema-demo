package com.gaplo917.demo.interfaces

import org.springframework.ai.chat.memory.ChatMemory

interface LLMOutputExtraction {
    fun String.extractXmlTag(tag: String): String? {
        val re = """<$tag>([\s\S]*?)</$tag>""".toRegex()
        return re.find(this)?.groupValues?.get(1)?.trim()
    }

    fun String.extractXmlTagAndMDCodeCypher(tag: String): String? {
        val re = """<$tag>\s*```cypher\s*([\s\S]*?)```\s*</$tag>""".toRegex()
        return re.find(this)?.groupValues?.get(1)?.trim()
    }

    fun ChatMemory.toConversationHistory(
        userId: String,
        chatHistoryWindowSize: Int
    ): String {
        return this.get(userId, chatHistoryWindowSize)
            .joinToString("\n") { "${it.messageType}: ${it.content}".trimIndent() }
    }
}
