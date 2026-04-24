package com.skillproof.backend_core.config;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class AppConfig {

    @Value("${http.client.connect-timeout:20s}")
    private Duration connectTimeout;

    @Value("${http.client.read-timeout:45s}")
    private Duration readTimeout;

    @Value("${http.client.proxy-host:}")
    private String proxyHost;

    @Value("${http.client.proxy-port:0}")
    private int proxyPort;

    @Bean
    public RestTemplate restTemplate() {
        Timeout connectTimeoutValue = Timeout.of(connectTimeout.toMillis(), TimeUnit.MILLISECONDS);
        Timeout readTimeoutValue = Timeout.of(readTimeout.toMillis(), TimeUnit.MILLISECONDS);

        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(connectTimeoutValue)
            .setResponseTimeout(readTimeoutValue)
            .build();

        // Socket configuration with TCP keep-alive for reliability
        SocketConfig socketConfig = SocketConfig.custom()
            .setSoKeepAlive(true)
            .setTcpNoDelay(true)
            .setSoReuseAddress(true)
            .build();

        // Connection pooling for better resource management
        PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
        connManager.setDefaultSocketConfig(socketConfig);
        connManager.setMaxTotal(20);
        connManager.setDefaultMaxPerRoute(10);

        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create()
            .setConnectionManager(connManager)
            .setDefaultRequestConfig(requestConfig)
            .useSystemProperties();

        // Enable system proxy selector for enterprise/corporate networks
        if (StringUtils.hasText(proxyHost) && proxyPort > 0) {
            log.info("HTTP client configured with proxy: {}:{}", proxyHost, proxyPort);
            httpClientBuilder.setProxy(new HttpHost("http", proxyHost, proxyPort));
        } else {
            log.info("HTTP client using system proxy selector with connection pooling");
        }

        HttpClient httpClient = httpClientBuilder.build();
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setConnectionRequestTimeout((int) readTimeout.toMillis());

        return new RestTemplate(requestFactory);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}