USER_DATA_PROMPT_TEMPLATE = """
Here's the graph schema:
\<graph-schema>
{graphSchema}
</graph-schema>

<query-template>
```cypher
{queryTemplate}
```
</query-template>

<properties-template>
properties1,properties2,...
</properties-template>

Use the <query-template> to create single openCypher query to search for context of the user question.
- Do not change the USER_ID_PLACEHOLDER.
- Update the WHERE clause if needed.
- ALWAYS return the path instead of node.

Use the <properties-template> to create a list of property that is useful to answer the user question.
- ALWAYS ignore `id` field that designed for machine processing


Think step by step carefully of which relationships and properties are needed on ALL depths with the follow format:

user question: the question you are going to solve

<thinking>
Your thinking
<thinking>

<query>
```cypher
Your output query
```
</query>

<properties>
all properties are related to user question, comma separated, directly output without preamble
</properties>

---
user question: "{userQuestion}"
