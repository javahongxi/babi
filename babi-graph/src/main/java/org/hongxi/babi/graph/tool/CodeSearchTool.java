package org.hongxi.babi.graph.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.hongxi.babi.common.eventbus.ToolEventBus;
import org.hongxi.babi.common.tool.CodeSearchLogic;

import java.util.Map;

/**
 * Tool for searching code in a directory using pattern matching.
 *
 * <p>Delegates to {@link CodeSearchLogic} for the actual search logic.
 */
public class CodeSearchTool extends AbstractNotifyingTool {

    public CodeSearchTool(ToolEventBus eventBus) {
        super(eventBus);
    }

    @Tool(name = "code_search", value = "Search for a pattern in files under a directory. Returns matching lines with file paths and line numbers. Uses ripgrep if available, falls back to grep. Supports regex patterns.")
    public String codeSearch(
            @P("The text or regex pattern to search for") String pattern,
            @P("Directory to search in (default: current directory)") String directory,
            @P("Optional glob to filter files, e.g. '*.java', '*.py'") String filePattern,
            @P("Maximum number of results to return (default: 50)") int maxResults) {

        emitEvent("code_search", Map.of("pattern", pattern, "directory", directory != null ? directory : "."));
        return CodeSearchLogic.codeSearch(pattern, directory, filePattern, maxResults);
    }
}
