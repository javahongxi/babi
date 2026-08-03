package org.hongxi.babi.spring.service;

import org.hongxi.babi.common.util.SessionContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Core service for the Babi Agent, wrapping Spring AI 2.0 ChatClient.
 *
 * <p>Provides streaming chat with automatic ReAct tool-calling loop
 * handled by Spring AI 2.0's ToolCallingAdvisor (auto-registered by DefaultChatClient).
 *
 * <p>Each session's conversation history is managed by {@link ChatMemory}
 * via {@link MessageChatMemoryAdvisor}.
 */
@Service
public class BabiService {

    private static final Logger log = LoggerFactory.getLogger(BabiService.class);

    /** Reactor Context key for propagating sessionId across thread boundaries. */
    public static final String SESSION_ID_CTX_KEY = "babi.sessionId";

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    public BabiService(ChatClient chatClient, ChatMemory chatMemory) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
    }

    /**
     * Stream a chat response with automatic tool-calling ReAct loop.
     *
     * <p>Spring AI 2.0's ToolCallingAdvisor handles the full ReAct cycle:
     * <ol>
     *   <li>Send prompt to model (streaming)</li>
     *   <li>Aggregate stream to detect tool calls</li>
     *   <li>Execute tools if detected</li>
     *   <li>Recursively call model with tool results</li>
     *   <li>Filter intermediate tool-call responses, only emit final text tokens</li>
     * </ol>
     *
     * @param userMessage the user's input message
     * @param sessionId   session identifier for conversation isolation
     * @return Flux of text token strings
     */
    public Flux<String> streamChat(String userMessage, String sessionId) {
        log.debug("streamChat: message='{}', sessionId='{}'", userMessage, sessionId);
        SessionContextHolder.setSessionId(sessionId);
        return chatClient.prompt()
                .user(userMessage)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .stream()
                .content()
                .contextWrite(ctx -> ctx.put(SESSION_ID_CTX_KEY, sessionId))
                .doFinally(sig -> SessionContextHolder.clear());
    }

    /**
     * Synchronous chat with automatic tool-calling ReAct loop.
     *
     * @param userMessage the user's input message
     * @param sessionId   session identifier for conversation isolation
     * @return full response text
     */
    public String chat(String userMessage, String sessionId) {
        log.info("chat: message='{}', sessionId='{}'", userMessage, sessionId);
        SessionContextHolder.setSessionId(sessionId);
        try {
            return chatClient.prompt()
                    .user(userMessage)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .call()
                    .content();
        } finally {
            SessionContextHolder.clear();
        }
    }

    /**
     * Clear conversation memory for a given session.
     */
    public void clearMemory(String sessionId) {
        chatMemory.clear(sessionId);
        log.info("Memory cleared for session: {}", sessionId);
    }
}
