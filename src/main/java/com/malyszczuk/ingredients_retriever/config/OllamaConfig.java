package com.malyszczuk.ingredients_retriever.config;

import com.malyszczuk.ingredients_retriever.agent.OllamaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(OllamaProperties.class)
public class OllamaConfig {

    @Bean
    public RestClient ollamaRestClient(OllamaProperties ollamaProperties) {
        return RestClient.builder()
                .baseUrl(ollamaProperties.baseUrl())
                .build();
    }
}
