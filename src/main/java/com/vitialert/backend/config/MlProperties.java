package com.vitialert.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Integracion con la API Python de inferencia.
 *
 * <p>Desactivada por defecto: mientras {@code enabled=false} el backend no calcula
 * features en el camino caliente ni llama a ningun servicio externo, y la valvula
 * sigue gobernada por {@code decision_riego_local}.</p>
 *
 * <p>El modelo (.pkl) NUNCA se carga desde Java: Python es responsable de entrenar,
 * evaluar, serializar e inferir.</p>
 */
@ConfigurationProperties(prefix = "vitialert.ml")
public record MlProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("http://localhost:8002") String baseUrl,
        @DefaultValue("/predict") String predictPath,
        @DefaultValue("1000") int connectTimeoutMs,
        @DefaultValue("1500") int readTimeoutMs
) {
}
