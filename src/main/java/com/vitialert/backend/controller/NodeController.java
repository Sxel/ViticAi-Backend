package com.vitialert.backend.controller;

import com.vitialert.backend.domain.Granularity;
import com.vitialert.backend.domain.Node;
import com.vitialert.backend.dto.AggregatedTelemetryDto;
import com.vitialert.backend.dto.DecisionRecordDto;
import com.vitialert.backend.dto.FeatureVector;
import com.vitialert.backend.dto.IrrigationEventDto;
import com.vitialert.backend.dto.NodeDto;
import com.vitialert.backend.dto.NodeStatusDto;
import com.vitialert.backend.dto.PageResponse;
import com.vitialert.backend.dto.TelemetryReadingDto;
import com.vitialert.backend.exception.ResourceNotFoundException;
import com.vitialert.backend.mapper.NodeMapper;
import com.vitialert.backend.service.AggregationService;
import com.vitialert.backend.service.FeatureService;
import com.vitialert.backend.service.IrrigationDecisionService;
import com.vitialert.backend.service.IrrigationEventService;
import com.vitialert.backend.service.NodeService;
import com.vitialert.backend.service.TelemetryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** Consultas de nodos, telemetria, riegos, decisiones y features. */
@RestController
@RequestMapping("/api/nodes")
@Tag(name = "Nodos", description = "Consulta del estado y del historico de cada nodo")
public class NodeController {

    private static final int MAX_PAGE_SIZE = 500;

    private final NodeService nodeService;
    private final TelemetryService telemetryService;
    private final IrrigationEventService irrigationEventService;
    private final FeatureService featureService;
    private final AggregationService aggregationService;
    private final IrrigationDecisionService irrigationDecisionService;
    private final NodeMapper nodeMapper;

    public NodeController(NodeService nodeService,
                          TelemetryService telemetryService,
                          IrrigationEventService irrigationEventService,
                          FeatureService featureService,
                          AggregationService aggregationService,
                          IrrigationDecisionService irrigationDecisionService,
                          NodeMapper nodeMapper) {
        this.nodeService = nodeService;
        this.telemetryService = telemetryService;
        this.irrigationEventService = irrigationEventService;
        this.featureService = featureService;
        this.aggregationService = aggregationService;
        this.irrigationDecisionService = irrigationDecisionService;
        this.nodeMapper = nodeMapper;
    }

    @Operation(summary = "Lista todos los nodos registrados")
    @GetMapping
    public List<NodeDto> findAll() {
        return nodeService.findAll().stream().map(nodeMapper::toDto).toList();
    }

    @Operation(summary = "Detalle de un nodo")
    @GetMapping("/{nodeId}")
    public NodeDto findOne(@PathVariable String nodeId) {
        return nodeMapper.toDto(nodeService.requireByExternalId(nodeId));
    }

    @Operation(summary = "Ultima lectura recibida del nodo")
    @GetMapping("/{nodeId}/telemetry/latest")
    public TelemetryReadingDto latestTelemetry(@PathVariable String nodeId) {
        Node node = nodeService.requireByExternalId(nodeId);
        return telemetryService.findLatest(node.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "El nodo " + nodeId + " todavia no tiene lecturas."));
    }

    @Operation(summary = "Historico de telemetria paginado",
            description = "from y to admiten ISO-8601 (2026-09-03T10:00:00Z) o fecha suelta (2026-09-03). "
                    + "Por defecto se devuelven las ultimas 24 horas.")
    @GetMapping("/{nodeId}/telemetry")
    public PageResponse<TelemetryReadingDto> telemetry(@PathVariable String nodeId,
                                                       @RequestParam(required = false) String from,
                                                       @RequestParam(required = false) String to,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "100") int size) {
        Node node = nodeService.requireByExternalId(nodeId);
        Instant toInstant = RequestTimes.parseOrDefault(to, "to", Instant.now());
        Instant fromInstant = RequestTimes.parseOrDefault(from, "from", RequestTimes.defaultFrom(toInstant));

        return telemetryService.findRange(
                node.getId(), fromInstant, toInstant, pageable(page, size));
    }

    @Operation(summary = "Eventos de riego del nodo")
    @GetMapping("/{nodeId}/irrigation-events")
    public PageResponse<IrrigationEventDto> irrigationEvents(@PathVariable String nodeId,
                                                             @RequestParam(required = false) String from,
                                                             @RequestParam(required = false) String to,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "50") int size) {
        Node node = nodeService.requireByExternalId(nodeId);
        Instant toInstant = RequestTimes.parseOrDefault(to, "to", Instant.now());
        Instant fromInstant = RequestTimes.parseOrDefault(from, "from", RequestTimes.defaultFrom(toInstant));

        return irrigationEventService.findRangeDto(
                node.getId(), fromInstant, toInstant, pageable(page, size));
    }

    @Operation(summary = "Riego actualmente en curso, si existe")
    @GetMapping("/{nodeId}/irrigation-events/current")
    public IrrigationEventDto currentIrrigation(@PathVariable String nodeId) {
        Node node = nodeService.requireByExternalId(nodeId);
        return irrigationEventService.findCurrentDto(node.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "El nodo " + nodeId + " no tiene un riego en curso."));
    }

    @Operation(summary = "Estado operativo del nodo",
            description = "Marca el nodo como offline cuando no hay comunicacion desde hace mas de "
                    + "vitialert.node.offline-after-minutes minutos.")
    @GetMapping("/{nodeId}/status")
    public NodeStatusDto status(@PathVariable String nodeId) {
        return telemetryService.buildStatus(nodeService.requireByExternalId(nodeId));
    }

    @Operation(summary = "Decisiones de riego registradas")
    @GetMapping("/{nodeId}/decisions")
    public PageResponse<DecisionRecordDto> decisions(@PathVariable String nodeId,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "50") int size) {
        Node node = nodeService.requireByExternalId(nodeId);
        return irrigationDecisionService.findByNode(node.getId(), pageable(page, size));
    }

    @Operation(summary = "Features temporales IoT calculadas por timestamp real",
            description = "Los lags se resuelven buscando la observacion mas proxima a t - Xh. "
                    + "Un lag inexistente devuelve null, nunca cero.")
    @GetMapping("/{nodeId}/features")
    public FeatureVector features(@PathVariable String nodeId,
                                  @RequestParam(required = false) String at) {
        Node node = nodeService.requireByExternalId(nodeId);
        Instant instant = RequestTimes.parseOrDefault(at, "at", Instant.now());
        return featureService.computeFeatures(node, instant, null);
    }

    @Operation(summary = "Agregacion temporal de la telemetria",
            description = "Granularidades disponibles: FIVE_MINUTES, FIFTEEN_MINUTES, HOURLY.")
    @GetMapping("/{nodeId}/aggregations")
    public List<AggregatedTelemetryDto> aggregations(@PathVariable String nodeId,
                                                     @RequestParam(required = false) String from,
                                                     @RequestParam(required = false) String to,
                                                     @RequestParam(defaultValue = "HOURLY") Granularity granularity) {
        Node node = nodeService.requireByExternalId(nodeId);
        Instant toInstant = RequestTimes.parseOrDefault(to, "to", Instant.now());
        Instant fromInstant = RequestTimes.parseOrDefault(from, "from", RequestTimes.defaultFrom(toInstant));
        return aggregationService.aggregate(node.getId(), fromInstant, toInstant, granularity);
    }

    private static Pageable pageable(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("El parametro page no puede ser negativo.");
        }
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("El parametro size debe estar entre 1 y " + MAX_PAGE_SIZE + ".");
        }
        return PageRequest.of(page, size);
    }
}
