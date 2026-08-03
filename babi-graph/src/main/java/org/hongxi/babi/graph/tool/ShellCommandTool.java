package org.hongxi.babi.graph.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.hongxi.babi.common.tool.ShellCommandLogic;

import java.io.File;

/**
 * Tool for executing shell commands.
 *
 * <p>Delegates to {@link ShellCommandLogic} for the actual command execution.
 */
public class ShellCommandTool {

    private final File workingDir;

    public ShellCommandTool() {
        this(null);
    }

    public ShellCommandTool(String workingDir) {
        this.workingDir = workingDir != null ? new File(workingDir) : null;
    }

    @Tool(name = "shell_command", value = "Execute a shell command on the local system and return its output. Use for build, test, git, and diagnostic commands.")
    public String shellCommand(@P("The shell command to execute") String command) {
        return ShellCommandLogic.shellCommand(command, workingDir);
    }
}
