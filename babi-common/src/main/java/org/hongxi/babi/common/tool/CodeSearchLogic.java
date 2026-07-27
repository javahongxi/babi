package org.hongxi.babi.common.tool;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Shared logic for searching code using pattern matching.
 *
 * <p>Uses ripgrep ({@code rg}) if available, falls back to {@code grep -rn}.
 *
 * <p>This class contains no framework-specific annotations — it is used by
 * both LangGraph4j and Spring AI tool wrappers.
 */
public final class CodeSearchLogic {

    private CodeSearchLogic() {}

    /** Default maximum number of results. */
    private static final int DEFAULT_MAX_RESULTS = 50;

    /** Timeout for the search process in seconds. */
    private static final int SEARCH_TIMEOUT_SECONDS = 15;

    /**
     * Searches for a pattern in files under a directory.
     *
     * @param pattern     the text or regex pattern to search for
     * @param directory   directory to search in (defaults to "." if blank)
     * @param filePattern optional glob to filter files (e.g. "*.java"), may be {@code null}
     * @param maxResults  maximum number of results (defaults to 50 if &lt;= 0)
     * @return matching lines with file paths and line numbers
     */
    public static String codeSearch(String pattern, String directory,
                                    String filePattern, int maxResults) {
        if (pattern == null || pattern.isBlank()) {
            return "Error: pattern cannot be empty";
        }
        if (directory == null || directory.isBlank()) {
            directory = ".";
        }
        if (maxResults <= 0) {
            maxResults = DEFAULT_MAX_RESULTS;
        }

        try {
            if (isRipgrepAvailable()) {
                return searchWithRipgrep(pattern, directory, filePattern, maxResults);
            } else {
                return searchWithGrep(pattern, directory, filePattern, maxResults);
            }
        } catch (Exception e) {
            return "Error searching: " + e.getMessage();
        }
    }

    private static boolean isRipgrepAvailable() {
        try {
            Process p = new ProcessBuilder("which", "rg").redirectErrorStream(true).start();
            p.waitFor(3, TimeUnit.SECONDS);
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String searchWithRipgrep(String pattern, String directory,
                                            String filePattern, int maxResults) throws Exception {
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

    private static String searchWithGrep(String pattern, String directory,
                                         String filePattern, int maxResults) throws Exception {
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

    private static String executeSearch(ArrayList<String> cmd, int maxResults) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        int lineCount = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null && lineCount < maxResults) {
                output.append(line).append("\n");
                lineCount++;
            }
        }

        boolean finished = process.waitFor(SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }

        if (output.isEmpty()) {
            return "No matches found for pattern: " + cmd.get(cmd.size() - 2);
        }

        String result = output.toString();
        if (lineCount >= maxResults) {
            result += "\n[Showing first " + maxResults
                    + " results. Narrow your pattern or increase max_results for more.]";
        }
        return result;
    }
}
