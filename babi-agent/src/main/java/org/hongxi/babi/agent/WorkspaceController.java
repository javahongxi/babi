package org.hongxi.babi.agent;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Workspace file system API for the browser-based IDE.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/workspace/tree?path=} — list directory children</li>
 *   <li>{@code GET /api/workspace/file?path=} — read file content</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    private final Path workspaceRoot;

    public WorkspaceController(
            @Value("${babi.agent.workspace:~/babi-workspace}") String workspace) {
        this.workspaceRoot = Path.of(AgentConstants.resolveWorkspace(workspace));
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
