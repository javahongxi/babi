package org.hongxi.babi.spring.tool;

import org.hongxi.babi.common.tool.FileEditLogic;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Tool for editing files via exact string replacement.
 *
 * <p>Delegates to {@link FileEditLogic} for the actual file editing.
 */
public class FileEditTool {

    @Tool(name = "edit_file", description = "Edit a file by replacing an exact text match. The old_text must match "
            + "the file content exactly (including whitespace). Only the first occurrence is replaced.")
    public String editFile(
            @ToolParam(description = "Path to the file to edit") String filePath,
            @ToolParam(description = "The exact text to find in the file (must match precisely)") String oldText,
            @ToolParam(description = "The text to replace it with") String newText) {
        return FileEditLogic.editFile(filePath, oldText, newText);
    }
}
