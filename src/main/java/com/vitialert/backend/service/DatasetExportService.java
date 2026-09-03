package com.vitialert.backend.service;

import com.vitialert.backend.config.WeatherProperties;
import com.vitialert.backend.domain.Granularity;
import com.vitialert.backend.domain.Node;
import com.vitialert.backend.domain.WeatherObservation;
import com.vitialert.backend.dto.AggregatedTelemetryDto;
import com.vitialert.backend.repository.WeatherObservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exportacion del dataset unificado (IoT + riego + meteorologia) para Ciencia de Datos.
 *
 * <p><b>Decision metodologica central.</b> Todas las features y el target se resuelven por
 * TIMESTAMP REAL sobre la serie agregada, nunca por posicion de fila. Concretamente:</p>
 * <ul>
 *   <li>{@code soil_moisture_lag_1h} en el instante {@code t} es el bucket cuyo inicio es
 *       exactamente {@code t - 1h}. Si ese bucket no existe (hueco de datos, nodo offline)
 *       la celda queda VACIA, nunca en cero.</li>
 *   <li>{@code soil_moisture_t_plus_24h} es el bucket {@code t + 24h}. Al obtenerse por
 *       timestamp, un hueco produce un target faltante en lugar de un target corrido, que
 *       es exactamente el error que introduce {@code shift(24)} sobre una serie con huecos.</li>
 *   <li>{@code soil_moisture_mean_24h} se calcula solo si al menos la mitad de los buckets
 *       de la ventana existen; con menos cobertura la media no es representativa.</li>
 * </ul>
 *
 * <p>Las variables meteorologicas provienen del Data Miner y son DIARIAS: se replican en
 * todas las filas del dia correspondiente (UTC). No se interpolan.</p>
 */
@Service
public class DatasetExportService {

    private static final Logger log = LoggerFactory.getLogger(DatasetExportService.class);

    /** Cobertura minima de la ventana de 24 h para publicar la media movil. */
    private static final double MIN_WINDOW_COVERAGE = 0.5;

    private static final List<String> HEADER = List.of(
            "timestamp",
            "node_id",
            "soil_moisture_pct",
            "soil_moisture_lag_1h",
            "soil_moisture_lag_3h",
            "soil_moisture_lag_6h",
            "soil_moisture_lag_24h",
            "soil_moisture_slope_3h",
            "soil_moisture_slope_12h",
            "soil_moisture_mean_24h",
            "temperature_c",
            "relative_humidity_pct",
            "wind_speed_kmh",
            "flow_l_min",
            "irrigation_volume_1h",
            "irrigation_volume_24h",
            "precipitation_mm",
            "et0_mm",
            "vpd_kpa",
            "solar_radiation",
            "sample_count",
            "valve_open_seconds",
            "soil_moisture_t_plus_24h");

    private final AggregationService aggregationService;
    private final WeatherObservationRepository weatherObservationRepository;
    private final WeatherProperties weatherProperties;

    public DatasetExportService(AggregationService aggregationService,
                                WeatherObservationRepository weatherObservationRepository,
                                WeatherProperties weatherProperties) {
        this.aggregationService = aggregationService;
        this.weatherObservationRepository = weatherObservationRepository;
        this.weatherProperties = weatherProperties;
    }

