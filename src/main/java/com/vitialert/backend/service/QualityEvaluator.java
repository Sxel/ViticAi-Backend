package com.vitialert.backend.service;

import com.vitialert.backend.config.QualityProperties;
import com.vitialert.backend.domain.Node;
import com.vitialert.backend.domain.QualityFlag;
import com.vitialert.backend.domain.TelemetryReading;
import com.vitialert.backend.dto.TelemetryRequest;
import com.vitialert.backend.repository.TelemetryReadingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Controles de sensor del punto 16 del alcance.
 *
 * <p>Estos chequeos NUNCA se convierten en decisiones de riego: solo producen
 * {@link QualityFlag} y advertencias, para que el dataset conserve la trazabilidad
 * de la confiabilidad de cada dato.</p>
 */
@Service
public class QualityEvaluator {

    private static final Logger log = LoggerFactory.getLogger(QualityEvaluator.class);

    private final TelemetryReadingRepository telemetryReadingRepository;
    private final QualityProperties properties;

    public QualityEvaluator(TelemetryReadingRepository telemetryReadingRepository,
                            QualityProperties properties) {
        this.telemetryReadingRepository = telemetryReadingRepository;
        this.properties = properties;
    }

    /**
     * @param request payload recibido
     * @param node    nodo ya resuelto
     * @param now     instante de recepcion asignado por el backend
     */
    public QualityAssessment evaluate(TelemetryRequest request, Node node, Instant now) {
        List<String> notes = new ArrayList<>();
        boolean missing = false;
        boolean sensorError = false;
        boolean outOfRange = false;
        boolean suspect = false;

        // --- Datos faltantes -------------------------------------------------
        if (request.temperaturaAmbienteC() == null) {
            notes.add("MISSING:temperatura_ambiente_c");
            missing = true;
        }
        if (request.humedadRelativaPct() == null) {
            notes.add("MISSING:humedad_relativa_pct");
            missing = true;
        }
        if (request.humedadSueloPct() == null) {
            notes.add("MISSING:humedad_suelo_pct");
            missing = true;
        }
        if (request.humedadSueloRaw() == null) {
            notes.add("MISSING:humedad_suelo_raw");
            missing = true;
        }
        if (request.valvulaAbiertaActual() == null) {
            notes.add("MISSING:valvula_abierta_actual");
            missing = true;
        }
        if (request.decisionRiegoLocal() == null) {
            notes.add("MISSING:decision_riego_local");
            missing = true;
        }

        // --- ADC pegado a un extremo ----------------------------------------
        Integer raw = request.humedadSueloRaw();
        if (raw != null && (raw <= properties.adcMin() || raw >= properties.adcMax())) {
            notes.add("SENSOR_ERROR:humedad_suelo_raw=" + raw + " (ADC en el extremo del rango)");
            sensorError = true;
        }

        // --- Fuera del rango operativo plausible (pero fisicamente posible) --
        Double temp = request.temperaturaAmbienteC();
        if (temp != null && (temp < properties.plausibleTempMinC() || temp > properties.plausibleTempMaxC())) {
            notes.add("OUT_OF_RANGE:temperatura_ambiente_c=" + temp);
            outOfRange = true;
        }
        Double wind = request.velocidadVientoKmh();
        if (wind != null && wind > properties.plausibleWindMaxKmh()) {
            notes.add("OUT_OF_RANGE:velocidad_viento_kmh=" + wind);
            outOfRange = true;
        }

        // --- Coherencia hidraulica ------------------------------------------
        BigDecimal caudal = request.caudalLMin();
        Boolean valvula = request.valvulaAbiertaActual();
        BigDecimal noiseThreshold = BigDecimal.valueOf(properties.flowNoiseThresholdLMin());

        if (caudal != null && valvula != null) {
            boolean flowing = caudal.compareTo(noiseThreshold) > 0;
            if (flowing && !valvula) {
                notes.add("SUSPECT:caudal=" + caudal.toPlainString() + " L/min con la valvula cerrada (posible fuga)");
                suspect = true;
            } else if (!flowing && valvula) {
                notes.add("SUSPECT:valvula abierta sin caudal (posible falta de suministro u obstruccion)");
                suspect = true;
            }
        }

        // --- Sensor de humedad congelado -------------------------------------
        if (properties.frozenSensorEnabled() && raw != null && node.getId() != null) {
            Optional<String> frozenNote = frozenSensorNote(node.getId(), raw, now);
            if (frozenNote.isPresent()) {
                notes.add(frozenNote.get());
                suspect = true;
            }
        }

        QualityFlag flag;
        if (sensorError) {
            flag = QualityFlag.SENSOR_ERROR;
        } else if (outOfRange) {
            flag = QualityFlag.OUT_OF_RANGE;
        } else if (missing) {
            flag = QualityFlag.MISSING;
        } else if (suspect) {
            flag = QualityFlag.SUSPECT;
        } else {
            flag = QualityFlag.VALID;
        }

        String joined = notes.isEmpty() ? null : truncate(String.join(" | ", notes));
        if (flag != QualityFlag.VALID) {
            log.warn("Calidad de dato degradada nodo={} flag={} detalle={}", node.getExternalId(), flag, joined);
        }
        return new QualityAssessment(flag, joined);
    }

    private Optional<String> frozenSensorNote(Long nodeId, Integer raw, Instant now) {
        Duration threshold = Duration.ofMinutes(properties.frozenSensorMinutes());

        List<TelemetryReading> different =
                telemetryReadingRepository.findLastWithDifferentRaw(nodeId, raw, PageRequest.of(0, 1));

        Instant lastChange;
        if (!different.isEmpty()) {
            lastChange = different.get(0).getTimestampReceived();
        } else {
            // Nunca vario: se mide desde la primera lectura conocida del nodo.
            List<TelemetryReading> oldest = telemetryReadingRepository.findOldest(nodeId, PageRequest.of(0, 1));
            lastChange = oldest.isEmpty() ? null : oldest.get(0).getTimestampReceived();
        }
        if (lastChange == null) {
            return Optional.empty();
        }
        Duration elapsed = Duration.between(lastChange, now);
        if (elapsed.compareTo(threshold) > 0) {
            return Optional.of("SUSPECT:humedad_suelo_raw sin cambios desde hace "
                    + elapsed.toMinutes() + " min (posible sensor congelado)");
        }
        return Optional.empty();
    }

    private static String truncate(String value) {
        return value.length() <= 500 ? value : value.substring(0, 497) + "...";
    }
}
