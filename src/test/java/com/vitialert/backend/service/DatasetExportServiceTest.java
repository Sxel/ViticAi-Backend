package com.vitialert.backend.service;

import com.vitialert.backend.TestSupport;
import com.vitialert.backend.domain.Granularity;
import com.vitialert.backend.domain.Node;
import com.vitialert.backend.domain.WeatherObservation;
import com.vitialert.backend.repository.NodeRepository;
import com.vitialert.backend.repository.TelemetryReadingRepository;
import com.vitialert.backend.repository.WeatherObservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Criterio de exito 8 aplicado al dataset: los lags y el target t+24h se obtienen por
 * timestamp real. Un hueco produce una celda VACIA, nunca un cero ni un valor corrido.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DatasetExportServiceTest {

    private static final Instant START = Instant.parse("2026-09-01T00:00:00Z");
    private static final int HOURS = 30;

    @Autowired
    private DatasetExportService datasetExportService;

    @Autowired
    private TelemetryReadingRepository telemetryReadingRepository;

    @Autowired
    private WeatherObservationRepository weatherObservationRepository;

    @Autowired
    private NodeRepository nodeRepository;

    private Node buildSeries(String externalId) {
        Node node = nodeRepository.save(new Node(externalId));
        // Una lectura cada 5 minutos durante 30 horas: 12 muestras por bucket horario.
        for (int step = 0; step < HOURS * 12; step++) {
            Instant at = START.plus(Duration.ofMinutes(5L * step));
            double soil = 60.0 - step * 0.05;
            telemetryReadingRepository.save(TestSupport.reading(node, at, soil));
        }
        return node;
    }

    @Test
    void generaLaCabeceraCompletaDelDataset() {
        Node node = buildSeries("ds-1");

        String csv = datasetExportService.exportCsv(
                node, START, START.plus(Duration.ofHours(HOURS)), Granularity.HOURLY);

        String header = csv.lines().findFirst().orElseThrow();
        assertThat(header).isEqualTo(String.join(",", DatasetExportService.header()));
        assertThat(header).contains("soil_moisture_lag_24h", "soil_moisture_slope_12h",
                "irrigation_volume_24h", "et0_mm", "soil_moisture_t_plus_24h");
    }

    @Test
    void resuelveLagsYTargetPorTimestampYDejaVaciasLasCeldasSinDato() {
        Node node = buildSeries("ds-2");

        String csv = datasetExportService.exportCsv(
                node, START, START.plus(Duration.ofHours(HOURS)), Granularity.HOURLY);

        Map<String, Map<String, String>> rows = parse(csv);
        assertThat(rows).hasSize(HOURS);

        // Primera fila: no hay pasado, por lo tanto todos los lags estan VACIOS (no en cero).
        Map<String, String> first = rows.get(START.toString());
        assertThat(first).isNotNull();
        assertThat(first.get("soil_moisture_lag_1h")).isEmpty();
        assertThat(first.get("soil_moisture_lag_24h")).isEmpty();
        assertThat(first.get("soil_moisture_slope_3h")).isEmpty();
        // El target si existe: hay datos 24 h despues.
        assertThat(first.get("soil_moisture_t_plus_24h")).isNotEmpty();

        // Fila con 3 horas de historia: los lags de 1 h y 3 h ya se resuelven.
        Map<String, String> hour3 = rows.get(START.plus(Duration.ofHours(3)).toString());
        assertThat(hour3.get("soil_moisture_lag_1h")).isNotEmpty();
        assertThat(hour3.get("soil_moisture_lag_3h")).isNotEmpty();
        assertThat(hour3.get("soil_moisture_lag_6h")).isEmpty();
        assertThat(Double.parseDouble(hour3.get("soil_moisture_lag_1h")))
                .isEqualTo(Double.parseDouble(rows.get(START.plus(Duration.ofHours(2)).toString())
                        .get("soil_moisture_pct")));

        // Ultimas filas: el target t+24h cae fuera de la serie y queda vacio.
        Map<String, String> hour29 = rows.get(START.plus(Duration.ofHours(29)).toString());
        assertThat(hour29.get("soil_moisture_lag_24h")).isNotEmpty();
        assertThat(hour29.get("soil_moisture_t_plus_24h")).isEmpty();
    }

    @Test
    void incorporaLaMeteorologiaDiariaImportadaDelDataMiner() {
        Node node = buildSeries("ds-3");

        WeatherObservation observation = new WeatherObservation(LocalDate.of(2026, 9, 1), "OPEN_METEO_ERA5");
        observation.setPrecipitationMm(12.5);
        observation.setEt0Mm(4.2);
        observation.setVpdMaxKpa(1.8);
        observation.setSolarRadiationMjM2(22.0);
        weatherObservationRepository.save(observation);

        String csv = datasetExportService.exportCsv(
                node, START, START.plus(Duration.ofHours(HOURS)), Granularity.HOURLY);
        Map<String, Map<String, String>> rows = parse(csv);

        Map<String, String> firstDayRow = rows.get(START.plus(Duration.ofHours(5)).toString());
        assertThat(firstDayRow.get("precipitation_mm")).isEqualTo("12.5");
        assertThat(firstDayRow.get("et0_mm")).isEqualTo("4.2");
        assertThat(firstDayRow.get("vpd_kpa")).isEqualTo("1.8");
        assertThat(firstDayRow.get("solar_radiation")).isEqualTo("22");

        // El dia 2 no fue importado: las celdas quedan vacias, nunca en cero.
        Map<String, String> secondDayRow = rows.get(START.plus(Duration.ofHours(26)).toString());
        assertThat(secondDayRow.get("precipitation_mm")).isEmpty();
        assertThat(secondDayRow.get("et0_mm")).isEmpty();
    }

    private static Map<String, Map<String, String>> parse(String csv) {
        List<String> lines = new ArrayList<>(csv.lines().toList());
        String[] header = lines.remove(0).split(",", -1);

        Map<String, Map<String, String>> rows = new LinkedHashMap<>();
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            String[] values = line.split(",", -1);
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < header.length; i++) {
                row.put(header[i], i < values.length ? values[i] : "");
            }
            rows.put(row.get("timestamp"), row);
        }
        return rows;
    }
}
