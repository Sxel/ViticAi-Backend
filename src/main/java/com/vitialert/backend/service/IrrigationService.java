package com.vitialert.backend.service;

import com.vitialert.backend.domain.IrrigationEvent;
import com.vitialert.backend.domain.IrrigationEventStatus;
import com.vitialert.backend.domain.Node;
import com.vitialert.backend.domain.TelemetryReading;
import com.vitialert.backend.repository.IrrigationEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Reconstruye los periodos reales de riego a partir de las transiciones de la electrovalvula.
 *
 * <pre>
 * valvula false -&gt; true  =&gt; inicio del evento
 * valvula true  -&gt; false =&gt; fin del evento
 * </pre>
 *
 * <p>Solo se consulta el evento abierto del nodo, que es a lo sumo uno (garantizado por un
 * indice unico parcial en la base): la ingesta de un POST nunca recorre el historico.</p>
 *
 * <p>Las repeticiones del mismo estado son ruido: el ESP32 informa el estado de la valvula en
 * CADA POST, no solo cuando cambia.</p>
 */
@Service
public class IrrigationService {

    private static final Logger log = LoggerFactory.getLogger(IrrigationService.class);

    private final IrrigationEventRepository irrigationEventRepository;

    public IrrigationService(IrrigationEventRepository irrigationEventRepository) {
        this.irrigationEventRepository = irrigationEventRepository;
    }

    /** Aplica la lectura recien persistida sobre la maquina de estados del riego. */
    @Transactional
    public Optional<IrrigationEvent> processReading(Node node, TelemetryReading reading) {
        if (reading.getValvulaAbiertaActual() == null) {
            // Sin estado de valvula no se puede inferir la transicion. La lectura ya quedo
            // marcada MISSING y el evento en curso se mantiene tal cual.
            return Optional.empty();
        }

        Optional<IrrigationEvent> open = findCurrent(node.getId());
        boolean valveOpen = reading.isValveOpen();

        if (valveOpen && open.isEmpty()) {
            IrrigationEvent saved = irrigationEventRepository.save(
                    new IrrigationEvent(node, reading.getTimestampReceived(), reading.getVolumenTotalL()));
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
}
