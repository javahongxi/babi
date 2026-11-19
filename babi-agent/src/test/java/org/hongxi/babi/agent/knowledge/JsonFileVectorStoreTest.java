package org.hongxi.babi.agent.knowledge;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.store.dto.SearchDocumentDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link JsonFileVectorStore} — pure local, no embedding API calls.
 */
// Rag model classes are @Deprecated(forRemoval) but still required by the 2.0.4 SPI.
@SuppressWarnings({"deprecation", "removal"})
class JsonFileVectorStoreTest {

    private static final int DIMS = 3;

    @TempDir
    Path tempDir;

    private static Document doc(String docId, String chunkId, String text,
                                String source, double[] vector) {
        DocumentMetadata md = DocumentMetadata.builder()
                .content(TextBlock.builder().text(text).build())
                .docId(docId)
                .chunkId(chunkId)
                .payload(Map.of(JsonFileVectorStore.SOURCE_KEY, source))
                .build();
        Document doc = new Document(md);
        doc.setEmbedding(vector);
        return doc;
    }

    private static SearchDocumentDto query(double[] vector, int limit) {
        return SearchDocumentDto.builder()
                .queryEmbedding(vector)
                .limit(limit)
                .build();
    }

    @Test
    void searchRanksByCosineSimilarity() {
        JsonFileVectorStore store = new JsonFileVectorStore(tempDir, DIMS);
        Document a = doc("d1", "0", "alpha", "/tmp/a.md", new double[]{1, 0, 0});
        Document b = doc("d2", "0", "beta", "/tmp/b.md", new double[]{0, 1, 0});
        store.add(List.of(a, b)).block();

        List<Document> results = store.search(query(new double[]{0.9, 0.1, 0}, 2)).block();
        assertEquals(2, results.size());
        assertEquals("alpha", results.get(0).getMetadata().getContentText());
        assertEquals("/tmp/a.md",
                results.get(0).getMetadata().getPayloadValue(JsonFileVectorStore.SOURCE_KEY));
        assertTrue(results.get(0).getScore() > results.get(1).getScore());
    }

    @Test
    void searchRespectsLimit() {
        JsonFileVectorStore store = new JsonFileVectorStore(tempDir, DIMS);
        store.add(List.of(
                doc("d1", "0", "one", "/tmp/a.md", new double[]{1, 0, 0}),
                doc("d2", "0", "two", "/tmp/a.md", new double[]{0.8, 0.2, 0}),
                doc("d3", "0", "three", "/tmp/b.md", new double[]{0, 0, 1}))).block();

        List<Document> results = store.search(query(new double[]{1, 0, 0}, 2)).block();
        assertEquals(2, results.size());
    }

    @Test
    void persistsAcrossRestart() {
        JsonFileVectorStore store = new JsonFileVectorStore(tempDir, DIMS);
        store.add(List.of(doc("d1", "0", "persisted text", "/tmp/a.md", new double[]{1, 0, 0}))).block();

        // Simulate restart: a fresh instance loads store.json from disk
        JsonFileVectorStore reloaded = new JsonFileVectorStore(tempDir, DIMS);
        assertEquals(1, reloaded.size());
        List<Document> results = reloaded.search(query(new double[]{1, 0, 0}, 1)).block();
        assertEquals("persisted text", results.get(0).getMetadata().getContentText());
        assertEquals("/tmp/a.md",
                results.get(0).getMetadata().getPayloadValue(JsonFileVectorStore.SOURCE_KEY));
    }

    @Test
    void deleteByIdAndBySource() throws Exception {
        JsonFileVectorStore store = new JsonFileVectorStore(tempDir, DIMS);
        Document a = doc("d1", "0", "alpha", "/tmp/a.md", new double[]{1, 0, 0});
        store.add(List.of(
                a,
                doc("d2", "0", "beta", "/tmp/b.md", new double[]{0, 1, 0}),
                doc("d2", "1", "beta2", "/tmp/b.md", new double[]{0, 0.9, 0.1}))).block();

        assertTrue(store.delete(a.getId()).block());
        assertFalse(store.delete(a.getId()).block());
        assertEquals(2, store.size());

        assertEquals(2, store.deleteBySource("/tmp/b.md"));
        assertEquals(0, store.size());
        assertTrue(store.listSources().isEmpty());

        // Deletions are persisted too
        assertEquals(0, new JsonFileVectorStore(tempDir, DIMS).size());
    }

    @Test
    void listSourcesAggregatesChunkCounts() {
        JsonFileVectorStore store = new JsonFileVectorStore(tempDir, DIMS);
        store.add(List.of(
                doc("d1", "0", "a0", "/tmp/a.md", new double[]{1, 0, 0}),
                doc("d1", "1", "a1", "/tmp/a.md", new double[]{0.9, 0.1, 0}),
                doc("d2", "0", "b0", "/tmp/b.md", new double[]{0, 1, 0}))).block();

        Map<String, Long> sources = store.listSources();
        assertEquals(2L, sources.get("/tmp/a.md"));
        assertEquals(1L, sources.get("/tmp/b.md"));
    }

    @Test
    void rejectsDimensionMismatch() {
        JsonFileVectorStore store = new JsonFileVectorStore(tempDir, DIMS);
        assertThrows(IllegalArgumentException.class, () ->
                store.add(List.of(doc("d1", "0", "bad", "/tmp/a.md", new double[]{1, 0}))).block());
        assertThrows(IllegalArgumentException.class, () ->
                store.search(query(new double[]{1, 0}, 1)).block());
    }
}