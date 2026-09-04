package com.vitialert.backend.service;

import com.vitialert.backend.domain.IrrigationEvent;
import com.vitialert.backend.domain.IrrigationEventStatus;
import com.vitialert.backend.domain.Node;
import com.vitialert.backend.domain.TelemetryReading;
import com.vitialert.backend.dto.IrrigationEventDto;
import com.vitialert.backend.dto.PageResponse;
import com.vitialert.backend.mapper.IrrigationEventMapper;
import com.vitialert.backend.repository.IrrigationEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Reconstruye los periodos reales de riego a partir de las transiciones de la
 * electrovalvula informadas por el ESP32.
 *
 * <p>Solo se consulta el evento abierto del nodo (a lo sumo uno): la ingesta de un POST
 * nunca recorre el historico.</p>
 */
@Service
public class IrrigationEventService {

    private static final Logger log = LoggerFactory.getLogger(IrrigationEventService.class);

    private final IrrigationEventRepository irrigationEventRepository;
    private final IrrigationEventMapper irrigationEventMapper;

    public IrrigationEventService(IrrigationEventRepository irrigationEventRepository,
                                  IrrigationEventMapper irrigationEventMapper) {
        this.irrigationEventRepository = irrigationEventRepository;
        this.irrigationEventMapper = irrigationEventMapper;
    }

    /**
     * Aplica la lectura recien persistida sobre la maquina de estados del riego.
     *
     * @return el evento afectado (abierto o cerrado) si hubo transicion
     */
    @Transactional
    public Optional<IrrigationEvent> processReading(Node node, TelemetryReading reading) {
        if (reading.getValvulaAbiertaActual() == null) {
            // Sin estado de valvula no se puede decidir la transicion: la lectura ya quedo
            // marcada como MISSING y el evento en curso se mantiene tal cual.
            return Optional.empty();
        }

        Optional<IrrigationEvent> open = findCurrent(node.getId());
        boolean valveOpen = reading.isValveOpen();

        if (valveOpen && open.isEmpty()) {
            IrrigationEvent event = new IrrigationEvent(node,
                    reading.getTimestampReceived(),
                    reading.getVolumenTotalL());
            IrrigationEvent saved = irrigationEventRepository.save(event);
            log.info("Riego iniciado nodo={} eventoId={} volumenInicial={} L",
                    node.getExternalId(), saved.getId(), reading.getVolumenTotalL());
            return Optional.of(saved);
        }

        if (!valveOpen && open.isPresent()) {
            IrrigationEvent event = open.get();
            event.close(reading.getTimestampReceived(), reading.getVolumenTotalL());
            IrrigationEvent saved = irrigationEventRepository.save(event);
            if (saved.getEstado() == IrrigationEventStatus.CLOSED_WITH_WARNING) {
                log.warn("Riego finalizado con advertencia nodo={} eventoId={} detalle={}",
                        node.getExternalId(), saved.getId(), saved.getObservaciones());
            } else {
                log.info("Riego finalizado nodo={} eventoId={} duracion={} s volumenAplicado={} L",
                        node.getExternalId(), saved.getId(), saved.getDuracionSegundos(),
                        saved.getVolumenAplicadoL());
            }
            return Optional.of(saved);
        }

        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<IrrigationEvent> findCurrent(Long nodeId) {
        List<IrrigationEvent> open = irrigationEventRepository.findByStatus(
                nodeId, IrrigationEventStatus.OPEN, PageRequest.of(0, 1));
        return open.isEmpty() ? Optional.empty() : Optional.of(open.get(0));
    }

    @Transactional(readOnly = true)
    public Page<IrrigationEvent> findRange(Long nodeId, Instant from, Instant to, Pageable pageable) {
        return irrigationEventRepository.findRange(nodeId, from, to, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<IrrigationEventDto> findCurrentDto(Long nodeId) {
        return findCurrent(nodeId).map(irrigationEventMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<IrrigationEventDto> findRangeDto(Long nodeId,
                                                         Instant from,
                                                         Instant to,
                                                         Pageable pageable) {
        Page<IrrigationEvent> events = irrigationEventRepository.findRange(nodeId, from, to, pageable);
        return PageResponse.of(events, irrigationEventMapper::toDto);
    }

    /**
     * Volumen regado en (from, to] segun los eventos ya cerrados.
     *
     * @return null si no hay ningun evento cerrado en la ventana
     */
    @Transactional(readOnly = true)
    public BigDecimal appliedVolume(Long nodeId, Instant from, Instant to) {
        return irrigationEventRepository.sumAppliedVolume(nodeId, from, to);
    }
}
