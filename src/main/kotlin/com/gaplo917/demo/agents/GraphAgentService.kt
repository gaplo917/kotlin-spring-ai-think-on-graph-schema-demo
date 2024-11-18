package com.gaplo917.demo.agents

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
    private fun Node.toString(properties: Set<String>): String {
        return "${labels().joinToString(",")}(${asMap().filter { properties.contains(it.key) }}"
    }
    private fun Relationship.toString(properties: Set<String>): String {
        return "${type()}(${asMap().filter { properties.contains(it.key) }}"
    }
    override fun toString(): String {
        return entities.joinToString("->") { entity ->
            when (entity) {
                is Node -> entity.toString(properties)
                is Relationship -> entity.toString(properties)
                else -> ""
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if(other is KnowledgePath) {
            return other.toString() == toString()
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return entities.hashCode()
    }
}

@Service
class GraphAgentServiceImpl @Autowired constructor(
    @Qualifier("graphAgentModel") private val model: ChatModel,
    @Value("classpath:/prompts/graph-agent-user-prompt.st") private val graphAgentPT: Resource,
    @Value("classpath:/prompts/mermaid_graph_schema.txt") private val graphSchemaResource: Resource,
    private val graphClient: ReactiveNeo4jClient,
) : GraphAgentService {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val graphSchema by lazy {
        graphSchemaResource.inputStream.readAllBytes().decodeToString()
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
                        "queryTemplate" to """
                            ```cypher
                            MATCH path=(u:User {userId: "USER_ID_PLACEHOLDER"})-[r1::{{relationships-at-depth1}}|:{{relationships-at-depth1}}]->(level1)-[r2:{{relationships-at-depth2}}|{{relationships-at-depth2}}]->(level2)
                            WHERE (level1.property IS NULL OR level1.timestamp >= datetime('2020-01-01T00:00:00'))
                            RETURN path
                            ```
                        """.trimIndent()
                    )
                )
            }.call()
            .content() ?: ""

        val queryTagRe = """<query>\s*```cypher\s*([\s\S]*?)```\s*</query>""".toRegex()
        val propertiesTagRe = """<properties>([\s\S]*?)</properties>""".toRegex()

        val query = queryTagRe.find(content)?.groupValues?.get(1)?.trim() ?: ""
        val userQuery = query.replace("USER_ID_PLACEHOLDER", userId)

        val propertiesStr = (propertiesTagRe.find(content)?.groupValues?.get(1)?.trim() ?: "")
        val properties = propertiesStr.split(",").map { it.trim() }.toSet()

        logger.debug("[GA01]generated user query: {}, properties: {}", userQuery, properties)

        return graphClient.query(userQuery).mappedBy { system, record ->
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
            .also { logger.debug("[GA02]graph data: {}", it) }
    }

}
