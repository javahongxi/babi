package org.hongxi.babi.agent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Tool for finding files by name pattern using glob syntax.
 *
 * <p>Uses {@link java.nio.file.PathMatcher} with {@code glob:} syntax to match
 * file names against patterns like {@code **&#47;*.java}, {@code *.xml}, etc.
 * Automatically excludes common non-source directories such as {@code .git},
 * {@code node_modules}, {@code target}, etc.
 */
public class GlobTool {

    /** Default maximum number of results. */
    private static final int DEFAULT_MAX_RESULTS = 100;

    /** Directories to skip during traversal. */
    private static final Set<String> EXCLUDED_DIRS = Set.of(
            ".git", "node_modules", "target", "build", "dist", ".idea", ".vscode", "__pycache__"
    );

    @Tool(
            name = "glob_files",
            description = "Find files by name pattern (glob) under a directory. "
                    + "Returns matching file paths. Supports patterns like '**/*.java', '*.xml', 'src/**/*.py'. "
                    + "Use this to discover files before reading them.",
            readOnly = true)
    public String globFiles(
            @ToolParam(name = "pattern", description = "Glob pattern to match files, e.g. '**/*.java', '*.xml'") String pattern,
            @ToolParam(name = "directory", description = "Directory to search in (default: current directory)") String directory,
            @ToolParam(name = "max_results", description = "Maximum number of results to return (default: 100)") int maxResults) {
        if (pattern == null || pattern.isBlank()) {
            return "Error: pattern cannot be empty";
        }
        if (directory == null || directory.isBlank()) {
            directory = ".";
        }
        if (maxResults <= 0) {
            maxResults = DEFAULT_MAX_RESULTS;
        }

        Path root = Path.of(directory);
        if (!Files.isDirectory(root)) {
            return "Error: Not a directory: " + directory;
        }

        PathMatcher matcher = root.getFileSystem().getPathMatcher("glob:" + pattern);
        List<String> matched = new ArrayList<>();
        final int limit = maxResults;

        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (dir.equals(root)) return FileVisitResult.CONTINUE;
                    String name = dir.getFileName().toString();
                    if (EXCLUDED_DIRS.contains(name)) return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    Path relative = root.relativize(file);
                    if (matcher.matches(relative) || matcher.matches(file.getFileName())) {
                        matched.add(relative.toString());
                        if (matched.size() >= limit) return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return "Error searching: " + e.getMessage();
        }

        if (matched.isEmpty()) {
            return "No files matched pattern: " + pattern;
        }

        String result = String.join("\n", matched);
        if (matched.size() >= limit) {
            result += "\n[Showing first " + matched.size()
                    + " results. Narrow your pattern or increase max_results for more.]";
        }
        return result;
    }
}