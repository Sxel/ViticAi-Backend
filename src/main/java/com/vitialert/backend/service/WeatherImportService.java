package com.vitialert.backend.service;

import com.vitialert.backend.config.WeatherProperties;
import com.vitialert.backend.domain.WeatherObservation;
import com.vitialert.backend.dto.WeatherImportResultDto;
import com.vitialert.backend.repository.WeatherObservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Importacion del dataset historico producido por el Data Miner Python.
 *
 * <p>Se eligio la via mas simple del alcance: subir el CSV ya procesado. El Data Miner NO
 * se porta a Java y los datos meteorologicos se guardan en su propia tabla, separados de
 * la telemetria IoT porque son fuentes distintas.</p>
 *
 * <p>La importacion es idempotente por (fecha, fuente): reimportar el mismo CSV actualiza
 * las filas existentes en lugar de duplicarlas.</p>
 */
@Service
public class WeatherImportService {

    private static final Logger log = LoggerFactory.getLogger(WeatherImportService.class);

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Alias aceptados para cada columna del dataset. */
    private static final Map<String, List<String>> COLUMN_ALIASES = Map.ofEntries(
            Map.entry("date", List.of("fecha", "date", "timestamp", "fecha_hora", "time")),
            Map.entry("temp_max_c", List.of("temp_max_c", "temperatura_max_c", "tmax")),
            Map.entry("temp_mean_c", List.of("temp_media_c", "temp_mean_c", "temperatura_media_c", "tmean")),
            Map.entry("humidity_mean_pct", List.of("humedad_media_pct", "humidity_mean_pct")),
            Map.entry("humidity_min_pct", List.of("humedad_min_pct", "humidity_min_pct")),
            Map.entry("wind_mean_kmh", List.of("velocidad_viento_media_kmh", "wind_mean_kmh")),
            Map.entry("wind_max_kmh", List.of("velocidad_viento_max_kmh", "wind_max_kmh")),
            Map.entry("precipitation_mm", List.of("precipitacion_mm", "precipitation_mm", "lluvia_mm")),
            Map.entry("solar_radiation_mj_m2", List.of("radiacion_solar_mj_m2", "solar_radiation_mj_m2")),
            Map.entry("et0_mm", List.of("et0_mm", "eto_mm", "et0")),
            Map.entry("vpd_max_kpa", List.of("vpd_max_kpa", "vpd_kpa", "vpd"))
    );

    private final WeatherObservationRepository weatherObservationRepository;
    private final WeatherProperties weatherProperties;

    public WeatherImportService(WeatherObservationRepository weatherObservationRepository,
                                WeatherProperties weatherProperties) {
        this.weatherObservationRepository = weatherObservationRepository;
        this.weatherProperties = weatherProperties;
    }

    @Transactional
    public WeatherImportResultDto importCsv(InputStream inputStream, String requestedSource) throws IOException {
        String source = (requestedSource == null || requestedSource.isBlank())
                ? weatherProperties.defaultSource()
                : requestedSource.trim();

        int rowsRead = 0;
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new IllegalArgumentException("El archivo CSV esta vacio o no tiene cabecera.");
            }
            headerLine = stripBom(headerLine);

            char delimiter = SimpleCsvParser.detectDelimiter(headerLine);
            Map<String, Integer> index = buildColumnIndex(SimpleCsvParser.parseLine(headerLine, delimiter));

            if (!index.containsKey("date")) {
                throw new IllegalArgumentException(
                        "El CSV no tiene columna de fecha. Alias aceptados: " + COLUMN_ALIASES.get("date"));
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                rowsRead++;
                List<String> values = SimpleCsvParser.parseLine(line, delimiter);

                Optional<LocalDate> date = parseDate(value(values, index, "date"));
                if (date.isEmpty()) {
                    skipped++;
                    if (errors.size() < 20) {
                        errors.add("Linea " + lineNumber + ": fecha invalida o ausente.");
                    }
                    continue;
                }

                Optional<WeatherObservation> existing =
                        weatherObservationRepository.findByObservationDateAndSource(date.get(), source);

                WeatherObservation observation = existing.orElseGet(
                        () -> new WeatherObservation(date.get(), source));

                observation.setTempMaxC(parseDouble(value(values, index, "temp_max_c")));
                observation.setTempMeanC(parseDouble(value(values, index, "temp_mean_c")));
                observation.setHumidityMeanPct(parseDouble(value(values, index, "humidity_mean_pct")));
                observation.setHumidityMinPct(parseDouble(value(values, index, "humidity_min_pct")));
                observation.setWindMeanKmh(parseDouble(value(values, index, "wind_mean_kmh")));
                observation.setWindMaxKmh(parseDouble(value(values, index, "wind_max_kmh")));
                observation.setPrecipitationMm(parseDouble(value(values, index, "precipitation_mm")));
                observation.setSolarRadiationMjM2(parseDouble(value(values, index, "solar_radiation_mj_m2")));
                observation.setEt0Mm(parseDouble(value(values, index, "et0_mm")));
                observation.setVpdMaxKpa(parseDouble(value(values, index, "vpd_max_kpa")));

                weatherObservationRepository.save(observation);
                if (existing.isPresent()) {
                    updated++;
                } else {
                    inserted++;
                }
            }
        }

        log.info("Importacion meteorologica source={} leidas={} insertadas={} actualizadas={} descartadas={}",
                source, rowsRead, inserted, updated, skipped);

        return new WeatherImportResultDto(source, rowsRead, inserted, updated, skipped, errors);
    }

    private static Map<String, Integer> buildColumnIndex(List<String> header) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String normalized = normalize(header.get(i));
            for (Map.Entry<String, List<String>> entry : COLUMN_ALIASES.entrySet()) {
                if (entry.getValue().contains(normalized)) {
                    index.putIfAbsent(entry.getKey(), i);
                }
            }
        }
        return index;
    }

    private static String value(List<String> values, Map<String, Integer> index, String logicalName) {
        Integer position = index.get(logicalName);
        if (position == null || position >= values.size()) {
            return null;
        }
        String raw = values.get(position);
        return (raw == null || raw.isBlank()) ? null : raw.trim();
    }

    /** Devuelve null si el valor falta: nunca se rellena con cero. */
    private static Double parseDouble(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.replace(',', '.');
        if (normalized.equalsIgnoreCase("nan") || normalized.equalsIgnoreCase("null")
                || normalized.equals("-") || normalized.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Optional<LocalDate> parseDate(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.trim();
        try {
            if (value.length() >= 10 && value.charAt(4) == '-') {
                return Optional.of(LocalDate.parse(value.substring(0, 10)));
            }
            return Optional.of(LocalDate.parse(value, DMY));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static String normalize(String header) {
        String value = header == null ? "" : header.trim().toLowerCase(Locale.ROOT);
        value = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return value.replace(' ', '_');
    }

    private static String stripBom(String line) {
        return line.startsWith("\uFEFF") ? line.substring(1) : line;
    }
}
