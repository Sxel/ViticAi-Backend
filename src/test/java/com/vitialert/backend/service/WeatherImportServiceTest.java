package com.vitialert.backend.service;

import com.vitialert.backend.domain.WeatherObservation;
import com.vitialert.backend.dto.WeatherImportResultDto;
import com.vitialert.backend.repository.WeatherObservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Importacion del dataset historico del Data Miner: idempotente y sin ceros artificiales. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WeatherImportServiceTest {

    private static final String CSV = """
            fecha,temp_max_c,temp_media_c,humedad_media_pct,humedad_min_pct,velocidad_viento_media_kmh,velocidad_viento_max_kmh,precipitacion_mm,radiacion_solar_mj_m2,et0_mm,vpd_max_kpa
            2026-08-01,31.2,24.1,52.0,28.0,11.4,29.8,0.0,24.6,5.1,2.4
            2026-08-02,28.9,22.7,61.0,35.0,9.2,21.0,4.6,18.2,3.8,1.6
            2026-08-03,33.0,26.4,44.0,,13.1,,0.0,,5.9,3.1
            """;

    @Autowired
    private WeatherImportService weatherImportService;

    @Autowired
    private WeatherObservationRepository weatherObservationRepository;

    @Test
    void importaElCsvDelDataMinerYRespetaLosValoresFaltantes() throws Exception {
        WeatherImportResultDto result = weatherImportService.importCsv(stream(CSV), "TEST_SOURCE");

        assertThat(result.rowsRead()).isEqualTo(3);
        assertThat(result.inserted()).isEqualTo(3);
        assertThat(result.updated()).isZero();
        assertThat(result.skipped()).isZero();
        assertThat(result.source()).isEqualTo("TEST_SOURCE");

        WeatherObservation first = weatherObservationRepository
                .findByObservationDateAndSource(LocalDate.of(2026, 8, 1), "TEST_SOURCE").orElseThrow();
        assertThat(first.getTempMaxC()).isEqualTo(31.2);
        assertThat(first.getEt0Mm()).isEqualTo(5.1);
        assertThat(first.getVpdMaxKpa()).isEqualTo(2.4);
        assertThat(first.getPrecipitationMm()).isEqualTo(0.0);

        // Celdas vacias -> null, nunca cero: la distincion es critica para Ciencia de Datos.
        WeatherObservation third = weatherObservationRepository
                .findByObservationDateAndSource(LocalDate.of(2026, 8, 3), "TEST_SOURCE").orElseThrow();
        assertThat(third.getHumidityMinPct()).isNull();
        assertThat(third.getWindMaxKmh()).isNull();
        assertThat(third.getSolarRadiationMjM2()).isNull();
        assertThat(third.getTempMaxC()).isEqualTo(33.0);
    }

    @Test
    void reimportarElMismoArchivoActualizaEnLugarDeDuplicar() throws Exception {
        weatherImportService.importCsv(stream(CSV), "IDEMPOTENTE");
        WeatherImportResultDto second =
                weatherImportService.importCsv(stream(CSV.replace("31.2", "35.9")), "IDEMPOTENTE");

        assertThat(second.inserted()).isZero();
        assertThat(second.updated()).isEqualTo(3);

        Optional<WeatherObservation> updated = weatherObservationRepository
                .findByObservationDateAndSource(LocalDate.of(2026, 8, 1), "IDEMPOTENTE");
        assertThat(updated).isPresent();
        assertThat(updated.get().getTempMaxC()).isEqualTo(35.9);
    }

    @Test
    void aceptaSeparadorPuntoYComaYFechasEnFormatoLocal() throws Exception {
        String csv = """
                fecha;temp_max_c;precipitacion_mm;et0_mm
                05/08/2026;30,5;1,2;4,4
                """;

        WeatherImportResultDto result = weatherImportService.importCsv(stream(csv), "LOCAL_FORMAT");

        assertThat(result.inserted()).isEqualTo(1);
        WeatherObservation observation = weatherObservationRepository
                .findByObservationDateAndSource(LocalDate.of(2026, 8, 5), "LOCAL_FORMAT").orElseThrow();
        assertThat(observation.getTempMaxC()).isEqualTo(30.5);
        assertThat(observation.getPrecipitationMm()).isEqualTo(1.2);
        assertThat(observation.getEt0Mm()).isEqualTo(4.4);
        assertThat(observation.getTempMeanC()).isNull();
    }

    private static ByteArrayInputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
