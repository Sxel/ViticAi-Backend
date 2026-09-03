package com.vitialert.backend.service;

import com.vitialert.backend.config.AggregationProperties;
import com.vitialert.backend.domain.Granularity;
import com.vitialert.backend.domain.TelemetryReading;
import com.vitialert.backend.dto.AggregatedTelemetryDto;
import com.vitialert.backend.repository.TelemetryReadingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Capa de agregacion temporal.
 *
 * <p>El ESP32 transmite con una frecuencia que no es constante ni conocida, por lo que
 * jamas se asume "una fila = un minuto" ni "una fila = una hora". Los buckets se calculan
 * truncando el timestamp real de cada lectura al inicio del intervalo (epoch UTC como
 * origen), y cada fila expone su {@code sample_count} para que el analisis posterior pueda
 * descartar buckets con poca cobertura.</p>
 *
 * <p>El tiempo de valvula abierta se integra sobre los intervalos reales entre lecturas
 * consecutivas, ignorando huecos mayores a {@code vitialert.aggregation.max-gap-seconds}
 * (esos huecos significan nodo offline, no riego continuo). El intervalo se atribuye al
 * bucket de la lectura anterior, que es la que declara el estado durante ese intervalo.</p>
 */
@Service
public class AggregationService {

    private final TelemetryReadingRepository telemetryReadingRepository;
    private final AggregationProperties properties;

    public AggregationService(TelemetryReadingRepository telemetryReadingRepository,
                              AggregationProperties properties) {
        this.telemetryReadingRepository = telemetryReadingRepository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<AggregatedTelemetryDto> aggregate(Long nodeId, Instant from, Instant to, Granularity granularity) {
        validateRange(from, to);

        List<TelemetryReading> readings = telemetryReadingRepository.findRangeAsc(nodeId, from, to);
        Map<Instant, Accumulator> buckets = new TreeMap<>();

        TelemetryReading previous = null;
        Accumulator previousBucket = null;
        long maxGap = properties.maxGapSeconds();

        for (TelemetryReading reading : readings) {
            Instant bucketStart = granularity.floor(reading.getTimestampReceived());
            Accumulator accumulator = buckets.computeIfAbsent(bucketStart, key -> new Accumulator());
            accumulator.add(reading);

            if (previous != null && previousBucket != null) {
                long gapSeconds = Duration.between(previous.getTimestampReceived(),
                        reading.getTimestampReceived()).getSeconds();
                if (gapSeconds > 0 && gapSeconds <= maxGap) {
                    if (previous.isValveOpen()) {
                        previousBucket.valveOpenSeconds += gapSeconds;
                    }
                    BigDecimal previousVolume = previous.getVolumenTotalL();
                    BigDecimal currentVolume = reading.getVolumenTotalL();
                    if (previousVolume != null && currentVolume != null) {
                        BigDecimal delta = currentVolume.subtract(previousVolume);
                        // Un delta negativo significa reinicio del contador del ESP32: se
                        // computa como 0 en lugar de inventar un consumo negativo. El bucket
                        // igual queda marcado como observado (0 L consumidos es un dato real,
                        // distinto de "no hay informacion").
                        previousBucket.addVolume(delta.signum() > 0 ? delta : BigDecimal.ZERO);
                    }
                }
            }

            previous = reading;
            previousBucket = accumulator;
        }

        List<AggregatedTelemetryDto> result = new ArrayList<>(buckets.size());
        for (Map.Entry<Instant, Accumulator> entry : buckets.entrySet()) {
            result.add(entry.getValue().toDto(entry.getKey()));
        }
        return result;
    }

    /** Igual que {@link #aggregate} pero indexado por instante de inicio de bucket. */
    @Transactional(readOnly = true)
    public Map<Instant, AggregatedTelemetryDto> aggregateIndexed(Long nodeId,
                                                                 Instant from,
                                                                 Instant to,
                                                                 Granularity granularity) {
        Map<Instant, AggregatedTelemetryDto> indexed = new LinkedHashMap<>();
        for (AggregatedTelemetryDto row : aggregate(nodeId, from, to, granularity)) {
            indexed.put(row.bucketStart(), row);
        }
        return indexed;
    }

    private void validateRange(Instant from, Instant to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Los parametros from y to son obligatorios.");
        }
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("El parametro from debe ser anterior a to.");
        }
        long days = Duration.between(from, to).toDays();
        if (days > properties.maxRangeDays()) {
            throw new IllegalArgumentException("El rango solicitado (" + days + " dias) supera el maximo de "
                    + properties.maxRangeDays() + " dias.");
        }
    }

    /** Acumulador mutable de un bucket. Vive solo durante el calculo. */
    private static final class Accumulator {

        private int sampleCount;

        private double tempSum;
        private int tempCount;
        private Double tempMax;

        private double humiditySum;
        private int humidityCount;

        private double soilSum;
        private int soilCount;
        private Double soilMin;
        private Double soilMax;

        private double windSum;
        private int windCount;
        private Double windMax;

        private BigDecimal flowSum = BigDecimal.ZERO;
        private int flowCount;
        private BigDecimal flowMax;

        private BigDecimal volumeUsed = BigDecimal.ZERO;
        private boolean volumeObserved;

        private long valveOpenSeconds;

        void add(TelemetryReading reading) {
            sampleCount++;

            Double temp = reading.getTemperaturaAmbienteC();
            if (temp != null) {
                tempSum += temp;
                tempCount++;
                tempMax = tempMax == null ? temp : Math.max(tempMax, temp);
            }

            Double humidity = reading.getHumedadRelativaPct();
            if (humidity != null) {
                humiditySum += humidity;
                humidityCount++;
            }

            Double soil = reading.getHumedadSueloPct();
            if (soil != null) {
                soilSum += soil;
                soilCount++;
                soilMin = soilMin == null ? soil : Math.min(soilMin, soil);
                soilMax = soilMax == null ? soil : Math.max(soilMax, soil);
            }

            Double wind = reading.getVelocidadVientoKmh();
            if (wind != null) {
                windSum += wind;
                windCount++;
                windMax = windMax == null ? wind : Math.max(windMax, wind);
            }

            BigDecimal flow = reading.getCaudalLMin();
            if (flow != null) {
                flowSum = flowSum.add(flow);
                flowCount++;
                flowMax = flowMax == null ? flow : flowMax.max(flow);
            }
        }

        void addVolume(BigDecimal delta) {
            volumeUsed = volumeUsed.add(delta);
            volumeObserved = true;
        }

        AggregatedTelemetryDto toDto(Instant bucketStart) {
            return new AggregatedTelemetryDto(
                    bucketStart,
                    sampleCount,
                    mean(tempSum, tempCount),
                    tempMax,
                    mean(humiditySum, humidityCount),
                    mean(soilSum, soilCount),
                    soilMin,
                    soilMax,
                    mean(windSum, windCount),
                    windMax,
                    flowCount == 0 ? null : flowSum.divide(BigDecimal.valueOf(flowCount), 3, RoundingMode.HALF_UP),
                    flowMax,
                    volumeObserved ? volumeUsed.setScale(3, RoundingMode.HALF_UP) : null,
                    valveOpenSeconds);
        }

        /** Null cuando no hubo ninguna muestra valida: nunca cero artificial. */
        private static Double mean(double sum, int count) {
            return count == 0 ? null : sum / count;
        }
    }
}
