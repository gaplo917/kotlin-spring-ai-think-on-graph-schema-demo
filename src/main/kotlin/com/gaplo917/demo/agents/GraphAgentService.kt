package com.gaplo917.demo.agents

import com.gaplo917.demo.interfaces.LLMOutputExtraction
import kotlinx.coroutines.flow.toList
import org.neo4j.driver.types.Node
import org.neo4j.driver.types.Relationship
import org.neo4j.driver.types.Entity
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.data.neo4j.core.ReactiveNeo4jClient
import org.springframework.data.neo4j.core.fetchAll
import org.springframework.data.neo4j.core.mappedBy
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

data class KnowledgePath(val entities: List<Entity>, val properties: Set<String>) {
    private fun Node.toLLMContext(properties: Set<String>): String {
        return "${labels().joinToString(",")}(${asMap().filter { properties.contains(it.key) }}"
    }
    private fun Relationship.toLLMContext(properties: Set<String>): String {
        return "${type()}(${asMap().filter { properties.contains(it.key) }}"
    }
    fun toLLMContext(): String {
        return entities.joinToString("->") { entity ->
            when (entity) {
                is Node -> entity.toLLMContext(properties)
                is Relationship -> entity.toLLMContext(properties)
                else -> ""
            }
        }
    }
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

        // TODO: validate the query using AST and rebuild it using query builder
        val userQuery = query.replace("USER_ID_PLACEHOLDER", userId)

        val properties = content.extractXmlTag("properties")
            ?.split(",")
            ?.map { it.trim() }
            ?.toSet()
            ?: setOf()

        logger.info("[DEMO_GRAPH_AGENT_002]generated user query: {}, properties: {}", userQuery, properties)

        return graphClient.query(userQuery).mappedBy { _, record ->
            val entities = mutableListOf<Entity>()
            var lastVisitNode: Node? = null
            for (segment in record.get(0).asPath()) {
                if(lastVisitNode?.elementId() != segment.start().elementId()) {
                    entities.add(segment.start())
                }
                entities.add(segment.relationship())
                entities.add(segment.end())
                lastVisitNode = segment.end()
            }
            KnowledgePath(entities, properties)
        }
            .fetchAll()
            .toList()
            .distinct()
            .also { logger.info("[DEMO_GRAPH_AGENT_003]queried graph data from database: {}", it) }
    }

}
