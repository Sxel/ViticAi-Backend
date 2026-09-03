package com.vitialert.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param maxLagToleranceMinutes tolerancia maxima al buscar la observacion mas proxima a
 *                               {@code t - Xh}. La tolerancia efectiva es
 *                               {@code min(maxLagToleranceMinutes, 25% del lag)} para que un lag
 *                               de 1 h no se resuelva con una lectura de hace 90 minutos.
 */
@ConfigurationProperties(prefix = "vitialert.features")
public record FeatureProperties(
        @DefaultValue("30") long maxLagToleranceMinutes
) {
}
