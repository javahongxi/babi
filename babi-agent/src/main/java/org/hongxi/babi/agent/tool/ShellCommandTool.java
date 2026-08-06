package org.hongxi.babi.agent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.hongxi.babi.common.tool.ShellCommandLogic;

import java.io.File;

/**
 * Tool for executing shell commands.
 *
 * <p>Delegates to {@link ShellCommandLogic} for the actual command execution.
 *
 * <p><b>Note:</b> This tool is not registered by default. The framework's built-in
 * {@code ShellExecuteTool} is used instead. If you prefer to use this tool for its
 * cleaner output format, you need to disable the built-in tool when building the agent:
 * <pre>{@code
 * HarnessAgent.builder()
 *     .disableShellTool()
 *     // register this custom tool
 *     .build();
 * }</pre>
 */
public class ShellCommandTool {

    private final File workingDir;

    public ShellCommandTool() {
        this(null);
    }

    public ShellCommandTool(String workingDir) {
        this.workingDir = workingDir != null ? new File(workingDir) : null;
    }

    @Tool(
            name = "shell_command",
            description = "Execute a shell command on the local system and return its output. "
                    + "Use for build, test, git, and diagnostic commands.")
    public String shellCommand(
            @ToolParam(name = "command", description = "The shell command to execute") String command) {
        return ShellCommandLogic.shellCommand(command, workingDir);
    }
}
