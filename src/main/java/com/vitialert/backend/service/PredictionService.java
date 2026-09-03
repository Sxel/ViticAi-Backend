package com.vitialert.backend.service;

import com.vitialert.backend.config.MlProperties;
import com.vitialert.backend.dto.FeatureVector;
import com.vitialert.backend.dto.PredictionRequest;
import com.vitialert.backend.dto.PredictionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Punto unico de contacto con el modelo de Ciencia de Datos.
 *
 * <p>Reparto de responsabilidades acordado para la tesis:</p>
 * <ul>
 *   <li><b>Python</b>: entrenamiento, evaluacion, serializacion (.pkl) e inferencia.</li>
 *   <li><b>Java</b>: recibir, validar, persistir, integrar, orquestar, construir features,
 *       consultar la inferencia, tomar la decision final y responder al ESP32.</li>
 * </ul>
 *
 * <p>Java NUNCA carga un .pkl. La unica via es HTTP contra el servicio Python.</p>
 *
 * <p>Mientras {@code vitialert.ml.enabled=false} este servicio no hace ninguna llamada de
 * red y devuelve {@link Optional#empty()}, de modo que la maqueta sigue funcionando
 * exactamente igual que hoy.</p>
 */
@Service
public class PredictionService {

    private static final Logger log = LoggerFactory.getLogger(PredictionService.class);

    private final RestClient restClient;
    private final MlProperties properties;

    public PredictionService(@Qualifier("mlRestClient") RestClient restClient, MlProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    /**
     * Consulta la inferencia al servicio Python.
     *
     * @return vacio si el modelo esta desactivado o el servicio no responde a tiempo;
     * en ese caso el llamador debe aplicar el fallback local
     */
    public Optional<PredictionResponse> predict(FeatureVector features) {
        if (!properties.enabled()) {
            return Optional.empty();
        }
        try {
            PredictionRequest request = new PredictionRequest(
                    features.nodeId(), features.timestamp(), toFeatureMap(features));

            PredictionResponse response = restClient.post()
                    .uri(properties.predictPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(PredictionResponse.class);

            return Optional.ofNullable(response);
        } catch (RuntimeException ex) {
            log.warn("Servicio de inferencia no disponible (nodo={}): {}", features.nodeId(), ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Serializa el vector de features con los mismos nombres que usa el dataset exportado.
     * Los valores faltantes viajan como null explicito: Python decide como imputarlos.
     */
    static Map<String, Double> toFeatureMap(FeatureVector features) {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put("soil_moisture_pct", features.soilMoisturePct());
        map.put("soil_moisture_lag_1h", features.soilMoistureLag1h());
        map.put("soil_moisture_lag_3h", features.soilMoistureLag3h());
        map.put("soil_moisture_lag_6h", features.soilMoistureLag6h());
        map.put("soil_moisture_lag_24h", features.soilMoistureLag24h());
        map.put("soil_moisture_slope_3h", features.soilMoistureSlope3h());
        map.put("soil_moisture_slope_12h", features.soilMoistureSlope12h());
        map.put("soil_moisture_mean_24h", features.soilMoistureMean24h());
        map.put("temperature_c", features.temperatureC());
        map.put("relative_humidity_pct", features.relativeHumidityPct());
        map.put("wind_speed_kmh", features.windSpeedKmh());
        map.put("flow_l_min", toDouble(features.flowLMin()));
        map.put("irrigation_volume_1h", toDouble(features.irrigationVolume1h()));
        map.put("irrigation_volume_24h", toDouble(features.irrigationVolume24h()));
        return map;
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
