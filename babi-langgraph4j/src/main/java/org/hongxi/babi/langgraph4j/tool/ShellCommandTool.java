package org.hongxi.babi.langgraph4j.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.hongxi.babi.common.eventbus.ToolEventBus;
import org.hongxi.babi.common.tool.ShellCommandLogic;

import java.io.File;
import java.util.Map;

/**
 * Tool for executing shell commands.
 *
 * <p>Delegates to {@link ShellCommandLogic} for the actual command execution.
 */
public class ShellCommandTool extends AbstractNotifyingTool {

    private final File workingDir;

    public ShellCommandTool(ToolEventBus eventBus) {
        this(null, eventBus);
    }

    public ShellCommandTool(String workingDir, ToolEventBus eventBus) {
        super(eventBus);
        this.workingDir = workingDir != null ? new File(workingDir) : null;
    }

    @Tool(name = "shell_command", value = "Execute a shell command on the local system and return its output. Use for build, test, git, and diagnostic commands.")
    public String shellCommand(@P("The shell command to execute") String command) {
        emitEvent("shell_command", Map.of("command", command));
        return ShellCommandLogic.shellCommand(command, workingDir);
    }
}
