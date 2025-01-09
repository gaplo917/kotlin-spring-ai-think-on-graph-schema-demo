package com.gaplo917.demo.models

import org.neo4j.driver.types.Entity
import org.neo4j.driver.types.Node
import org.neo4j.driver.types.Relationship

/**
 * Represents a path in the knowledge graph, consisting of a list of entities (nodes and relationships)
 * and a set of properties. This class is used to hold database returned data and transform to LLM-friendly
 * context later.
 */
data class KnowledgePath(val entities: List<Entity>, val properties: Set<String>) {
    private fun Node.toLLMContext(properties: Set<String>): String {
        val props = asMap().filter { properties.contains(it.key) }
        return "${labels().joinToString(",")}${if(props.isEmpty()) "" else "($props)"}"
    }
    private fun Relationship.toLLMContext(properties: Set<String>): String {
        val props = asMap().filter { properties.contains(it.key) }
        return "${type()}${if(props.isEmpty()) "" else "($props)"}"
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
