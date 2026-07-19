package org.hongxi.babi.agent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tool for editing files via exact string replacement.
 *
 * <p>Replaces the first occurrence of {@code oldText} with {@code newText}
 * in the target file. The match is exact — whitespace and indentation matter.
 */
public class FileEditTool {

    @Tool(name = "edit_file", description = "Edit a file by replacing an exact text match. The old_text must match the file content exactly (including whitespace). Only the first occurrence is replaced.")
    public String editFile(
            @ToolParam(name = "file_path", description = "Path to the file to edit") String filePath,
            @ToolParam(name = "old_text", description = "The exact text to find in the file (must match precisely)") String oldText,
            @ToolParam(name = "new_text", description = "The text to replace it with") String newText) {

        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) {
                return "Error: File not found: " + filePath;
            }
            if (!Files.isRegularFile(path)) {
                return "Error: Not a regular file: " + filePath;
            }

            String content = Files.readString(path);
            if (!content.contains(oldText)) {
                return "Error: old_text not found in file. Make sure the text matches exactly, including whitespace and indentation. Try reading the file first to get the exact content.";
            }

            // Replace only the first occurrence
            String updated = content.replaceFirst(
                    java.util.regex.Pattern.quote(oldText),
                    java.util.regex.Matcher.quoteReplacement(newText));

            Files.writeString(path, updated);

            // Count remaining occurrences for user awareness
            long remaining = content.split(java.util.regex.Pattern.quote(oldText), -1).length - 1;
            if (remaining > 1) {
                return "Successfully replaced 1 occurrence. Note: " + (remaining - 1) + " more occurrence(s) of old_text remain in the file.";
            }
            return "Successfully edited " + filePath;
        } catch (IOException e) {
            return "Error editing file: " + e.getMessage();
        }
    }
}
