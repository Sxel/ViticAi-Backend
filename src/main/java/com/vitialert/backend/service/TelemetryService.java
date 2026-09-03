package com.vitialert.backend.service;

import com.vitialert.backend.domain.IrrigationEvent;
import com.vitialert.backend.domain.Node;
import com.vitialert.backend.domain.TelemetryReading;
import com.vitialert.backend.dto.NodeStatusDto;
import com.vitialert.backend.dto.TelemetryRequest;
import com.vitialert.backend.dto.TelemetryResponse;
import com.vitialert.backend.mapper.IrrigationEventMapper;
import com.vitialert.backend.mapper.TelemetryMapper;
import com.vitialert.backend.repository.TelemetryReadingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Recepcion y consulta de telemetria.
 *
 * <p>El camino de ingesta esta acotado a proposito: resolver el nodo, evaluar calidad,
 * guardar la lectura, actualizar como mucho un evento de riego y registrar la decision.
 * No se recorre el historico, no se recalculan 24 horas y no se escribe ningun CSV, a
 * diferencia del prototipo Python. La persistencia y la respuesta al ESP32 son
 * prioritarias.</p>
 */
@Service
public class TelemetryService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private final TelemetryReadingRepository telemetryReadingRepository;
    private final NodeService nodeService;
    private final QualityEvaluator qualityEvaluator;
    private final IrrigationEventService irrigationEventService;
    private final IrrigationDecisionService irrigationDecisionService;
    private final TelemetryMapper telemetryMapper;
    private final IrrigationEventMapper irrigationEventMapper;

    public TelemetryService(TelemetryReadingRepository telemetryReadingRepository,
                            NodeService nodeService,
                            QualityEvaluator qualityEvaluator,
                            IrrigationEventService irrigationEventService,
                            IrrigationDecisionService irrigationDecisionService,
                            TelemetryMapper telemetryMapper,
                            IrrigationEventMapper irrigationEventMapper) {
        this.telemetryReadingRepository = telemetryReadingRepository;
        this.nodeService = nodeService;
        this.qualityEvaluator = qualityEvaluator;
        this.irrigationEventService = irrigationEventService;
        this.irrigationDecisionService = irrigationDecisionService;
        this.telemetryMapper = telemetryMapper;
        this.irrigationEventMapper = irrigationEventMapper;
    }

    /**
     * Procesa un POST del ESP32 y devuelve las ordenes para el nodo.
     *
     * <p>El timestamp lo genera el backend en UTC en el momento de la recepcion. Mas
     * adelante podra incorporarse un timestamp propio del sensor sin cambiar el contrato.</p>
     */
    @Transactional
    public TelemetryResponse ingest(TelemetryRequest request) {
        Instant receivedAt = Instant.now();

        Node node = nodeService.resolveForIngest(request.nodoId());
        QualityAssessment quality = qualityEvaluator.evaluate(request, node, receivedAt);

        TelemetryReading reading = telemetryMapper.toEntity(
                request, node, receivedAt, quality.flag(), quality.notes());
        reading = telemetryReadingRepository.save(reading);

        irrigationEventService.processReading(node, reading);
        DecisionOutcome outcome = irrigationDecisionService.decide(node, reading);

        log.info("Telemetria nodo={} ts={} humedadSuelo={}% viento={} km/h caudal={} L/min "
                        + "valvula={} decisionLocal={} decisionFinal={} calidad={}",
                node.getExternalId(),
                receivedAt,
                request.humedadSueloPct(),
                request.velocidadVientoKmh(),
                request.caudalLMin(),
                request.valvulaAbiertaActual(),
                request.decisionRiegoLocal(),
                outcome.decisionFinal(),
                quality.flag());

        return new TelemetryResponse(outcome.decisionFinal(), outcome.decisionFinal());
    }

    @Transactional(readOnly = true)
    public Optional<TelemetryReading> findLatest(Long nodeId) {
        List<TelemetryReading> latest = telemetryReadingRepository.findLatest(nodeId, PageRequest.of(0, 1));
        return latest.isEmpty() ? Optional.empty() : Optional.of(latest.get(0));
    }

    @Transactional(readOnly = true)
    public Page<TelemetryReading> findRange(Long nodeId, Instant from, Instant to, Pageable pageable) {
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("El parametro from debe ser anterior a to.");
        }
        return telemetryReadingRepository.findRange(nodeId, from, to, pageable);
    }

    /**
     * Estado operativo del nodo. Se considera offline cuando no hay comunicacion desde hace
     * mas de {@code vitialert.node.offline-after-minutes} minutos.
     */
    @Transactional(readOnly = true)
    public NodeStatusDto buildStatus(Node node) {
        Optional<TelemetryReading> latest = findLatest(node.getId());
        Optional<IrrigationEvent> current = irrigationEventService.findCurrent(node.getId());

        long offlineAfterMinutes = nodeService.offlineAfterMinutes();
        Instant lastSeen = latest.map(TelemetryReading::getTimestampReceived).orElse(null);
        boolean online = lastSeen != null
                && Duration.between(lastSeen, Instant.now()).toMinutes() < offlineAfterMinutes;

        return new NodeStatusDto(
                node.getExternalId(),
                lastSeen,
                online,
                offlineAfterMinutes,
                latest.map(telemetryMapper::toDto).orElse(null),
                current.map(irrigationEventMapper::toDto).orElse(null));
    }
}
