package org.hongxi.babi.graph.controller;

import org.hongxi.babi.common.config.DashScopeProperties;
import org.hongxi.babi.common.model.ModelCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * UI configuration endpoint for frontend display settings.
 */
@RestController
@RequestMapping("/api/ui")
public class UIConfigController {

    private final DashScopeProperties properties;

    public UIConfigController(DashScopeProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/config")
    public Mono<Map<String, Object>> config() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("badge", "LangGraph Java");
        config.put("models", ModelCatalog.list());
        config.put("defaultModel", properties.chat().model());
        return Mono.just(config);
    }
}
