package org.hongxi.babi.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the babi agent.
 *
 * @param workspace     workspace directory path
 * @param enableTaskList whether to register the todo_write task-list tool
 */
@ConfigurationProperties(AgentProperties.PREFIX)
public record AgentProperties(
        String workspace,
        boolean enableTaskList
) {
    static final String PREFIX = "babi.agent";
}
