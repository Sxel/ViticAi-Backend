package com.vitialert.backend.service;

import com.vitialert.backend.TestSupport;
import com.vitialert.backend.domain.Node;
import com.vitialert.backend.dto.FeatureVector;
import com.vitialert.backend.repository.NodeRepository;
import com.vitialert.backend.repository.TelemetryReadingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Criterio de exito 8: los lags se calculan por tiempo real y NO por posicion de fila.
 *
 * <p>El escenario incluye mucho ruido de alta frecuencia alrededor de t. Si se usara
 * {@code shift(1)} como en el prototipo Python, el "lag de 1 hora" devolveria la lectura
 * de hace 30 segundos. Los tests verifican que devuelve la de hace una hora.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FeatureServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

    @Autowired
    private FeatureService featureService;

    @Autowired
    private TelemetryReadingRepository telemetryReadingRepository;

    @Autowired
    private NodeRepository nodeRepository;

    private Node buildScenario(String externalId) {
        Node node = nodeRepository.save(new Node(externalId));

        // Observaciones de referencia, una por cada lag que interesa.
        telemetryReadingRepository.save(TestSupport.reading(node, NOW.minus(Duration.ofHours(24)), 60.0));
        telemetryReadingRepository.save(TestSupport.reading(node, NOW.minus(Duration.ofHours(12)), 50.0));
        telemetryReadingRepository.save(TestSupport.reading(node, NOW.minus(Duration.ofHours(6)), 40.0));
        telemetryReadingRepository.save(TestSupport.reading(node, NOW.minus(Duration.ofHours(3)), 34.0));
        telemetryReadingRepository.save(TestSupport.reading(node, NOW.minus(Duration.ofHours(1)), 28.0));

        // Ruido de alta frecuencia: el ESP32 transmite cada 30 segundos.
        for (int i = 1; i <= 10; i++) {
            telemetryReadingRepository.save(
                    TestSupport.reading(node, NOW.minusSeconds(30L * i), 25.0 - i * 0.01));
        }
        telemetryReadingRepository.save(TestSupport.reading(node, NOW, 25.0));
        return node;
    }

    @Test
    void resuelveLosLagsPorTimestampRealYNoPorPosicionDeFila() {
        Node node = buildScenario("feat-1");

        FeatureVector features = featureService.computeFeatures(node, NOW, null);

        assertThat(features.soilMoisturePct()).isEqualTo(25.0);
        assertThat(features.soilMoistureLag1h()).isEqualTo(28.0);
        assertThat(features.soilMoistureLag3h()).isEqualTo(34.0);
        assertThat(features.soilMoistureLag6h()).isEqualTo(40.0);
        assertThat(features.soilMoistureLag24h()).isEqualTo(60.0);
    }

    @Test
    void calculaLasPendientesEnPuntosPorcentualesPorHora() {
        Node node = buildScenario("feat-2");

        FeatureVector features = featureService.computeFeatures(node, NOW, null);

        // (25 - 34) / 3 h = -3 pp/h ; (25 - 50) / 12 h = -2.0833 pp/h
        assertThat(features.soilMoistureSlope3h()).isCloseTo(-3.0, within(1e-9));
        assertThat(features.soilMoistureSlope12h()).isCloseTo(-25.0 / 12.0, within(1e-9));
    }

    @Test
    void calculaLaMediaDeLasUltimas24Horas() {
        Node node = nodeRepository.save(new Node("feat-3"));
        telemetryReadingRepository.save(TestSupport.reading(node, NOW.minus(Duration.ofHours(6)), 40.0));
        telemetryReadingRepository.save(TestSupport.reading(node, NOW.minus(Duration.ofHours(3)), 30.0));
        telemetryReadingRepository.save(TestSupport.reading(node, NOW, 20.0));
        // Fuera de la ventana: no debe influir.
        telemetryReadingRepository.save(TestSupport.reading(node, NOW.minus(Duration.ofHours(40)), 90.0));

        FeatureVector features = featureService.computeFeatures(node, NOW, null);

        assertThat(features.soilMoistureMean24h()).isCloseTo(30.0, within(1e-9));
    }

    @Test
    void devuelveNullCuandoNoExisteObservacionEnLaTolerancia() {
        Node node = nodeRepository.save(new Node("feat-4"));
        telemetryReadingRepository.save(TestSupport.reading(node, NOW, 20.0));
        // Existe una lectura vieja, pero lejos del instante t-24h buscado.
        telemetryReadingRepository.save(TestSupport.reading(node, NOW.minus(Duration.ofHours(19)), 55.0));

        FeatureVector features = featureService.computeFeatures(node, NOW, null);

        assertThat(features.soilMoistureLag1h()).isNull();
        assertThat(features.soilMoistureLag24h()).isNull();
        assertThat(features.soilMoistureSlope3h()).isNull();
        // Nunca se rellena con cero: el dato faltante debe llegar como faltante.
        assertThat(features.soilMoistureLag6h()).isNull();
    }

    @Test
    void toleranciaProporcionalEvitaResolverUnLagDe1HoraConUnaLecturaDeHace90Minutos() {
        Node node = nodeRepository.save(new Node("feat-5"));
        telemetryReadingRepository.save(TestSupport.reading(node, NOW, 20.0));
        telemetryReadingRepository.save(TestSupport.reading(node, NOW.minus(Duration.ofMinutes(90)), 45.0));

        FeatureVector features = featureService.computeFeatures(node, NOW, null);

        // Tolerancia efectiva para 1 h = min(30 min, 25 % de 60 min) = 15 min.
        assertThat(features.soilMoistureLag1h()).isNull();
    }
}
