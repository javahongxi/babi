package org.hongxi.babi.agent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.util.ArrayList;
import java.util.List;

/**
 * Tool for tracking task progress via an in-memory todo list.
 *
 * <p>The agent can create, update, and track tasks with statuses:
 * pending, in_progress, completed, cancelled. Each call replaces
 * the entire list (there is no incremental update).
 */
public class TodoWriteTool {

    private final List<TodoItem> items = new ArrayList<>();

    @Tool(name = "todo_write", description = "Create or update a task list for tracking multi-step progress. Pass the COMPLETE list each time — it replaces the previous list. Statuses: pending, in_progress, completed, cancelled. Keep at most ONE task in_progress at a time.")
    public String todoWrite(
            @ToolParam(name = "todos", description = "JSON array of todo objects, each with: id (unique string), content (description, max 70 chars), status (pending|in_progress|completed|cancelled)") String todosJson) {

        try {
            List<TodoItem> parsed = parseTodos(todosJson);
            if (parsed.isEmpty()) {
                return "Error: todos array is empty. Provide at least one task.";
            }

            // Validate: at most one in_progress
            long inProgressCount = parsed.stream()
                    .filter(t -> "in_progress".equals(t.status))
                    .count();
            if (inProgressCount > 1) {
                return "Error: Only ONE task can be in_progress at a time. Found " + inProgressCount + ".";
            }

            items.clear();
            items.addAll(parsed);
            return formatTodos();
        } catch (Exception e) {
            return "Error parsing todos JSON: " + e.getMessage()
                    + "\nExpected format: [{\"id\":\"t1\",\"content\":\"Do something\",\"status\":\"pending\"},{...}]";
        }
    }

    /**
     * Returns the current todo list as formatted text.
     * Called by the framework to inject into context if needed.
     */
    public String getCurrentTodos() {
        if (items.isEmpty()) return "";
        return formatTodos();
    }

    private String formatTodos() {
        StringBuilder sb = new StringBuilder();
        for (TodoItem item : items) {
            sb.append("- [").append(item.status).append("] ")
                    .append(item.id).append(": ")
                    .append(item.content).append("\n");
        }
        return sb.toString();
    }

    private List<TodoItem> parseTodos(String json) {
        // Minimal JSON array parser — avoids external dependency
        List<TodoItem> result = new ArrayList<>();
        json = json.trim();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]")) json = json.substring(0, json.length() - 1);

        // Split by object boundaries
        int depth = 0;
        int start = -1;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    result.add(parseItem(json.substring(start, i + 1)));
                    start = -1;
                }
            }
        }
        return result;
    }

    private TodoItem parseItem(String obj) {
        String id = extractField(obj, "id");
        String content = extractField(obj, "content");
        String status = extractField(obj, "status");

        if (id == null || id.isBlank()) throw new IllegalArgumentException("Missing 'id' field");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Missing 'content' field");
        if (status == null || status.isBlank()) status = "pending";

        // Validate status
        if (!List.of("pending", "in_progress", "completed", "cancelled").contains(status)) {
            throw new IllegalArgumentException("Invalid status: " + status + ". Must be: pending, in_progress, completed, cancelled");
        }

        return new TodoItem(id, content, status);
    }

    private String extractField(String json, String field) {
        String pattern = "\"" + field + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        idx = json.indexOf(":", idx + pattern.length());
        if (idx < 0) return null;
        // Find the value
        idx++;
        while (idx < json.length() && json.charAt(idx) == ' ') idx++;
        if (idx >= json.length()) return null;
        if (json.charAt(idx) == '"') {
            // String value
            int end = json.indexOf('"', idx + 1);
            if (end < 0) return null;
            return json.substring(idx + 1, end);
        }
        return null;
    }

    private record TodoItem(String id, String content, String status) {}
}
