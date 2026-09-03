package com.vitialert.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Seguridad minima del prototipo, desactivada por defecto para no romper la maqueta.
 *
 * <p>No se usa OAuth para el ESP32: solo una clave por nodo en la cabecera
 * {@code X-Node-Key}. Los endpoints administrativos admiten una clave global en
 * {@code X-Admin-Key}. El backend nunca guarda credenciales de WiFi.</p>
 */
@ConfigurationProperties(prefix = "vitialert.security")
public record SecurityProperties(
        @DefaultValue("false") boolean nodeKeyEnabled,
        @DefaultValue("false") boolean adminKeyEnabled,
        @DefaultValue("") String adminKey
) {
}
