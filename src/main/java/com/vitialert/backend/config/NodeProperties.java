package com.vitialert.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param autoRegister        si un {@code nodo_id} desconocido se da de alta solo (necesario para
 *                            que la maqueta actual funcione sin configuracion previa)
 * @param offlineAfterMinutes minutos sin telemetria a partir de los cuales el nodo se considera offline
 */
@ConfigurationProperties(prefix = "vitialert.node")
public record NodeProperties(
        @DefaultValue("true") boolean autoRegister,
        @DefaultValue("10") long offlineAfterMinutes
) {
}
