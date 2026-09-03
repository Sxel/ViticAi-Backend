package com.vitialert.backend.controller;

import com.vitialert.backend.domain.QualityFlag;
import com.vitialert.backend.domain.TelemetryReading;
import com.vitialert.backend.repository.DecisionRecordRepository;
import com.vitialert.backend.repository.NodeRepository;
import com.vitialert.backend.repository.TelemetryReadingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Criterios de exito 1 a 6: el contrato del ESP32 no cambia, el JSON actual se acepta tal cual,
 * la lectura queda persistida y la valvula sigue gobernada por decision_riego_local.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TelemetryIngestionIT {

    private static final String PAYLOAD_ESP32 = """
            {
              "nodo_id": "%s",
              "temperatura_ambiente_c": 28.4,
              "humedad_relativa_pct": 45,
              "humedad_suelo_pct": 22,
              "humedad_suelo_raw": 2730,
              "velocidad_viento_kmh": 18.5,
              "caudal_l_min": 7.80,
              "volumen_total_l": 124.60,
              "valvula_abierta_actual": true,
              "decision_riego_local": %s
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TelemetryReadingRepository telemetryReadingRepository;

    @Autowired
    private NodeRepository nodeRepository;

    @Autowired
    private DecisionRecordRepository decisionRecordRepository;

    @Test
    void aceptaElPayloadRealDelEsp32YPersisteLaLectura() throws Exception {
        mockMvc.perform(post("/api/data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD_ESP32.formatted("ingest-1", "true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abrir_valvula").value(true))
                .andExpect(jsonPath("$.encender_luz").value(true));

        var node = nodeRepository.findByExternalId("ingest-1").orElseThrow();
        List<TelemetryReading> readings = telemetryReadingRepository.findLatest(node.getId(), PageRequest.of(0, 5));

        assertThat(readings).hasSize(1);
        TelemetryReading reading = readings.get(0);
        assertThat(reading.getTemperaturaAmbienteC()).isEqualTo(28.4);
        assertThat(reading.getHumedadRelativaPct()).isEqualTo(45.0);
        assertThat(reading.getHumedadSueloPct()).isEqualTo(22.0);
        assertThat(reading.getHumedadSueloRaw()).isEqualTo(2730);
        assertThat(reading.getVelocidadVientoKmh()).isEqualTo(18.5);
        assertThat(reading.getCaudalLMin()).isEqualByComparingTo("7.80");
        assertThat(reading.getVolumenTotalL()).isEqualByComparingTo("124.60");
        assertThat(reading.getValvulaAbiertaActual()).isTrue();
        assertThat(reading.getDecisionRiegoLocal()).isTrue();
        assertThat(reading.getTimestampReceived()).isNotNull();
    }

    @Test
    void laDecisionFinalReplicaLaDecisionLocalCuandoNoHayModelo() throws Exception {
        mockMvc.perform(post("/api/data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD_ESP32.formatted("ingest-2", "false")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abrir_valvula").value(false))
                .andExpect(jsonPath("$.encender_luz").value(false));

        var node = nodeRepository.findByExternalId("ingest-2").orElseThrow();
        var decisions = decisionRecordRepository.findByNode(node.getId(), PageRequest.of(0, 5));

        assertThat(decisions.getContent()).hasSize(1);
        var decision = decisions.getContent().get(0);
        assertThat(decision.getDecisionLocal()).isFalse();
        assertThat(decision.getDecisionBackend()).isNull();
        assertThat(decision.isDecisionFinal()).isFalse();
        assertThat(decision.getSource().name()).isEqualTo("LOCAL_FALLBACK");
        assertThat(decision.getAccion().name()).isEqualTo("CERRAR");
    }

    @Test
    void rechazaTemperaturaFueraDelRangoFisico() throws Exception {
        mockMvc.perform(post("/api/data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nodo_id":"ingest-3","temperatura_ambiente_c":120.0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/data"))
                .andExpect(jsonPath("$.details.temperaturaAmbienteC").exists());

        assertThat(nodeRepository.findByExternalId("ingest-3")).isEmpty();
    }

    @Test
    void rechazaHumedadRelativaMayorA100() throws Exception {
        mockMvc.perform(post("/api/data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nodo_id":"ingest-4","humedad_relativa_pct":150}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void rechazaCaudalNegativoYVientoImposible() throws Exception {
        mockMvc.perform(post("/api/data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nodo_id":"ingest-5","caudal_l_min":-3.0,"velocidad_viento_kmh":900}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.caudalLMin").exists())
                .andExpect(jsonPath("$.details.velocidadVientoKmh").exists());
    }

    @Test
    void rechazaPayloadSinNodoId() throws Exception {
        mockMvc.perform(post("/api/data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"humedad_suelo_pct":30}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.nodoId").exists());
    }

    @Test
    void aceptaPayloadIncompletoYLoMarcaComoMissing() throws Exception {
        mockMvc.perform(post("/api/data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nodo_id":"ingest-6","humedad_suelo_pct":30}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abrir_valvula").value(false));

        var node = nodeRepository.findByExternalId("ingest-6").orElseThrow();
        TelemetryReading reading = telemetryReadingRepository
                .findLatest(node.getId(), PageRequest.of(0, 1)).get(0);

        assertThat(reading.getQualityFlag()).isEqualTo(QualityFlag.MISSING);
        assertThat(reading.getQualityNotes()).contains("MISSING:temperatura_ambiente_c");
        assertThat(reading.getHumedadSueloPct()).isEqualTo(30.0);
    }

    @Test
    void marcaComoSospechosoElCaudalConValvulaCerrada() throws Exception {
        mockMvc.perform(post("/api/data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nodo_id":"ingest-7","temperatura_ambiente_c":22.0,"humedad_relativa_pct":50,
                                 "humedad_suelo_pct":30,"humedad_suelo_raw":2500,"velocidad_viento_kmh":5,
                                 "caudal_l_min":4.5,"volumen_total_l":10.0,
                                 "valvula_abierta_actual":false,"decision_riego_local":false}
                                """))
                .andExpect(status().isOk());

        var node = nodeRepository.findByExternalId("ingest-7").orElseThrow();
        TelemetryReading reading = telemetryReadingRepository
                .findLatest(node.getId(), PageRequest.of(0, 1)).get(0);

        assertThat(reading.getQualityFlag()).isEqualTo(QualityFlag.SUSPECT);
        assertThat(reading.getQualityNotes()).contains("posible fuga");
    }
}
