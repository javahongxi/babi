package org.hongxi.babi.codingagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Spring Boot application entry point for the Coding Agent.
 *
 * <p>Provides a web API for the BabiCodingAgent via {@link CodingAgentController},
 * supporting both SSE streaming and synchronous chat endpoints.
 *
 * <p><b>Run:</b>
 * <pre>
 *   export DASHSCOPE_API_KEY=your_key
 *   mvn spring-boot:run -pl babi-codingagent
 * </pre>
 */
@SpringBootApplication
public class CodingAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(CodingAgentApplication.class, args);
    }

    @Bean
    public ApplicationListener<ApplicationReadyEvent> applicationReadyEventListener(Environment environment) {
        return event -> {
            String port = environment.getProperty("server.port", "8080");
            String contextPath = environment.getProperty("server.servlet.context-path", "");
            String accessUrl = "http://localhost:" + port + contextPath;
            System.out.println("\n========================================");
            System.out.println("Application is ready!");
            System.out.println("Chat with your agent: " + accessUrl);
            System.out.println("========================================\n");
        };
    }
}
