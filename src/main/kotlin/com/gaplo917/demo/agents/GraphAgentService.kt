package com.gaplo917.demo.agents

import com.gaplo917.demo.extensions.queryKnowledgePaths
import com.gaplo917.demo.interfaces.LLMOutputExtraction
import com.gaplo917.demo.models.KnowledgePath
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.data.neo4j.core.ReactiveNeo4jClient
import org.springframework.stereotype.Service

interface GraphAgentService {

    /**
     * Think on graph, generate a cypher query to answer the question
     *
     * @param schema The schema of the graph
     * @param question The question to ask
     * @return The cypher query to answer the question
     */
    suspend fun thinkOnGraph(userId: String, question: String): List<KnowledgePath>

}

@Service
class GraphAgentServiceImpl @Autowired constructor(
    @Qualifier("graphAgentModel") private val model: ChatModel,
    @Value("classpath:/prompts/graph-agent-user-prompt.st") private val graphAgentPT: Resource,
    @Value("classpath:/prompts/mermaid-graph-schema.txt") private val graphSchemaResource: Resource,
    @Value("classpath:/prompts/cypher-query-template.txt") private val cypherQueryTemplateResource: Resource,
    private val graphClient: ReactiveNeo4jClient,
) : GraphAgentService, LLMOutputExtraction {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val graphSchema by lazy {
        graphSchemaResource.inputStream.readAllBytes().decodeToString()
    }

    private val cypherQueryTemplate by lazy {
        cypherQueryTemplateResource.inputStream.readAllBytes().decodeToString()
    }

    private val chatClient by lazy {
        ChatClient.builder(model)
            .defaultUser(graphAgentPT)
            .build()
    }

    override suspend fun thinkOnGraph(userId: String, question: String): List<KnowledgePath> {
        val content = chatClient.prompt()
            .user {
                it.params(
                    mapOf(
                        "userQuestion" to question,
                        "graphSchema" to graphSchema,
                        "queryTemplate" to cypherQueryTemplate
                    )
                )
            }.call()
            .content() ?: ""

        logger.info("[DEMO_GRAPH_AGENT_001]agent: {}", content)

        val query = content.extractXmlTagAndMDCodeCypher("query") ?: ""

        // TODO: validate the query using AST and rebuild query dynamically using query builder
        val queryWithUserId = query.replace("USER_ID_PLACEHOLDER", userId)

        val properties = content.extractXmlTag("properties")
            ?.split(",")
            ?.mapNotNull { it.split(".").takeLast(1).lastOrNull()?.toString()?.trim() }
            ?.toSet()
            ?: setOf()

        logger.info("[DEMO_GRAPH_AGENT_002]generated user query: {}, properties: {}", queryWithUserId, properties)

        return graphClient.queryKnowledgePaths(queryWithUserId, selectors = properties)
            .also { logger.info("[DEMO_GRAPH_AGENT_003]queried graph data from database: {}", it) }
    }

}
