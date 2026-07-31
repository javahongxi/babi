package org.hongxi.babi.langgraph4j.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.hongxi.babi.common.eventbus.ToolEventBus;
import org.hongxi.babi.common.tool.FileEditLogic;

import java.util.Map;

/**
 * Tool for editing files via exact string replacement.
 *
 * <p>Delegates to {@link FileEditLogic} for the actual file editing.
 */
public class FileEditTool extends AbstractNotifyingTool {

    public FileEditTool(ToolEventBus eventBus) {
        super(eventBus);
    }

    @Tool(name = "edit_file", value = "Edit a file by replacing an exact text match. The old_text must match the file content exactly (including whitespace). Only the first occurrence is replaced.")
    public String editFile(
            @P("Path to the file to edit") String filePath,
            @P("The exact text to find in the file (must match precisely)") String oldText,
            @P("The text to replace it with") String newText) {

        emitEvent("edit_file", Map.of("file_path", filePath));
        return FileEditLogic.editFile(filePath, oldText, newText);
    }
}
