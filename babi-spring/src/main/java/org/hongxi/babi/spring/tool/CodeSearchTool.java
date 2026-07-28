package org.hongxi.babi.spring.tool;

import org.hongxi.babi.common.tool.CodeSearchLogic;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Tool for searching code in a directory using pattern matching.
 *
 * <p>Delegates to {@link CodeSearchLogic} for the actual search logic.
 */
public class CodeSearchTool {

    @Tool(name = "code_search", description = "Search for a pattern in files under a directory. Returns matching lines "
            + "with file paths and line numbers. Uses ripgrep if available, falls back to grep. "
            + "Supports regex patterns.")
    public String codeSearch(
            @ToolParam(description = "The text or regex pattern to search for") String pattern,
            @ToolParam(description = "Directory to search in (default: current directory)") String directory,
            @ToolParam(description = "Optional glob to filter files, e.g. '*.java', '*.py'") String filePattern,
            @ToolParam(description = "Maximum number of results to return (default: 50)") int maxResults) {
        return CodeSearchLogic.codeSearch(pattern, directory, filePattern, maxResults);
    }
}
