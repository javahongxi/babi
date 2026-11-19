package org.hongxi.babi.agent.controller;

import org.hongxi.babi.agent.util.AgentUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Workspace file system API for the browser-based IDE.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/workspace/tree?path=} — list directory children</li>
 *   <li>{@code GET /api/workspace/file?path=} — read file content</li>
 *   <li>{@code GET /api/workspace/files?q=&limit=} — flat file search for @-mention completion</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    private final Path workspaceRoot;

    public WorkspaceController(
            @Value("${babi.agent.workspace:~/babi-workspace}") String workspace) {
        this.workspaceRoot = Path.of(AgentUtils.resolveWorkspace(workspace));
    }

    /**
     * List children of a directory (relative to workspace root).
     *
     * @param path relative path inside workspace (empty string = root)
     * @return list of {name, path, isDir}
     */
    @GetMapping("/tree")
    public Mono<List<Map<String, Object>>> listTree(
            @RequestParam(defaultValue = "") String path) {
        return Mono.fromCallable(() -> {
            Path dir = workspaceRoot.resolve(path).normalize();
            // Security: ensure path is within workspace
            if (!dir.startsWith(workspaceRoot)) {
                return List.of();
            }
            if (!Files.isDirectory(dir)) {
                return List.of();
            }
            List<Map<String, Object>> children = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path entry : stream) {
                    String name = entry.getFileName().toString();
                    // Skip hidden files/dirs
                    if (name.startsWith(".")) continue;
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", name);
                    item.put("path", workspaceRoot.relativize(entry).toString());
                    item.put("isDir", Files.isDirectory(entry));
                    item.put("size", Files.isRegularFile(entry) ? Files.size(entry) : 0);
                    children.add(item);
                }
            }
            // Sort: directories first, then by name
            children.sort((a, b) -> {
                boolean aDir = (boolean) a.get("isDir");
                boolean bDir = (boolean) b.get("isDir");
                if (aDir != bDir) return aDir ? -1 : 1;
                return ((String) a.get("name")).compareToIgnoreCase((String) b.get("name"));
            });
            return children;
        });
    }

    /** Directories never worth suggesting as @-attachments. */
    private static final Set<String> SKIP_DIRS =
            Set.of("node_modules", "target", "dist", "build", "__pycache__");

    /**
     * Flat file search for the frontend @-mention autocomplete.
     *
     * <p>Walks the workspace (hidden entries and build/vendor dirs skipped),
     * filters by case-insensitive substring on the relative path, and ranks
     * filename-prefix matches first.
     *
     * @param q     filter substring (empty = all files)
     * @param limit max results (capped at 50)
     * @return list of workspace-relative file paths
     */
    @GetMapping("/files")
    public Mono<List<String>> listFiles(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "20") int limit) {
        return Mono.fromCallable(() -> {
            String needle = q.toLowerCase();
            int cap = Math.max(1, Math.min(limit, 50));
            List<String> candidates = new ArrayList<>();
            try (Stream<Path> stream = Files.walk(workspaceRoot, 10)) {
                stream.filter(Files::isRegularFile)
                        .map(workspaceRoot::relativize)
                        .filter(rel -> {
                            for (Path seg : rel) {
                                String name = seg.toString();
                                if (name.startsWith(".") || SKIP_DIRS.contains(name)) return false;
                            }
                            return true;
                        })
                        .map(Path::toString)
                        .filter(p -> needle.isEmpty() || p.toLowerCase().contains(needle))
                        .limit(5000)
                        .forEach(candidates::add);
            }
            candidates.sort(Comparator
                    .comparingInt((String p) -> matchRank(p, needle))
                    .thenComparingInt(String::length)
                    .thenComparing(p -> p));
            return candidates.size() > cap ? candidates.subList(0, cap) : candidates;
        });
    }

    /** Rank: filename prefix match (0) &gt; filename substring (1) &gt; path-only match (2). */
    private static int matchRank(String path, String needle) {
        if (needle.isEmpty()) return 0;
        String name = path.substring(path.lastIndexOf('/') + 1).toLowerCase();
        if (name.startsWith(needle)) return 0;
        if (name.contains(needle)) return 1;
        return 2;
    }

    /**
     * Read file content.
     *
     * @param path relative path inside workspace
     * @return {content, language, size}
     */
    @GetMapping("/file")
    public Mono<Map<String, Object>> readFile(
            @RequestParam String path) {
        return Mono.fromCallable(() -> {
            Map<String, Object> result = new LinkedHashMap<>();
            Path file = workspaceRoot.resolve(path).normalize();
            // Security: ensure path is within workspace
            if (!file.startsWith(workspaceRoot)) {
                result.put("error", "Access denied");
                return result;
            }
            if (!Files.isRegularFile(file)) {
                result.put("error", "Not a file");
                return result;
            }
            // Limit file size (1MB)
            long size = Files.size(file);
            if (size > 1024 * 1024) {
                result.put("error", "File too large (" + (size / 1024 / 1024) + "MB)");
                return result;
            }
            String content = Files.readString(file);
            result.put("content", content);
            result.put("size", size);
            result.put("language", detectLanguage(file.getFileName().toString()));
            return result;
        });
    }

    /**
     * Serve image file as binary for preview.
     *
     * @param path relative path inside workspace
     * @return image binary with correct content type
     */
    @GetMapping("/image")
    public Mono<ResponseEntity<byte[]>> readImage(
            @RequestParam String path) {
        return Mono.fromCallable(() -> {
            Path file = workspaceRoot.resolve(path).normalize();
            if (!file.startsWith(workspaceRoot)) {
                return ResponseEntity.badRequest().body(null);
            }
            if (!Files.isRegularFile(file)) {
                return ResponseEntity.badRequest().body(null);
            }
            long size = Files.size(file);
            if (size > 5 * 1024 * 1024) {
                return ResponseEntity.badRequest().body(null);
            }
            byte[] bytes = Files.readAllBytes(file);
            String contentType = detectImageType(file.getFileName().toString());
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(bytes);
        });
    }

    /**
     * Detect programming language from file extension for syntax highlighting.
     */
    private static String detectLanguage(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return "plaintext";
        String ext = filename.substring(dot + 1).toLowerCase();
        return switch (ext) {
            case "java" -> "java";
            case "js" -> "javascript";
            case "ts" -> "typescript";
            case "py" -> "python";
            case "go" -> "go";
            case "rs" -> "rust";
            case "rb" -> "ruby";
            case "c", "h" -> "c";
            case "cpp", "cc", "cxx", "hpp" -> "cpp";
            case "sh", "bash", "zsh" -> "bash";
            case "xml" -> "xml";
            case "html", "htm" -> "html";
            case "css" -> "css";
            case "sql" -> "sql";
            case "json" -> "json";
            case "yaml", "yml" -> "yaml";
            case "md" -> "markdown";
            case "kt" -> "kotlin";
            case "scala" -> "scala";
            case "php" -> "php";
            case "toml" -> "toml";
            case "dockerfile" -> "dockerfile";
            default -> "plaintext";
        };
    }

    /**
     * Detect image content type from file extension.
     */
    private static String detectImageType(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return "application/octet-stream";
        String ext = filename.substring(dot + 1).toLowerCase();
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "bmp" -> "image/bmp";
            case "ico" -> "image/x-icon";
            default -> "application/octet-stream";
        };
    }

    /**
     * Check if a filename has an image extension.
     */
    public static boolean isImageFile(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = filename.substring(dot + 1).toLowerCase();
        return "png|jpg|jpeg|gif|webp|svg|bmp|ico".contains(ext);
    }
}
