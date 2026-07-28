package org.hongxi.babi.langgraph4j.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.hongxi.babi.common.tool.FileEditLogic;
import org.hongxi.babi.langgraph4j.eventbus.ToolEventBus;
import org.hongxi.babi.langgraph4j.util.ToolContext;

import java.util.Map;

/**
 * Tool for editing files via exact string replacement.
 *
 * <p>Delegates to {@link FileEditLogic} for the actual file editing.
 */
public class FileEditTool {

    private final ToolEventBus eventBus;

    public FileEditTool(ToolEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Tool(name = "edit_file", value = "Edit a file by replacing an exact text match. The old_text must match the file content exactly (including whitespace). Only the first occurrence is replaced.")
    public String editFile(
            @P("Path to the file to edit") String filePath,
            @P("The exact text to find in the file (must match precisely)") String oldText,
            @P("The text to replace it with") String newText) {

        emitEvent("edit_file", Map.of("file_path", filePath));
        return FileEditLogic.editFile(filePath, oldText, newText);
    }

    private void emitEvent(String toolName, Map<String, Object> input) {
        if (eventBus != null) {
            String sessionId = ToolContext.getSessionId();
            if (sessionId != null) {
                eventBus.publish(ToolEventBus.ToolEvent.toolCall(sessionId, toolName, input));
            }
        }
    }
}