    @Transactional(readOnly = true)
    public String exportCsv(Node node, Instant from, Instant to, Granularity granularity) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException("Se requiere un rango valido: from debe ser anterior a to.");
        }

        Duration step = granularity.duration();

        // Se agrega un margen de 24 h a cada lado: hacia atras para poder resolver los lags
        // de las primeras filas y hacia adelante para poder resolver el target t+24h.
        Instant extendedFrom = granularity.floor(from).minus(Duration.ofHours(24));
        Instant extendedTo = to.plus(Duration.ofHours(24));

        Map<Instant, AggregatedTelemetryDto> series =
                aggregationService.aggregateIndexed(node.getId(), extendedFrom, extendedTo, granularity);

        Map<LocalDate, WeatherObservation> weather = loadWeather(extendedFrom, extendedTo);

        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", HEADER)).append('\n');

        Instant windowStart = granularity.floor(from);
        int rows = 0;

        for (Map.Entry<Instant, AggregatedTelemetryDto> entry : series.entrySet()) {
            Instant timestamp = entry.getKey();
            if (timestamp.isBefore(windowStart) || !timestamp.isBefore(to)) {
                continue;
            }
            csv.append(buildRow(node, timestamp, entry.getValue(), series, weather, step)).append('\n');
            rows++;
        }

        log.info("Dataset exportado nodo={} desde={} hasta={} granularidad={} filas={}",
                node.getExternalId(), from, to, granularity, rows);
        return csv.toString();
    }

    private String buildRow(Node node,
                            Instant timestamp,
                            AggregatedTelemetryDto row,
                            Map<Instant, AggregatedTelemetryDto> series,
                            Map<LocalDate, WeatherObservation> weather,
                            Duration step) {

        Double soil = row.soilMoistureMean();
        Double lag1h = soilAt(series, timestamp.minus(Duration.ofHours(1)));
        Double lag3h = soilAt(series, timestamp.minus(Duration.ofHours(3)));
        Double lag6h = soilAt(series, timestamp.minus(Duration.ofHours(6)));
        Double lag12h = soilAt(series, timestamp.minus(Duration.ofHours(12)));
        Double lag24h = soilAt(series, timestamp.minus(Duration.ofHours(24)));

        Double slope3h = slope(soil, lag3h, 3.0);
        Double slope12h = slope(soil, lag12h, 12.0);
        Double mean24h = windowMeanSoil(series, timestamp, Duration.ofHours(24), step);

        BigDecimal irrigation1h = windowVolume(series, timestamp, Duration.ofHours(1), step);
        BigDecimal irrigation24h = windowVolume(series, timestamp, Duration.ofHours(24), step);

        WeatherObservation observation = weather.get(timestamp.atZone(ZoneOffset.UTC).toLocalDate());
        Double target = soilAt(series, timestamp.plus(Duration.ofHours(24)));

        List<String> values = new ArrayList<>(HEADER.size());
        values.add(timestamp.toString());
        values.add(escape(node.getExternalId()));
        values.add(number(soil));
        values.add(number(lag1h));
        values.add(number(lag3h));
        values.add(number(lag6h));
        values.add(number(lag24h));
        values.add(number(slope3h));
        values.add(number(slope12h));
        values.add(number(mean24h));
        values.add(number(row.tempMean()));
        values.add(number(row.humidityMean()));
        values.add(number(row.windMean()));
        values.add(number(row.flowMean()));
        values.add(number(irrigation1h));
        values.add(number(irrigation24h));
        values.add(number(observation == null ? null : observation.getPrecipitationMm()));
        values.add(number(observation == null ? null : observation.getEt0Mm()));
        values.add(number(observation == null ? null : observation.getVpdMaxKpa()));
        values.add(number(observation == null ? null : observation.getSolarRadiationMjM2()));
        values.add(Integer.toString(row.sampleCount()));
        values.add(Long.toString(row.valveOpenSeconds()));
        values.add(number(target));

        return String.join(",", values);
    }

    private Map<LocalDate, WeatherObservation> loadWeather(Instant from, Instant to) {
        LocalDate fromDate = from.atZone(ZoneOffset.UTC).toLocalDate().minusDays(1);
        LocalDate toDate = to.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1);

        Map<LocalDate, WeatherObservation> byDate = new LinkedHashMap<>();
        String preferred = weatherProperties.preferredSource();

        for (WeatherObservation observation :
                weatherObservationRepository.findByObservationDateBetweenOrderByObservationDateAscSourceAsc(fromDate, toDate)) {
            WeatherObservation current = byDate.get(observation.getObservationDate());
            if (current == null) {
                byDate.put(observation.getObservationDate(), observation);
            } else if (!preferred.equals(current.getSource()) && preferred.equals(observation.getSource())) {
                byDate.put(observation.getObservationDate(), observation);
            }
        }
        return byDate;
    }

    private static Double soilAt(Map<Instant, AggregatedTelemetryDto> series, Instant timestamp) {
        AggregatedTelemetryDto row = series.get(timestamp);
        return row == null ? null : row.soilMoistureMean();
    }

    private static Double slope(Double current, Double past, double hours) {
        if (current == null || past == null) {
            return null;
        }
        return (current - past) / hours;
    }

    /**
     * Media de humedad sobre la ventana movil que termina en {@code timestamp} (inclusive).
     * Devuelve null si la cobertura de buckets es menor al 50 %.
     */
    private static Double windowMeanSoil(Map<Instant, AggregatedTelemetryDto> series,
                                         Instant timestamp,
                                         Duration window,
                                         Duration step) {
        int expected = (int) (window.getSeconds() / step.getSeconds());
        double sum = 0;
        int found = 0;
        for (int i = 0; i < expected; i++) {
            AggregatedTelemetryDto row = series.get(timestamp.minus(step.multipliedBy(i)));
            if (row != null && row.soilMoistureMean() != null) {
                sum += row.soilMoistureMean();
                found++;
            }
        }
        if (found == 0 || found < expected * MIN_WINDOW_COVERAGE) {
            return null;
        }
        return sum / found;
    }

    /**
     * Volumen regado en la ventana movil que termina en {@code timestamp} (inclusive).
     * Null si ningun bucket de la ventana tiene informacion de volumen.
     */
    private static BigDecimal windowVolume(Map<Instant, AggregatedTelemetryDto> series,
                                           Instant timestamp,
                                           Duration window,
                                           Duration step) {
        int buckets = (int) (window.getSeconds() / step.getSeconds());
        BigDecimal total = BigDecimal.ZERO;
        boolean observed = false;
        for (int i = 0; i < buckets; i++) {
            AggregatedTelemetryDto row = series.get(timestamp.minus(step.multipliedBy(i)));
            if (row != null && row.waterVolumeUsed() != null) {
                total = total.add(row.waterVolumeUsed());
                observed = true;
            }
        }
        return observed ? total : null;
    }

    /** Celda vacia para dato faltante. Nunca cero. */
    private static String number(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return "";
        }
        return BigDecimal.valueOf(value)
                .setScale(6, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private static String number(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    public static List<String> header() {
        return HEADER;
    }
}
