package org.hongxi.babi.agent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Tool for searching code in a directory using pattern matching.
 *
 * <p>Uses ripgrep (rg) if available, falls back to grep -rn.
 * Returns matching lines with file path and line numbers.
 */
public class CodeSearchTool {

    @Tool(name = "code_search", description = "Search for a pattern in files under a directory. Returns matching lines with file paths and line numbers. Uses ripgrep if available, falls back to grep. Supports regex patterns.")
    public String codeSearch(
            @ToolParam(name = "pattern", description = "The text or regex pattern to search for") String pattern,
            @ToolParam(name = "directory", description = "Directory to search in (default: current directory)") String directory,
            @ToolParam(name = "file_pattern", description = "Optional glob to filter files, e.g. '*.java', '*.py'") String filePattern,
            @ToolParam(name = "max_results", description = "Maximum number of results to return (default: 50)") int maxResults) {

        if (pattern == null || pattern.isBlank()) {
            return "Error: pattern cannot be empty";
        }
        if (directory == null || directory.isBlank()) {
            directory = ".";
        }
        if (maxResults <= 0) {
            maxResults = 50;
        }

        try {
            // Try ripgrep first, fall back to grep
            if (isRipgrepAvailable()) {
                return searchWithRipgrep(pattern, directory, filePattern, maxResults);
            } else {
                return searchWithGrep(pattern, directory, filePattern, maxResults);
            }
        } catch (Exception e) {
            return "Error searching: " + e.getMessage();
        }
    }

    private boolean isRipgrepAvailable() {
        try {
            Process p = new ProcessBuilder("which", "rg").redirectErrorStream(true).start();
            p.waitFor(3, TimeUnit.SECONDS);
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String searchWithRipgrep(String pattern, String directory, String filePattern, int maxResults) throws Exception {
        var cmd = new ArrayList<String>();
        cmd.add("rg");
        cmd.add("--no-heading");
        cmd.add("--line-number");
        cmd.add("--max-count");
        cmd.add(String.valueOf(maxResults));
        if (filePattern != null && !filePattern.isBlank()) {
            cmd.add("--glob");
            cmd.add(filePattern);
        }
        cmd.add(pattern);
        cmd.add(directory);

        return executeSearch(cmd, maxResults);
    }

    private String searchWithGrep(String pattern, String directory, String filePattern, int maxResults) throws Exception {
        var cmd = new ArrayList<String>();
        cmd.add("grep");
        cmd.add("-rn");
        cmd.add("--max-count");
        cmd.add(String.valueOf(maxResults));
        if (filePattern != null && !filePattern.isBlank()) {
            cmd.add("--include");
            cmd.add(filePattern);
        }
        cmd.add(pattern);
        cmd.add(directory);

        return executeSearch(cmd, maxResults);
    }

    private String executeSearch(ArrayList<String> cmd, int maxResults) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        int lineCount = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null && lineCount < maxResults) {
                output.append(line).append("\n");
                lineCount++;
            }
        }

        boolean finished = process.waitFor(15, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }

        if (output.isEmpty()) {
            return "No matches found for pattern: " + cmd.get(cmd.size() - 2);
        }

        String result = output.toString();
        if (lineCount >= maxResults) {
            result += "\n[Showing first " + maxResults + " results. Narrow your pattern or increase max_results for more.]";
        }
        return result;
    }
}
