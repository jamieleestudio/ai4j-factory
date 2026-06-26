package org.ai4j.chatbi.config;

import org.ai4j.chatbi.semantic.SemanticLayer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class SemanticLayerConfig {

    @Bean
    public SemanticLayer semanticLayer() throws IOException {
        SemanticLayer layer = new SemanticLayer();
        layer.loadFromResources("classpath:semantic/*.json");
        return layer;
    }
}
