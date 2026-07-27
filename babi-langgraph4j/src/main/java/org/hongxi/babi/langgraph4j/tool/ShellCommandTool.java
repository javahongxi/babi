package org.hongxi.babi.langgraph4j.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.hongxi.babi.common.tool.ShellCommandLogic;
import org.hongxi.babi.langgraph4j.eventbus.ToolEventBus;
import org.hongxi.babi.langgraph4j.util.ToolContext;

import java.io.File;
import java.util.Map;

/**
 * Tool for executing shell commands.
 *
 * <p>Delegates to {@link ShellCommandLogic} for the actual command execution.
 */
public class ShellCommandTool {

    private final File workingDir;
    private final ToolEventBus eventBus;

    public ShellCommandTool(ToolEventBus eventBus) {
        this(null, eventBus);
    }

    public ShellCommandTool(String workingDir, ToolEventBus eventBus) {
        this.workingDir = workingDir != null ? new File(workingDir) : null;
        this.eventBus = eventBus;
    }

    @Tool("Execute a shell command on the local system and return its output. Use for build, test, git, and diagnostic commands.")
    public String shellCommand(@P("The shell command to execute") String command) {
        emitEvent("shell_command", Map.of("command", command));
        return ShellCommandLogic.shellCommand(command, workingDir);
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
