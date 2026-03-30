package com.maple.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for the URL proxy feature.
 *
 * <p>Provides a RestTemplate dedicated to outbound proxy requests with
 * configurable timeouts to prevent resource exhaustion.
 */
@Configuration
public class UrlProxyConfig {

    @Value("${maple.url-proxy.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${maple.url-proxy.read-timeout-ms:10000}")
    private int readTimeoutMs;

    @Bean(name = "urlProxyRestTemplate")
    public RestTemplate urlProxyRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return new RestTemplate(factory);
    }
}
