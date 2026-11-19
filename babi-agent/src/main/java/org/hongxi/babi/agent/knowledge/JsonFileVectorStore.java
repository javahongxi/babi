package org.hongxi.babi.agent.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.store.VDBStoreBase;
import io.agentscope.core.rag.store.dto.SearchDocumentDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Local-file vector store persisting embedded chunks to {@code {storeDir}/store.json}.
 *
 * <p>Implements the rag-simple {@link VDBStoreBase} SPI so it plugs into
 * {@code SimpleKnowledge} like the built-in InMemoryStore, but survives restarts.
 * Chunks are kept in a flat DTO form (id/text/docId/chunkId/source/vector) and
 * reconstructed into {@link Document} instances on search. Writes go through a
 * temp file + atomic move so a crash never leaves a half-written store.
 */
public class JsonFileVectorStore implements VDBStoreBase {

    private static final Logger log = LoggerFactory.getLogger(JsonFileVectorStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Payload key holding the source file path of a chunk. */
    public static final String SOURCE_KEY = "source";

    /** Flat persisted form of one embedded chunk. */
    record StoredChunk(String id, String text, String docId, String chunkId,
                       String source, double[] vector) {}

    private final Path storeFile;
    private final int dimensions;
    private final Map<String, StoredChunk> chunks = new LinkedHashMap<>();

    public JsonFileVectorStore(Path storeDir, int dimensions) {
        this.storeFile = storeDir.resolve("store.json");
        this.dimensions = dimensions;
        load();
    }

    private synchronized void load() {
        if (!Files.exists(storeFile)) {
            return;
        }
        try {
            StoredChunk[] arr = MAPPER.readValue(storeFile.toFile(), StoredChunk[].class);
            for (StoredChunk c : arr) {
                chunks.put(c.id(), c);
            }
            log.info("Loaded {} knowledge chunks from {}", chunks.size(), storeFile);
        } catch (IOException e) {
            log.warn("Failed to load knowledge store {}: {}", storeFile, e.getMessage());
        }
    }

    @Override
    public Mono<Void> add(List<Document> documents) {
        return Mono.fromCallable(() -> {
            doAdd(documents);
            return null;
        });
    }

    private synchronized void doAdd(List<Document> documents) throws IOException {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        for (Document doc : documents) {
            if (doc == null || doc.getEmbedding() == null) {
                throw new IllegalArgumentException("Document must have embedding set");
            }
            validateDimensions(doc.getEmbedding());
            DocumentMetadata md = doc.getMetadata();
            Object source = md.getPayloadValue(SOURCE_KEY);
            chunks.put(doc.getId(), new StoredChunk(
                    doc.getId(),
                    md.getContentText(),
                    md.getDocId(),
                    md.getChunkId(),
                    source != null ? source.toString() : "",
                    doc.getEmbedding().clone()));
        }
        persist();
    }

    @Override
    public Mono<List<Document>> search(SearchDocumentDto searchDocumentDto) {
        return Mono.fromCallable(() -> doSearch(searchDocumentDto));
    }

    private synchronized List<Document> doSearch(SearchDocumentDto dto) {
        double[] query = dto.getQueryEmbedding();
        validateDimensions(query);
        if (dto.getLimit() <= 0) {
            throw new IllegalArgumentException("Limit must be positive");
        }
        List<Document> results = new ArrayList<>();
        for (StoredChunk c : chunks.values()) {
            double similarity = cosineSimilarity(query, c.vector());
            if (dto.getScoreThreshold() != null && similarity < dto.getScoreThreshold()) {
                continue;
            }
            DocumentMetadata md = DocumentMetadata.builder()
                    .content(TextBlock.builder().text(c.text()).build())
                    .docId(c.docId())
                    .chunkId(c.chunkId())
                    .payload(Map.of(SOURCE_KEY, c.source()))
                    .build();
            Document doc = new Document(md);
            doc.setEmbedding(c.vector());
            doc.setScore(similarity);
            results.add(doc);
        }
        results.sort(Comparator.comparing(Document::getScore, Comparator.reverseOrder()));
        return results.size() > dto.getLimit()
                ? new ArrayList<>(results.subList(0, dto.getLimit()))
                : results;
    }

    @Override
    public Mono<Boolean> delete(String id) {
        return Mono.fromCallable(() -> {
            synchronized (this) {
                if (chunks.remove(id) == null) {
                    return false;
                }
                persist();
                return true;
            }
        });
    }

    /** Lists ingested sources with their chunk counts (insertion order). */
    public synchronized Map<String, Long> listSources() {
        Map<String, Long> bySource = new LinkedHashMap<>();
        for (StoredChunk c : chunks.values()) {
            bySource.merge(c.source(), 1L, Long::sum);
        }
        return bySource;
    }

    /** Removes all chunks belonging to a source file; returns the removed count. */
    public synchronized int deleteBySource(String source) throws IOException {
        int before = chunks.size();
        chunks.values().removeIf(c -> c.source().equals(source));
        int removed = before - chunks.size();
        if (removed > 0) {
            persist();
        }
        return removed;
    }

    public synchronized int size() {
        return chunks.size();
    }

    private void validateDimensions(double[] vector) {
        if (vector == null || vector.length != dimensions) {
            throw new IllegalArgumentException(
                    "Embedding dimensions mismatch: expected " + dimensions
                            + ", got " + (vector == null ? "null" : vector.length));
        }
    }

    /** Cosine similarity in [-1, 1]; zero vectors yield 0. */
    private static double cosineSimilarity(double[] a, double[] b) {
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private void persist() throws IOException {
        Files.createDirectories(storeFile.getParent());
        Path tmp = storeFile.resolveSibling("store.json.tmp");
        MAPPER.writeValue(tmp.toFile(), chunks.values());
        try {
            Files.move(tmp, storeFile,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, storeFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}