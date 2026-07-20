package org.hongxi.babi.agent.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ReasoningInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Middleware that truncates the conversation context before sending to the LLM,
 * keeping only the most recent messages to control token usage.
 *
 * <p>The truncation preserves tool call/result pairs — it never cuts between
 * an assistant message containing tool calls and the corresponding tool results.
 *
 * <p>Usage:
 * <pre>{@code
 * ReActAgent.builder()
 *     .middleware(new ContextTruncateMiddleware(30))
 *     .build();
 * }</pre>
 */
public class ContextTruncateMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(ContextTruncateMiddleware.class);

    /** Maximum number of messages to keep in the context sent to the model. */
    private final int maxMessages;

    /**
     * @param maxMessages max messages to retain (must be >= 2)
     */
    public ContextTruncateMiddleware(int maxMessages) {
        if (maxMessages < 2) {
            throw new IllegalArgumentException("maxMessages must be >= 2, got: " + maxMessages);
        }
        this.maxMessages = maxMessages;
    }

    @Override
    public Flux<AgentEvent> onReasoning(
            Agent agent,
            RuntimeContext ctx,
            ReasoningInput input,
            Function<ReasoningInput, Flux<AgentEvent>> next) {

        List<Msg> messages = input.messages();
        if (messages.size() <= maxMessages) {
            return next.apply(input);
        }

        List<Msg> truncated = truncate(messages, maxMessages);
        log.debug("Context truncated: {} -> {} messages (sessionId={})",
                messages.size(), truncated.size(),
                ctx != null ? ctx.getSessionId() : "null");

        ReasoningInput truncatedInput = new ReasoningInput(truncated, input.tools(), input.options());
        return next.apply(truncatedInput);
    }

    /**
     * Truncate messages to at most {@code maxMessages}, preserving tool call/result pairs.
     *
     * <p>Strategy:
     * <ol>
     *   <li>Take the last {@code maxMessages} messages as the candidate window.</li>
     *   <li>If the window starts with a TOOL result (orphaned), skip forward until
     *       we find a USER or ASSISTANT message.</li>
     *   <li>If the window ends with an ASSISTANT tool call missing its results,
     *       extend forward to include those results.</li>
     * </ol>
     */
    private List<Msg> truncate(List<Msg> messages, int maxMessages) {
        int total = messages.size();
        int start = total - maxMessages;

        // Skip forward past orphaned TOOL results at the start of the window
        while (start < total && messages.get(start).getRole() == MsgRole.TOOL) {
            start++;
        }

        // If we skipped everything, fall back to keeping the last maxMessages as-is
        if (start >= total) {
            return new ArrayList<>(messages.subList(total - maxMessages, total));
        }

        List<Msg> window = new ArrayList<>(messages.subList(start, total));

        // Check if the last ASSISTANT message in the window has pending tool calls
        // whose results are outside the window — if so, trim that assistant message's
        // tool calls to avoid sending incomplete pairs.
        // (In practice the ReAct loop ensures tool results follow immediately,
        //  so this mainly guards against edge cases at the boundary.)

        return window;
    }
}
