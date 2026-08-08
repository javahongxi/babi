package org.hongxi.babi.common.tool;

import org.hongxi.babi.common.util.SessionContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared logic for the {@code todo_write} task-list tool.
 *
 * <p>Mirrors the agentscope-java {@code TodoTools} semantics:
 * <ul>
 *   <li>Full-list-replace — the model passes the COMPLETE updated list every call</li>
 *   <li>Session-scoped state — tasks are isolated by session ID via {@link SessionContextHolder}</li>
 *   <li>Single in_progress invariant — at most one task may be in_progress at a time</li>
 * </ul>
 *
 * <p>Unlike agentscope which persists tasks in {@code AgentState.tasksContext},
 * this implementation uses an in-memory {@link ConcurrentHashMap}. Tasks are
 * lost on restart but survive across turns within the same session.
 */
public final class TodoWriteLogic {

    /** Tool description for the {@code todo_write} tool (used in @Tool annotations). */
    public static final String TOOL_DESCRIPTION = """
            Create and maintain a structured task list for the current session. Tracks progress,
            organizes multi-step work, and surfaces status to the user. Pass the COMPLETE updated
            list every call — this tool replaces the whole list (it does not merge).

            ## When to use
            Use proactively when:
            - The task needs 3+ distinct steps or actions
            - The work is non-trivial and benefits from planning
            - The user gives multiple tasks (numbered or comma-separated) or asks for a todo list
            - New instructions arrive — capture them as todos
            - You start a task — mark it in_progress (only one at a time) before working
            - You finish a task — mark it completed and add any follow-ups you discovered

            ## When NOT to use
            Skip when the work is a single straightforward step (<3 trivial steps), or the request
            is purely informational/conversational, or tracking adds no value.

            ## States
            - pending     — not started
            - in_progress — actively working (exactly ONE at a time)
            - completed   — finished successfully

            ## Rules
            - Update status in real time; do not batch completions.
            - Mark completed only after the work is actually done, including any required
              verification — never based on intent.
            - Keep exactly one task in_progress while work remains.
            - If blocked or partial, keep the task in_progress and add a follow-up todo describing
              the blocker.
            - Items must be specific and actionable; break large work into smaller steps.
            - To drop a task that is no longer needed, simply omit it from the list.
            """;

    /** Parameter description for the {@code todos} parameter (used in @ToolParam/@P annotations). */
    public static final String PARAM_DESCRIPTION = "The COMPLETE updated todo list. "
            + "Replaces the existing list entirely. "
            + "Each item has: content (string), status (pending|in_progress|completed), "
            + "priority (high|medium|low, optional)";

    /** Per-session task storage. Outer key = sessionId, value = mutable task list. */
    private static final ConcurrentHashMap<String, List<TodoItem>> SESSION_TASKS =
            new ConcurrentHashMap<>();

    private TodoWriteLogic() {}

    /**
     * Replace the session's task list with the given items.
     *
     * @param todos the complete updated task list (replaces existing)
     * @return rendered task list or error message
     */
    public static String todoWrite(List<TodoItem> todos) {
        String sessionId = SessionContextHolder.getSessionId();
        if (sessionId == null) {
            sessionId = "default";
        }

        List<TodoItem> items = todos == null ? List.of() : todos;

        // Validate
        int inProgress = 0;
        for (TodoItem item : items) {
            String status = normalizeStatus(item.status());
            if (status == null) {
                return "Error: invalid status '" + item.status()
                        + "' for todo '" + safe(item.content())
                        + "'. Allowed: pending, in_progress, completed.";
            }
            if ("in_progress".equals(status)) {
                inProgress++;
            }
            if (item.content() == null || item.content().isBlank()) {
                return "Error: every todo must have non-blank content.";
            }
        }
        if (inProgress > 1) {
            return "Error: at most one task may be in_progress at a time, but "
                    + inProgress + " were provided. Keep exactly one in_progress.";
        }

        // Normalize and store
        List<TodoItem> normalized = new ArrayList<>(items.size());
        for (TodoItem item : items) {
            normalized.add(new TodoItem(
                    item.content(),
                    normalizeStatus(item.status()),
                    normalizePriority(item.priority())));
        }
        SESSION_TASKS.put(sessionId, normalized);

        return render(normalized);
    }

    /**
     * Get the current task list for a session.
     *
     * @param sessionId session identifier
     * @return the current task list (empty if none)
     */
    public static List<TodoItem> getTasks(String sessionId) {
        List<TodoItem> tasks = SESSION_TASKS.get(sessionId);
        return tasks == null ? List.of() : List.copyOf(tasks);
    }

    /**
     * Render the task list as a human-readable string.
     */
    public static String render(List<TodoItem> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return "Todo list cleared (0 items).";
        }
        long open = tasks.stream()
                .filter(t -> !"completed".equals(t.status()))
                .count();
        StringBuilder sb = new StringBuilder();
        sb.append(open).append(" open todo(s):\n");
        for (TodoItem t : tasks) {
            sb.append(marker(t.status())).append(' ').append(t.content());
            if (t.priority() != null) {
                sb.append(" (priority: ").append(t.priority()).append(')');
            }
            sb.append('\n');
        }
        return sb.toString().stripTrailing();
    }

    /**
     * Render a system-reminder block for the current session's tasks.
     * Returns empty string if no tasks exist.
     */
    public static String renderReminder(String sessionId) {
        List<TodoItem> tasks = getTasks(sessionId);
        if (tasks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<system-reminder>\n");
        sb.append("Your current todo list is shown below. This is the source of truth — do not");
        sb.append(" assume earlier statuses still hold. Keep exactly one task in_progress.");
        sb.append(" Update the whole list with todo_write as you progress.\n\n");
        for (TodoItem t : tasks) {
            sb.append(marker(t.status())).append(' ').append(t.content());
            if (t.priority() != null) {
                sb.append(" (priority: ").append(t.priority()).append(')');
            }
            sb.append('\n');
        }
        sb.append("</system-reminder>");
        return sb.toString();
    }

    static String marker(String status) {
        return switch (status) {
            case "completed" -> "- [x]";
            case "in_progress" -> "- [~]";
            default -> "- [ ]";
        };
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return "pending";
        return switch (status.trim().toLowerCase()) {
            case "pending", "in_progress", "completed" -> status.trim().toLowerCase();
            default -> null;
        };
    }

    private static String normalizePriority(String priority) {
        if (priority == null || priority.isBlank()) return null;
        return switch (priority.trim().toLowerCase()) {
            case "high", "medium", "low" -> priority.trim().toLowerCase();
            default -> null;
        };
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /**
     * A single task item.
     *
     * @param content  task description
     * @param status   one of: pending, in_progress, completed
     * @param priority optional: high, medium, low
     */
    public record TodoItem(String content, String status, String priority) {}
}
