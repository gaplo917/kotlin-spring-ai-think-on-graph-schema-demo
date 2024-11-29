package com.gaplo917.demo.advisors

import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisorChain
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.model.MessageAggregator
import org.springframework.ai.chat.prompt.PromptTemplate
import reactor.core.publisher.Flux

class MessageChatMemoryAdvisorKtFix : AbstractChatMemoryAdvisor<ChatMemory> {
    constructor(chatMemory: ChatMemory) : super(chatMemory)

    @JvmOverloads
    constructor(
        chatMemory: ChatMemory, defaultConversationId: String, chatHistoryWindowSize: Int,
        order: Int = DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER
    ) : super(
        chatMemory, defaultConversationId, chatHistoryWindowSize, true, order
    )

    override fun aroundCall(advisedRequest: AdvisedRequest, chain: CallAroundAdvisorChain): AdvisedResponse {
        var advisedRequest = advisedRequest
        advisedRequest = this.before(advisedRequest)

        val advisedResponse = chain.nextAroundCall(advisedRequest)

        this.observeAfter(advisedResponse)

        return advisedResponse
    }

    override fun aroundStream(advisedRequest: AdvisedRequest, chain: StreamAroundAdvisorChain): Flux<AdvisedResponse> {
        val advisedResponses = this.doNextWithProtectFromBlockingBefore(
            advisedRequest, chain
        ) { request: AdvisedRequest -> this.before(request) }

        return MessageAggregator().aggregateAdvisedResponse(
            advisedResponses
        ) { advisedResponse: AdvisedResponse -> this.observeAfter(advisedResponse) }
    }

    private fun before(request: AdvisedRequest): AdvisedRequest {
        val conversationId = this.doGetConversationId(request.adviseContext())

        val chatMemoryRetrieveSize = this.doGetChatMemoryRetrieveSize(request.adviseContext())

        // 1. Retrieve the chat memory for the current conversation.
        val memoryMessages = getChatMemoryStore()[conversationId, chatMemoryRetrieveSize]

        // 2. Advise the request messages list.
        val advisedMessages: MutableList<Message> = ArrayList(request.messages())
        advisedMessages.addAll(memoryMessages)

        // 3. Create a new request with the advised messages.
        val advisedRequest = AdvisedRequest.from(request).withMessages(advisedMessages).build()

        // 4. Add the new user input to the conversation memory.
        // TODO: report a github issue that original MessageChatMemoryAdvisor.java doesn't store rendered user message
        val userMessage =
            UserMessage(PromptTemplate(request.userText(), request.userParams()).render(), request.media())
        getChatMemoryStore().add(this.doGetConversationId(request.adviseContext()), userMessage)

        return advisedRequest
    }

    private fun observeAfter(advisedResponse: AdvisedResponse) {
        val assistantMessages = advisedResponse.response()
            ?.results
            ?.stream()
            ?.map { g: Generation -> g.output as Message }
            ?.toList()

        getChatMemoryStore().add(this.doGetConversationId(advisedResponse.adviseContext()), assistantMessages)
    }
}
