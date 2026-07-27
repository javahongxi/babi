package org.hongxi.babi.spring.tool;

import org.hongxi.babi.common.tool.ShellCommandLogic;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;

/**
 * Tool for executing shell commands via Spring AI @Tool annotation.
 *
 * <p>Delegates to {@link ShellCommandLogic} for the actual command execution.
 */
public class ShellCommandTools {

    private final File workingDir;

    public ShellCommandTools(String workspacePath) {
        this.workingDir = workspacePath != null ? new File(workspacePath) : null;
    }

    @Tool(description = "Execute a shell command on the local system and return its output. "
            + "Use for build, test, git, and diagnostic commands.")
    public String shellCommand(
            @ToolParam(description = "The shell command to execute") String command) {
        return ShellCommandLogic.shellCommand(command, workingDir);
    }
}
