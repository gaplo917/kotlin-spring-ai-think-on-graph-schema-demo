package com.gaplo917.demo.extensions

import com.gaplo917.demo.models.KnowledgePath
import kotlinx.coroutines.flow.toList
import org.neo4j.driver.types.Entity
import org.neo4j.driver.types.Node
import org.springframework.data.neo4j.core.ReactiveNeo4jClient
import org.springframework.data.neo4j.core.fetchAll
import org.springframework.data.neo4j.core.mappedBy

/**
 * Query the database for custom `KnowledgePath` model
 * @param query the query to execute
 * @param selectors the selectors to use for the query
 * @return a list of knowledge paths
 */
suspend fun ReactiveNeo4jClient.queryKnowledgePaths(query: String, selectors: Set<String>): List<KnowledgePath> {
    return query(query).mappedBy { _, record ->
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
        KnowledgePath(entities, selectors)
    }
        .fetchAll()
        .toList()
        .distinct()
}
