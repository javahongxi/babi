package org.hongxi.babi.graph.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = Properties.PREFIX)
public record Properties(

    @NestedConfigurationProperty
    ChatModelProperties streamingChatModel
) {
    static final String PREFIX = "babi.open-ai";
}