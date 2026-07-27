package org.hongxi.babi.common.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared logic for editing files via exact string replacement.
 *
 * <p>This class contains no framework-specific annotations — it is used by
 * both LangGraph4j and Spring AI tool wrappers.
 */
public final class FileEditLogic {

    private FileEditLogic() {}

    /**
     * Edits a file by replacing the first occurrence of {@code oldText} with {@code newText}.
     *
     * @param filePath path to the file to edit
     * @param oldText  the exact text to find (must match precisely)
     * @param newText  the replacement text
     * @return success message, or an error description
     */
    public static String editFile(String filePath, String oldText, String newText) {
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
                return "Error: old_text not found in file. Make sure the text matches exactly, "
                        + "including whitespace and indentation. Try reading the file first to get the exact content.";
            }

            String updated = content.replaceFirst(
                    Pattern.quote(oldText),
                    Matcher.quoteReplacement(newText));

            Files.writeString(path, updated);

            long remaining = content.split(Pattern.quote(oldText), -1).length - 1;
            if (remaining > 1) {
                return "Successfully replaced 1 occurrence. Note: " + (remaining - 1)
                        + " more occurrence(s) of old_text remain in the file.";
            }
            return "Successfully edited " + filePath;
        } catch (IOException e) {
            return "Error editing file: " + e.getMessage();
        }
    }
}
