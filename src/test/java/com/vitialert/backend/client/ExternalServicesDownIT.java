package com.vitialert.backend.client;

import com.vitialert.backend.domain.DecisionSource;
import com.vitialert.backend.repository.DecisionRecordRepository;
import com.vitialert.backend.repository.NodeRepository;
import com.vitialert.backend.repository.TelemetryReadingRepository;
import com.vitialert.backend.service.PredictionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Punto 10 y 19 del alcance: si VitiAlert satelital o la API de inferencia estan caidos, la
 * telemetria se sigue aceptando y persistiendo, se registra la indisponibilidad y la valvula
 * cae al fallback local. Las integraciones apuntan a un puerto cerrado a proposito.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:vitialert-down;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL",
        "vitialert.satellite.enabled=true",
        "vitialert.satellite.base-url=http://127.0.0.1:59997",
        "vitialert.satellite.connect-timeout-ms=300",
        "vitialert.satellite.read-timeout-ms=300",
        "vitialert.ml.enabled=true",
        "vitialert.ml.base-url=http://127.0.0.1:59998",
        "vitialert.ml.connect-timeout-ms=300",
        "vitialert.ml.read-timeout-ms=300"
})
@Transactional
class ExternalServicesDownIT {

    private static final String PAYLOAD = """
            {
              "nodo_id": "down-1",
              "temperatura_ambiente_c": 28.4,
              "humedad_relativa_pct": 45,
              "humedad_suelo_pct": 22,
              "humedad_suelo_raw": 2730,
              "velocidad_viento_kmh": 18.5,
              "caudal_l_min": 7.80,
              "volumen_total_l": 124.60,
              "valvula_abierta_actual": true,
              "decision_riego_local": true
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SatelliteClient satelliteClient;

    @Autowired
    private WeatherClient weatherClient;

    @Autowired
    private PredictionService predictionService;

    @Autowired
    private NodeRepository nodeRepository;

    @Autowired
    private TelemetryReadingRepository telemetryReadingRepository;

    @Autowired
    private DecisionRecordRepository decisionRecordRepository;

    @Test
    void elClienteSatelitalCaidoDevuelveVacioEnLugarDeFallar() {
        assertThat(satelliteClient.isEnabled()).isTrue();
        assertThat(satelliteClient.fetchLatest("down-1")).isEmpty();
    }

    @Test
    void elDataMinerDesactivadoDevuelveVacio() {
        assertThat(weatherClient.isEnabled()).isFalse();
        assertThat(weatherClient.fetchDaily(java.time.LocalDate.now().minusDays(1),
                java.time.LocalDate.now())).isEmpty();
    }

    @Test
    void laTelemetriaSeGuardaYLaValvulaCaeAlFallbackLocalAunqueLosServiciosEstenCaidos() throws Exception {
        assertThat(predictionService.isEnabled()).isTrue();

        mockMvc.perform(post("/api/data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abrir_valvula").value(true))
                .andExpect(jsonPath("$.encender_luz").value(true));

        var node = nodeRepository.findByExternalId("down-1").orElseThrow();
        assertThat(telemetryReadingRepository.findLatest(node.getId(), PageRequest.of(0, 5))).hasSize(1);

        var decision = decisionRecordRepository.findByNode(node.getId(), PageRequest.of(0, 1))
                .getContent().get(0);
        assertThat(decision.getSource()).isEqualTo(DecisionSource.LOCAL_FALLBACK);
        assertThat(decision.getDecisionBackend()).isNull();
        assertThat(decision.isDecisionFinal()).isTrue();
        assertThat(decision.getMotivo()).contains("fallback");
    }
}
