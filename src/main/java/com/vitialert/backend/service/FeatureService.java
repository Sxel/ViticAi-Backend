package com.vitialert.backend.service;

import com.vitialert.backend.config.FeatureProperties;
import com.vitialert.backend.domain.Node;
import com.vitialert.backend.domain.TelemetryReading;
import com.vitialert.backend.dto.FeatureVector;
import com.vitialert.backend.repository.TelemetryReadingRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Construccion de features temporales a partir de la telemetria IoT.
 *
 * <p><b>Correccion conceptual respecto del prototipo Python.</b> El {@code server.py}
 * actual usa {@code shift(1)}, {@code shift(3)}, {@code shift(6)} y {@code shift(24)},
 * que son desplazamientos por POSICION de fila. Como el ESP32 transmite cada pocos
 * segundos, {@code shift(1)} no es "hace una hora" sino "la observacion anterior".
 * Aca los lags se resuelven SIEMPRE por timestamp real: se busca la observacion mas
 * proxima a {@code t - Xh} dentro de una tolerancia, y si no existe el valor es
 * {@code null} (nunca cero).</p>
 */
@Service
public class FeatureService {

    private final TelemetryReadingRepository telemetryReadingRepository;
    private final IrrigationEventService irrigationEventService;
    private final FeatureProperties properties;

    public FeatureService(TelemetryReadingRepository telemetryReadingRepository,
                          IrrigationEventService irrigationEventService,
                          FeatureProperties properties) {
        this.telemetryReadingRepository = telemetryReadingRepository;
        this.irrigationEventService = irrigationEventService;
        this.properties = properties;
    }

    /**
     * Calcula el vector de features IoT para un nodo en un instante dado.
     *
     * @param node      nodo
     * @param at        instante de referencia (normalmente el timestamp de la ultima lectura)
     * @param reference lectura de referencia; si es null se toma la mas reciente hasta {@code at}
     */
    @Transactional(readOnly = true)
    public FeatureVector computeFeatures(Node node, Instant at, TelemetryReading reference) {
        Long nodeId = node.getId();

        TelemetryReading current = reference;
        if (current == null) {
            current = nearestSoilReading(nodeId, at, Duration.ofHours(1)).orElse(null);
        }

        Double soilNow = current == null ? null : current.getHumedadSueloPct();

        Double lag1h = soilAt(nodeId, at.minus(Duration.ofHours(1)), Duration.ofHours(1));
        Double lag3h = soilAt(nodeId, at.minus(Duration.ofHours(3)), Duration.ofHours(3));
        Double lag6h = soilAt(nodeId, at.minus(Duration.ofHours(6)), Duration.ofHours(6));
        Double lag12h = soilAt(nodeId, at.minus(Duration.ofHours(12)), Duration.ofHours(12));
        Double lag24h = soilAt(nodeId, at.minus(Duration.ofHours(24)), Duration.ofHours(24));

        Double slope3h = slope(soilNow, lag3h, 3.0);
        Double slope12h = slope(soilNow, lag12h, 12.0);

        Double mean24h = telemetryReadingRepository.averageSoilMoisture(
                nodeId, at.minus(Duration.ofHours(24)), at);

        BigDecimal irrigation1h = irrigationVolume(nodeId, at, Duration.ofHours(1));
        BigDecimal irrigation24h = irrigationVolume(nodeId, at, Duration.ofHours(24));

        return new FeatureVector(
                node.getExternalId(),
                at,
                soilNow,
                lag1h,
                lag3h,
                lag6h,
                lag24h,
                slope3h,
                slope12h,
                mean24h,
                current == null ? null : current.getTemperaturaAmbienteC(),
                current == null ? null : current.getHumedadRelativaPct(),
                current == null ? null : current.getVelocidadVientoKmh(),
                current == null ? null : current.getCaudalLMin(),
                irrigation1h,
                irrigation24h);
    }

    /**
     * Humedad de suelo en el instante objetivo, resuelta por timestamp real.
     *
     * @return null si no hay observacion dentro de la tolerancia (dato faltante legitimo)
     */
    @Transactional(readOnly = true)
    public Double soilAt(Long nodeId, Instant target, Duration lag) {
        return nearestSoilReading(nodeId, target, lag)
                .map(TelemetryReading::getHumedadSueloPct)
                .orElse(null);
    }

    /**
     * Observacion con humedad de suelo mas proxima al instante objetivo.
     *
     * <p>La tolerancia efectiva es {@code min(maxLagToleranceMinutes, 25% del lag)}: sin
     * ese limite proporcional un lag de 1 h podria resolverse con una lectura de hace
     * 90 minutos y la feature dejaria de significar lo que dice su nombre.</p>
     */
    @Transactional(readOnly = true)
    public Optional<TelemetryReading> nearestSoilReading(Long nodeId, Instant target, Duration lag) {
        Duration tolerance = toleranceFor(lag);
        Instant lower = target.minus(tolerance);
        Instant upper = target.plus(tolerance);

        List<TelemetryReading> before = telemetryReadingRepository
                .findSoilNearestBefore(nodeId, target, lower, PageRequest.of(0, 1));
        List<TelemetryReading> after = telemetryReadingRepository
                .findSoilNearestAfter(nodeId, target, upper, PageRequest.of(0, 1));

        if (before.isEmpty() && after.isEmpty()) {
            return Optional.empty();
        }
        if (before.isEmpty()) {
            return Optional.of(after.get(0));
        }
        if (after.isEmpty()) {
            return Optional.of(before.get(0));
        }

        long distBefore = Math.abs(Duration.between(before.get(0).getTimestampReceived(), target).toMillis());
        long distAfter = Math.abs(Duration.between(after.get(0).getTimestampReceived(), target).toMillis());
        return Optional.of(distBefore <= distAfter ? before.get(0) : after.get(0));
    }

    Duration toleranceFor(Duration lag) {
        long maxMinutes = properties.maxLagToleranceMinutes();
        long proportional = Math.max(1L, lag.toMinutes() / 4);
        return Duration.ofMinutes(Math.min(maxMinutes, proportional));
    }

    /** Pendiente en puntos porcentuales por hora. Null si falta alguno de los extremos. */
    private static Double slope(Double current, Double past, double hours) {
        if (current == null || past == null || hours <= 0) {
            return null;
        }
        return (current - past) / hours;
    }

    /**
     * Volumen regado en la ventana. Devuelve null cuando el nodo no tiene ninguna lectura
     * en la ventana: en ese caso "0 litros" no es un dato, es ausencia de informacion.
     */
    private BigDecimal irrigationVolume(Long nodeId, Instant at, Duration window) {
        Instant from = at.minus(window);
        if (telemetryReadingRepository.countInRange(nodeId, from, at) == 0) {
            return null;
        }
        BigDecimal applied = irrigationEventService.appliedVolume(nodeId, from, at);
        return applied == null ? BigDecimal.ZERO : applied;
    }
}
