package com.vitialert.backend.service;

import com.vitialert.backend.TestSupport;
import com.vitialert.backend.domain.Node;
import com.vitialert.backend.dto.NodeStatusDto;
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

import static org.assertj.core.api.Assertions.assertThat;

/** Deteccion de nodo offline segun vitialert.node.offline-after-minutes (10 minutos por defecto). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NodeStatusTest {

    @Autowired
    private TelemetryService telemetryService;

    @Autowired
    private IrrigationEventService irrigationEventService;

    @Autowired
    private TelemetryReadingRepository telemetryReadingRepository;

    @Autowired
    private NodeRepository nodeRepository;

    @Test
    void marcaElNodoComoOnlineCuandoLaUltimaLecturaEsReciente() {
        Node node = nodeRepository.save(new Node("status-1"));
        Instant recent = Instant.now().minus(Duration.ofMinutes(2));
        telemetryReadingRepository.save(TestSupport.reading(node, recent, 33.0));

        NodeStatusDto status = telemetryService.buildStatus(node);

        assertThat(status.nodeId()).isEqualTo("status-1");
        assertThat(status.online()).isTrue();
        assertThat(status.lastSeen()).isEqualTo(recent);
        assertThat(status.offlineAfterMinutes()).isEqualTo(10L);
        assertThat(status.latest()).isNotNull();
        assertThat(status.latest().humedadSueloPct()).isEqualTo(33.0);
        assertThat(status.currentIrrigation()).isNull();
    }

    @Test
    void marcaElNodoComoOfflineCuandoNoHayComunicacionRecientemente() {
        Node node = nodeRepository.save(new Node("status-2"));
        telemetryReadingRepository.save(
                TestSupport.reading(node, Instant.now().minus(Duration.ofMinutes(45)), 33.0));

        NodeStatusDto status = telemetryService.buildStatus(node);

        assertThat(status.online()).isFalse();
        assertThat(status.lastSeen()).isNotNull();
    }

    @Test
    void marcaElNodoComoOfflineCuandoNuncaEnvioTelemetria() {
        Node node = nodeRepository.save(new Node("status-3"));

        NodeStatusDto status = telemetryService.buildStatus(node);

        assertThat(status.online()).isFalse();
        assertThat(status.lastSeen()).isNull();
        assertThat(status.latest()).isNull();
    }

    @Test
    void exponeElRiegoEnCursoDentroDelEstado() {
        Node node = nodeRepository.save(new Node("status-4"));
        Instant now = Instant.now().minus(Duration.ofMinutes(1));
        var reading = telemetryReadingRepository.save(
                TestSupport.reading(node, now, 33.0, true, new BigDecimal("50.000")));
        irrigationEventService.processReading(node, reading);

        NodeStatusDto status = telemetryService.buildStatus(node);

        assertThat(status.online()).isTrue();
        assertThat(status.currentIrrigation()).isNotNull();
        assertThat(status.currentIrrigation().estado()).isEqualTo("OPEN");
        assertThat(status.currentIrrigation().volumenInicialL()).isEqualByComparingTo("50.000");
    }
}
