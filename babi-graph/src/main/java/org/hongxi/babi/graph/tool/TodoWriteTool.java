package org.hongxi.babi.graph.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.hongxi.babi.common.tool.TodoWriteLogic;
import org.hongxi.babi.common.tool.TodoWriteLogic.TodoItem;

import java.util.List;

/**
 * Tool for maintaining a structured task list (task list) for the current session.
 *
 * <p>Delegates to {@link TodoWriteLogic} for the actual task management.
 * Uses full-list-replace semantics: the model passes the COMPLETE updated list every call.
 */
public class TodoWriteTool {

    @Tool(name = "todo_write", value = TodoWriteLogic.TOOL_DESCRIPTION)
    public String todoWrite(
            @P(TodoWriteLogic.PARAM_DESCRIPTION) List<TodoItem> todos) {
        return TodoWriteLogic.todoWrite(todos);
    }
}
