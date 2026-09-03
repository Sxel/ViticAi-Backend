package com.vitialert.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Clientes HTTP hacia los servicios Python externos.
 *
 * <p>Se usan timeouts cortos y explicitos: ninguna integracion externa puede demorar la
 * respuesta al ESP32, que es prioritaria.</p>
 */
@Configuration
public class HttpClientsConfig {

    @Bean
    public RestClient satelliteRestClient(SatelliteProperties properties) {
        return build(properties.baseUrl(), properties.connectTimeoutMs(), properties.readTimeoutMs());
    }

    @Bean
    public RestClient weatherRestClient(WeatherProperties properties) {
        return build(properties.baseUrl(), properties.connectTimeoutMs(), properties.readTimeoutMs());
    }

    @Bean
    public RestClient mlRestClient(MlProperties properties) {
        return build(properties.baseUrl(), properties.connectTimeoutMs(), properties.readTimeoutMs());
    }

    private static RestClient build(String baseUrl, int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
