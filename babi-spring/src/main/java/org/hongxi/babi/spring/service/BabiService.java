package org.hongxi.babi.spring.service;

import org.hongxi.babi.common.util.SessionContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    /** Tracks active subscriptions per session for interrupt support */
    private final Map<String, Disposable> activeSubscriptions = new ConcurrentHashMap<>();
    /** Tracks active threads per session for thread interrupt */
    private final Map<String, Thread> activeThreads = new ConcurrentHashMap<>();

    public BabiService(ChatClient chatClient, ChatMemory chatMemory) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
    }

    /**
     * Stream a chat response with automatic tool-calling ReAct loop.
     *
     * <p>Returns full {@link ChatResponse} objects so that the caller can
     * extract both text content and reasoning/thinking metadata.
     *
     * @param userMessage the user's input message
     * @param sessionId   session identifier for conversation isolation
     * @return Flux of ChatResponse objects
     */
    public Flux<ChatResponse> streamChat(String userMessage, String sessionId) {
        log.debug("streamChat: message='{}', sessionId='{}'", userMessage, sessionId);
        SessionContextHolder.setSessionId(sessionId);
        // Track current thread for interrupt support
        activeThreads.put(sessionId, Thread.currentThread());
        return chatClient.prompt()
                .user(userMessage)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .stream()
                .chatResponse()
                .contextWrite(ctx -> ctx.put(SESSION_ID_CTX_KEY, sessionId))
                .doFinally(sig -> {
                    SessionContextHolder.clear();
                    activeThreads.remove(sessionId);
                    activeSubscriptions.remove(sessionId);
                    log.debug("Cleaned up session resources: {}", sessionId);
                });
    }

    /**
     * Register a disposable subscription for a session.
     * Called by the controller after subscribing to the stream.
     *
     * @param sessionId  session identifier
     * @param disposable the subscription to track
     */
    public void registerSubscription(String sessionId, Disposable disposable) {
        activeSubscriptions.put(sessionId, disposable);
    }

    /**
     * Interrupt an in-flight request for a specific session.
     * This disposes the subscription and interrupts the executing thread
     * to stop LLM token generation.
     *
     * @param sessionId session identifier to interrupt
     */
    public void interrupt(String sessionId) {
        log.info("Interrupting session: {}", sessionId);
        // Dispose the subscription to cancel the Flux
        Disposable disposable = activeSubscriptions.remove(sessionId);
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
            log.debug("Disposed subscription for session: {}", sessionId);
        }
        // Interrupt the thread to stop any blocking operations
        Thread thread = activeThreads.remove(sessionId);
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
            log.debug("Interrupted thread for session: {}", sessionId);
        }
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
