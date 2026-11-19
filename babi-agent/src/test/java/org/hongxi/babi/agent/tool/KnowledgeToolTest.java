package org.hongxi.babi.agent.tool;

import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.store.dto.SearchDocumentDto;
import org.hongxi.babi.agent.knowledge.JsonFileVectorStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for {@link KnowledgeTool} ingestion.
 *
 * <p>{@code ReaderInput} is interpreted differently by the rag-simple readers: {@code PDFReader}
 * and {@code WordReader} treat {@code asString()} as a path and open the file, while
 * {@code TextReader} treats it as the document body. Passing {@code ReaderInput.fromPath(..)} to
 * {@code TextReader} therefore embedded the literal path string and threw the real content away,
 * so every {@code .txt}/{@code .md}/{@code .rst} file produced exactly one useless chunk whose
 * text was the filename.
 *
 * <p>Runs fully offline: a stub {@link EmbeddingModel} derives a deterministic vector from the
 * chunk text, so an embedding is a pure function of the body. That lets these tests assert the
 * ingested <em>text</em> really came from the file, not just that the call returned.
 */
// Rag model classes are @Deprecated(forRemoval) but still required by SimpleKnowledge in 2.0.4.
@SuppressWarnings({"deprecation", "removal"})
class KnowledgeToolTest {

    private static final int DIMS = 16;

    @TempDir
    Path tempDir;

    /** Hashes text into a fixed-dimension bag-of-characters vector. */
    private static final class HashEmbedding implements EmbeddingModel {

        private final AtomicLong calls = new AtomicLong();

        @Override
        public Mono<double[]> embed(ContentBlock block) {
            calls.incrementAndGet();
            String text = block instanceof TextBlock tb && tb.getText() != null
                    ? tb.getText() : String.valueOf(block);
            return Mono.just(hash(text));
        }

        @Override
        public String getModelName() {
            return "hash-stub";
        }

        @Override
        public int getDimensions() {
            return DIMS;
        }

        long callCount() {
            return calls.get();
        }
    }

    /** Shared by the stub and the query builder so a matching query scores ~1.0. */
    private static double[] hash(String text) {
        double[] vector = new double[DIMS];
        for (int i = 0; i < text.length(); i++) {
            vector[Math.floorMod(text.charAt(i) * 31 + i, DIMS)] += 1.0;
        }
        return vector;
    }

    private record Harness(KnowledgeTool tool, JsonFileVectorStore store, HashEmbedding embedder) {}

    private Harness harness() {
        JsonFileVectorStore store = new JsonFileVectorStore(tempDir, DIMS);
        HashEmbedding embedder = new HashEmbedding();
        SimpleKnowledge knowledge = SimpleKnowledge.builder()
                .embeddingModel(embedder)
                .embeddingStore(store)
                .build();
        return new Harness(new KnowledgeTool(knowledge, store, 5), store, embedder);
    }

    private static SearchDocumentDto queryFor(String text, int limit) {
        return SearchDocumentDto.builder()
                .queryEmbedding(hash(text))
                .limit(limit)
                .build();
    }

    private static String storedText(Document doc) {
        return doc.getMetadata().getContentText();
    }

    /** The stored text must be the document body, never the file path. */
    @Test
    void markdownIngestsRealTextNotThePath() throws IOException {
        Path file = tempDir.resolve("notes.md");
        Files.writeString(file, "# Deployment Runbook\n\nRestart the gateway before draining pods.\n");

        Harness h = harness();
        String result = h.tool().knowledgeAdd(file.toString());
        assertTrue(result.startsWith("Added "), result);

        List<Document> hits = h.store().search(queryFor(
                "Restart the gateway before draining pods.", 3)).block();
        assertFalse(hits.isEmpty(), "the chunk body should be retrievable");

        String best = storedText(hits.get(0));
        assertTrue(best.contains("Restart the gateway"), "stored text was: " + best);
        assertFalse(best.contains("notes.md"),
                "regression: the path was embedded instead of the content: " + best);
    }

