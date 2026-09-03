package com.vitialert.backend.service;

import com.vitialert.backend.TestSupport;
import com.vitialert.backend.domain.IrrigationEvent;
import com.vitialert.backend.domain.IrrigationEventStatus;
import com.vitialert.backend.domain.Node;
import com.vitialert.backend.domain.TelemetryReading;
import com.vitialert.backend.repository.NodeRepository;
import com.vitialert.backend.repository.TelemetryReadingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Criterio de exito 7: deteccion de apertura/cierre y calculo del consumo de agua. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IrrigationEventServiceTest {

    @Autowired
    private IrrigationEventService irrigationEventService;

    @Autowired
    private TelemetryReadingRepository telemetryReadingRepository;

    @Autowired
    private NodeRepository nodeRepository;

    private Node newNode(String externalId) {
        return nodeRepository.save(new Node(externalId));
    }

    private TelemetryReading save(Node node, Instant at, boolean valveOpen, String volume) {
        return telemetryReadingRepository.save(
                TestSupport.reading(node, at, 25.0, valveOpen, new BigDecimal(volume)));
    }

    @Test
    void abreUnEventoCuandoLaValvulaPasaDeCerradaAAbierta() {
        Node node = newNode("evt-1");
        Instant t0 = Instant.parse("2026-09-01T10:00:00Z");

        irrigationEventService.processReading(node, save(node, t0, false, "100.000"));
        assertThat(irrigationEventService.findCurrent(node.getId())).isEmpty();

        irrigationEventService.processReading(node, save(node, t0.plusSeconds(30), true, "100.000"));

        Optional<IrrigationEvent> current = irrigationEventService.findCurrent(node.getId());
        assertThat(current).isPresent();
        assertThat(current.get().getEstado()).isEqualTo(IrrigationEventStatus.OPEN);
        assertThat(current.get().getStartedAt()).isEqualTo(t0.plusSeconds(30));
        assertThat(current.get().getVolumenInicialL()).isEqualByComparingTo("100.000");
        assertThat(current.get().getEndedAt()).isNull();
    }

    @Test
    void cierraElEventoYCalculaVolumenDuracionYCaudalPromedio() {
        Node node = newNode("evt-2");
        Instant start = Instant.parse("2026-09-01T10:00:00Z");
        Instant end = start.plus(Duration.ofMinutes(5));

        irrigationEventService.processReading(node, save(node, start, true, "100.000"));
        irrigationEventService.processReading(node, save(node, start.plusSeconds(120), true, "115.000"));
        irrigationEventService.processReading(node, save(node, end, false, "137.500"));

        assertThat(irrigationEventService.findCurrent(node.getId())).isEmpty();

        IrrigationEvent event = irrigationEventService
                .findRange(node.getId(), start.minusSeconds(60), end.plusSeconds(60),
                        org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent().get(0);

        assertThat(event.getEstado()).isEqualTo(IrrigationEventStatus.CLOSED);
        assertThat(event.getEndedAt()).isEqualTo(end);
        assertThat(event.getDuracionSegundos()).isEqualTo(300L);
        assertThat(event.getVolumenAplicadoL()).isEqualByComparingTo("37.500");
        // 37.5 L en 5 minutos => 7.5 L/min
        assertThat(event.getCaudalPromedioLMin()).isEqualByComparingTo("7.500");
    }

    @Test
    void detectaElReinicioDelContadorDelEsp32YLoMarcaComoAdvertencia() {
        Node node = newNode("evt-3");
        Instant start = Instant.parse("2026-09-01T12:00:00Z");
        Instant end = start.plus(Duration.ofMinutes(2));

        irrigationEventService.processReading(node, save(node, start, true, "980.000"));
        // El nodo se reinicio: el contador acumulado vuelve a empezar desde cero.
        irrigationEventService.processReading(node, save(node, end, false, "12.000"));

        IrrigationEvent event = irrigationEventService
                .findRange(node.getId(), start.minusSeconds(60), end.plusSeconds(60),
                        org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent().get(0);

        assertThat(event.getEstado()).isEqualTo(IrrigationEventStatus.CLOSED_WITH_WARNING);
        assertThat(event.getVolumenAplicadoL()).isEqualByComparingTo("12.000");
        assertThat(event.getObservaciones()).contains("reiniciado");
    }

    @Test
    void noCreaEventosMientrasLaValvulaSigueCerrada() {
        Node node = newNode("evt-4");
        Instant t0 = Instant.parse("2026-09-01T14:00:00Z");

        irrigationEventService.processReading(node, save(node, t0, false, "10.000"));
        irrigationEventService.processReading(node, save(node, t0.plusSeconds(30), false, "10.000"));
        irrigationEventService.processReading(node, save(node, t0.plusSeconds(60), false, "10.000"));

        assertThat(irrigationEventService.findCurrent(node.getId())).isEmpty();
        assertThat(irrigationEventService
                .findRange(node.getId(), t0.minusSeconds(60), t0.plusSeconds(600),
                        org.springframework.data.domain.PageRequest.of(0, 10))
                .getTotalElements()).isZero();
    }

    @Test
    void mantieneUnUnicoEventoAbiertoMientrasLaValvulaSigueAbierta() {
        Node node = newNode("evt-5");
        Instant t0 = Instant.parse("2026-09-01T16:00:00Z");

        irrigationEventService.processReading(node, save(node, t0, true, "0.000"));
        irrigationEventService.processReading(node, save(node, t0.plusSeconds(20), true, "2.500"));
        irrigationEventService.processReading(node, save(node, t0.plusSeconds(40), true, "5.000"));

        assertThat(irrigationEventService
                .findRange(node.getId(), t0.minusSeconds(60), t0.plusSeconds(600),
                        org.springframework.data.domain.PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(1);
        assertThat(irrigationEventService.findCurrent(node.getId())).isPresent();
    }
}
