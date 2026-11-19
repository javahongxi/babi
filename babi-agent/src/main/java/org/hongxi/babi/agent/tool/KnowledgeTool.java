package org.hongxi.babi.agent.tool;

import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.rag.reader.PDFReader;
import io.agentscope.core.rag.reader.Reader;
import io.agentscope.core.rag.reader.ReaderInput;
import io.agentscope.core.rag.reader.TextReader;
import io.agentscope.core.rag.reader.WordReader;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.hongxi.babi.agent.knowledge.JsonFileVectorStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Conversation-driven local knowledge base tools.
 *
 * <p>Documents are chunked by the rag-simple readers, embedded via DashScope
 * (BYOK direct call, same boundary as chat), and persisted locally by
 * {@link JsonFileVectorStore}. Retrieval is semantic search over ingested
 * chunks — for files not yet ingested use glob/read tools instead.
 */
public class KnowledgeTool {

    private final SimpleKnowledge knowledge;
    private final JsonFileVectorStore store;
    private final int topK;

    public KnowledgeTool(SimpleKnowledge knowledge, JsonFileVectorStore store, int topK) {
        this.knowledge = knowledge;
        this.store = store;
        this.topK = topK;
    }

    @Tool(
            name = "knowledge_add",
            description = "Ingest a document into the local knowledge base for later semantic search. "
                    + "Supported formats: pdf, docx, txt, md, rst. The file is chunked, embedded, "
                    + "and persisted locally. Re-adding the same file refreshes its chunks.",
            readOnly = false)
    public String knowledgeAdd(
            @ToolParam(name = "path", description = "Path to the document (absolute or ~ supported)") String path) {
        if (path == null || path.isBlank()) {
            return "Error: path cannot be empty";
        }
        Path file = expand(path);
        if (!Files.isRegularFile(file)) {
            return "Error: file not found: " + file;
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        Reader reader = readerFor(name);
        if (reader == null) {
            return "Error: unsupported format '" + name
                    + "'. Supported: pdf, docx, txt, md, rst (legacy .doc must be converted to .docx first)";
        }
        try {
            List<Document> docs = reader.read(ReaderInput.fromPath(file)).block();
            if (docs == null || docs.isEmpty()) {
                return "No extractable text found in: " + file;
            }
            String source = file.toString();
            List<Document> withSource = new ArrayList<>(docs.size());
            for (Document doc : docs) {
                DocumentMetadata md = doc.getMetadata();
                DocumentMetadata tagged = DocumentMetadata.builder()
                        .content(md.getContent())
                        .docId(md.getDocId())
                        .chunkId(md.getChunkId())
                        .payload(Map.of(JsonFileVectorStore.SOURCE_KEY, source))
                        .build();
                withSource.add(new Document(tagged));
            }
            // Readers assign a fresh random docId per read, so drop the previous
            // chunks of this source first to make re-adding a true refresh.
            store.deleteBySource(source);
            knowledge.addDocuments(withSource).block();
            return "Added " + withSource.size() + " chunks from " + source
                    + " to the knowledge base (" + store.size() + " chunks total).";
        } catch (Exception e) {
            return "Error ingesting " + file + ": " + e.getMessage();
        }
    }

    @Tool(
            name = "knowledge_search",
            description = "Semantic search over the local knowledge base (previously ingested documents). "
                    + "Returns the most relevant text chunks with their source files and similarity scores. "
                    + "Use knowledge_list to see what is ingested; use glob/read for files not in the knowledge base.",
            readOnly = true)
    public String knowledgeSearch(
            @ToolParam(name = "query", description = "Natural-language query describing what to find") String query) {
        if (query == null || query.isBlank()) {
            return "Error: query cannot be empty";
        }
        if (store.size() == 0) {
            return "Knowledge base is empty. Use knowledge_add to ingest documents first.";
        }
        try {
            List<Document> results = knowledge.retrieve(query,
                    RetrieveConfig.builder().limit(topK).scoreThreshold(0.0).build()).block();
            if (results == null || results.isEmpty()) {
                return "No relevant chunks found for: " + query;
            }
            StringBuilder sb = new StringBuilder();
            int i = 1;
            for (Document doc : results) {
                Object source = doc.getMetadata().getPayloadValue(JsonFileVectorStore.SOURCE_KEY);
                sb.append("[").append(i++).append("] ")
                        .append(source != null ? source : "unknown")
                        .append(String.format(" (score %.3f)%n", doc.getScore()))
                        .append(doc.getMetadata().getContentText().strip())
                        .append("\n\n");
            }
            return sb.toString().strip();
        } catch (Exception e) {
            return "Error searching knowledge base: " + e.getMessage();
        }
    }

    @Tool(
            name = "knowledge_list",
            description = "List all documents currently ingested in the local knowledge base, "
                    + "with their source paths and chunk counts.",
            readOnly = true)
    public String knowledgeList() {
        Map<String, Long> sources = store.listSources();
        if (sources.isEmpty()) {
            return "Knowledge base is empty. Use knowledge_add to ingest documents.";
        }
        StringBuilder sb = new StringBuilder();
        sources.forEach((source, count) ->
                sb.append("- ").append(source).append(" (").append(count).append(" chunks)\n"));
        return sb.toString().strip();
    }

    @Tool(
            name = "knowledge_remove",
            description = "Remove a document (all its chunks) from the local knowledge base by its source path. "
                    + "Use knowledge_list to get the exact source paths.",
            readOnly = false)
    public String knowledgeRemove(
            @ToolParam(name = "source", description = "Source file path as shown by knowledge_list") String source) {
        if (source == null || source.isBlank()) {
            return "Error: source cannot be empty";
        }
        try {
            int removed = store.deleteBySource(source);
            if (removed == 0) {
                return "No chunks found for source: " + source + ". Use knowledge_list to see exact paths.";
            }
            return "Removed " + removed + " chunks of " + source
                    + " (" + store.size() + " chunks remaining).";
        } catch (Exception e) {
            return "Error removing " + source + ": " + e.getMessage();
        }
    }

    /** Selects a reader by file extension; null means unsupported. */
    private static Reader readerFor(String lowerName) {
        if (lowerName.endsWith(".pdf")) {
            return new PDFReader();
        }
        if (lowerName.endsWith(".docx")) {
            return new WordReader();
        }
        if (lowerName.endsWith(".txt") || lowerName.endsWith(".md") || lowerName.endsWith(".rst")) {
            return new TextReader();
        }
        return null;
    }

    /** Expands a leading ~ and converts to an absolute normalized path. */
    private static Path expand(String path) {
        String expanded = path.startsWith("~")
                ? System.getProperty("user.home") + path.substring(1)
                : path;
        return Path.of(expanded).toAbsolutePath().normalize();
    }
}