    /** Plain .txt goes through the same TextReader and had the same bug. */
    @Test
    void plainTextIngestsRealText() throws IOException {
        Path file = tempDir.resolve("plain.txt");
        Files.writeString(file, "Quarterly figures: revenue grew twelve percent.\n");

        Harness h = harness();
        h.tool().knowledgeAdd(file.toString());

        List<Document> hits = h.store().search(queryFor(
                "Quarterly figures: revenue grew twelve percent.", 3)).block();
        assertEquals(1, hits.size());
        assertTrue(storedText(hits.get(0)).contains("twelve percent"),
                "stored text was: " + storedText(hits.get(0)));
    }

    /**
     * A long document must be split into several chunks. Before the fix the count was always 1 per
     * file regardless of size, which is how the bug first surfaced.
     */
    @Test
    void longDocumentIsChunked() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 400; i++) {
            sb.append("Section ").append(i)
                    .append(": the quick brown fox jumps over the lazy dog number ").append(i)
                    .append(".\n\n");
        }
        Path file = tempDir.resolve("big.md");
        Files.writeString(file, sb.toString());

        Harness h = harness();
        String result = h.tool().knowledgeAdd(file.toString());
        int chunks = parseChunkCount(result);
        assertTrue(chunks > 1, "expected the long document to be split, got " + chunks
                + " chunk(s): " + result);

        // One embedding call per chunk: a single path-derived chunk meant exactly 1 call.
        assertEquals(chunks, h.embedder().callCount(),
                "every chunk's body should have been embedded");
    }

    /** knowledge_search must surface chunk bodies so the agent can actually read them. */
    @Test
    void searchReturnsChunkBody() throws IOException {
        Path file = tempDir.resolve("arch.md");
        Files.writeString(file,
                "The cache eviction policy is window-tiny-lru with a 200ms latency budget.\n");

        Harness h = harness();
        h.tool().knowledgeAdd(file.toString());

        String out = h.tool().knowledgeSearch(
                "The cache eviction policy is window-tiny-lru with a 200ms latency budget.");
        assertTrue(out.contains("window-tiny-lru"), out);
        assertTrue(out.contains("latency budget"), out);
    }

    /** Re-adding refreshes a source: no duplicates, and the new content wins. */
    @Test
    void reAddRefreshesWithoutDuplicates() throws IOException {
        Path file = tempDir.resolve("dup.md");
        Files.writeString(file, "Original sentence about alpha budgeting.\n");

        Harness h = harness();
        h.tool().knowledgeAdd(file.toString());
        int first = h.store().size();

        Files.writeString(file, "Rewritten sentence about beta scheduling.\n");
        h.tool().knowledgeAdd(file.toString());

        assertEquals(first, h.store().size(),
                "re-adding must replace chunks, not accumulate them");
        List<Document> hits = h.store().search(
                queryFor("Rewritten sentence about beta scheduling.", 3)).block();
        assertFalse(hits.isEmpty(), "the rewritten content should be retrievable");
        assertTrue(storedText(hits.get(0)).contains("beta scheduling"),
                "stored text was: " + storedText(hits.get(0)));
    }

    @Test
    void emptyKnowledgeBaseSearchIsFriendly() {
        Harness h = harness();
        assertTrue(h.tool().knowledgeSearch("anything").contains("empty"));
    }

    @Test
    void rejectsEmptyMissingAndUnsupportedInputs() throws IOException {
        Harness h = harness();
        assertTrue(h.tool().knowledgeAdd("").contains("empty"));
        assertTrue(h.tool().knowledgeAdd(tempDir.resolve("nope.iso").toString()).contains("not found"));

        Path png = tempDir.resolve("picture.png");
        Files.writeString(png, "not really a png");
        assertTrue(h.tool().knowledgeAdd(png.toString()).contains("unsupported"));

        assertEquals(0, h.store().size(), "no rejected input should persist chunks");
    }

    private static int parseChunkCount(String result) {
        // "Added N chunks from <path> to the knowledge base (M chunks total)."
        return Integer.parseInt(result.split(" ")[1]);
    }
}
