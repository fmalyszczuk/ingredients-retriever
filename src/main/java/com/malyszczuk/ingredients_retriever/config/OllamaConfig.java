package com.malyszczuk.ingredients_retriever.config;

import com.malyszczuk.ingredients_retriever.agent.OllamaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(OllamaProperties.class)
public class OllamaConfig {

    @Bean
    public RestClient ollamaRestClient(OllamaProperties ollamaProperties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(ollamaProperties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(ollamaProperties.readTimeout());

        return RestClient.builder()
                .baseUrl(ollamaProperties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
