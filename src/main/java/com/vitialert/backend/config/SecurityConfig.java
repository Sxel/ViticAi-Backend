package com.vitialert.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitialert.backend.repository.NodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Seguridad opcional del prototipo, sin Spring Security.
 *
 * <ul>
 *   <li>{@code X-Node-Key} sobre {@code /api/data}: la clave se guarda por nodo en
 *       {@code node.api_key}. Desactivada por defecto para no romper la maqueta.</li>
 *   <li>{@code X-Admin-Key} sobre {@code /api/admin/**}: clave unica configurable.</li>
 * </ul>
 *
 * <p>El backend no almacena en ningun momento credenciales de WiFi del nodo.</p>
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public FilterRegistrationBean<ApiKeyFilter> nodeKeyFilter(SecurityProperties properties,
                                                              NodeRepository nodeRepository,
                                                              ObjectMapper objectMapper) {
        ApiKeyFilter filter = new ApiKeyFilter("X-Node-Key",
                key -> nodeRepository.findByApiKey(key).isPresent(),
                objectMapper);

        FilterRegistrationBean<ApiKeyFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/data");
        registration.setOrder(1);
        registration.setEnabled(properties.nodeKeyEnabled());

        log.info("Autenticacion por clave de nodo (X-Node-Key): {}",
                properties.nodeKeyEnabled() ? "ACTIVADA" : "desactivada");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<ApiKeyFilter> adminKeyFilter(SecurityProperties properties,
                                                               ObjectMapper objectMapper) {
        String expected = properties.adminKey();
        ApiKeyFilter filter = new ApiKeyFilter("X-Admin-Key",
                key -> expected != null && !expected.isBlank() && expected.equals(key),
                objectMapper);

        FilterRegistrationBean<ApiKeyFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/admin/*");
        registration.setOrder(2);
        registration.setEnabled(properties.adminKeyEnabled());

        log.info("Autenticacion de endpoints administrativos (X-Admin-Key): {}",
                properties.adminKeyEnabled() ? "ACTIVADA" : "desactivada");
        return registration;
    }
}
