package org.ai4j.factory.bi.semantic;

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
