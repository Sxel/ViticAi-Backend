package com.vitialert.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param maxGapSeconds segundos maximos que se aceptan entre dos lecturas consecutivas al
 *                      integrar el tiempo de valvula abierta. Un hueco mayor significa que el
 *                      nodo estuvo offline y no se contabiliza como riego.
 * @param maxRangeDays  rango maximo consultable de una sola vez, para proteger la base
 */
@ConfigurationProperties(prefix = "vitialert.aggregation")
public record AggregationProperties(
        @DefaultValue("120") long maxGapSeconds,
        @DefaultValue("31") int maxRangeDays
) {
}
