package org.hongxi.babi.graph.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(DashScopeProperties.PREFIX)
public record DashScopeProperties(
        String apiKey,
        @NestedConfigurationProperty
        ChatProperties chat,
        @NestedConfigurationProperty
        ImageProperties image
) {
    static final String PREFIX = "babi.dashscope";
}
