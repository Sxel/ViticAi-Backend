package com.vitialert.backend.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Granularidades soportadas por la capa de agregacion temporal.
 *
 * <p>El ESP32 transmite con frecuencia variable, por lo tanto NUNCA se asume que
 * un registro equivale a un minuto ni a una hora: los buckets se calculan siempre
 * a partir del timestamp real de cada lectura.</p>
 */
public enum Granularity {

    FIVE_MINUTES(Duration.ofMinutes(5)),
    FIFTEEN_MINUTES(Duration.ofMinutes(15)),
    HOURLY(Duration.ofHours(1));

    private final Duration duration;

    Granularity(Duration duration) {
        this.duration = duration;
    }

    public Duration duration() {
        return duration;
    }

    public long seconds() {
        return duration.getSeconds();
    }

    /** Trunca un instante al inicio del bucket que lo contiene (epoch UTC como origen). */
    public Instant floor(Instant instant) {
        long seconds = instant.getEpochSecond();
        long bucket = Math.floorDiv(seconds, seconds()) * seconds();
        return Instant.ofEpochSecond(bucket);
    }
}
