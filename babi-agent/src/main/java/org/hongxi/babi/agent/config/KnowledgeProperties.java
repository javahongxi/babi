package org.hongxi.babi.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the local knowledge base (RAG).
 *
 * @param enabled        whether the knowledge tools are registered (also requires an API key)
 * @param embeddingModel DashScope text embedding model name
 * @param dimensions     embedding vector dimensions (must match the model output)
 * @param topK           default number of chunks returned by knowledge_search
 * @param storeDir       directory holding the persisted vector store (store.json)
 */
@ConfigurationProperties(KnowledgeProperties.PREFIX)
public record KnowledgeProperties(
        Boolean enabled,
        String embeddingModel,
        Integer dimensions,
        Integer topK,
        String storeDir
) {
    static final String PREFIX = "babi.agent.knowledge";

    public KnowledgeProperties {
        if (enabled == null) enabled = true;
        if (embeddingModel == null || embeddingModel.isBlank()) embeddingModel = "text-embedding-v4";
        if (dimensions == null || dimensions <= 0) dimensions = 1024;
        if (topK == null || topK <= 0) topK = 5;
        if (storeDir == null || storeDir.isBlank()) storeDir = "~/.babi/knowledge";
    }
}