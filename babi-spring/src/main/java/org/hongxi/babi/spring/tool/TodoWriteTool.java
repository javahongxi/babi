package org.hongxi.babi.spring.tool;

import org.hongxi.babi.common.tool.TodoWriteLogic;
import org.hongxi.babi.common.tool.TodoWriteLogic.TodoItem;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

/**
 * Tool for maintaining a structured task list (task list) for the current session.
 *
 * <p>Delegates to {@link TodoWriteLogic} for the actual task management.
 * Uses full-list-replace semantics: the model passes the COMPLETE updated list every call.
 */
public class TodoWriteTool {

    @Tool(name = "todo_write", description = TodoWriteLogic.TOOL_DESCRIPTION)
    public String todoWrite(
            @ToolParam(description = TodoWriteLogic.PARAM_DESCRIPTION) List<TodoItem> todos) {
        return TodoWriteLogic.todoWrite(todos);
    }
}
