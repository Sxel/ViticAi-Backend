package com.vitialert.backend.controller;

import com.vitialert.backend.client.SatelliteClient;
import com.vitialert.backend.client.WeatherClient;
import com.vitialert.backend.service.PredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Estado de las integraciones externas, util durante la defensa y la puesta en marcha. */
@RestController
@RequestMapping("/api/health")
@Tag(name = "Salud", description = "Estado del backend y de sus integraciones")
public class HealthController {

    private final SatelliteClient satelliteClient;
    private final WeatherClient weatherClient;
    private final PredictionService predictionService;

    public HealthController(SatelliteClient satelliteClient,
                            WeatherClient weatherClient,
                            PredictionService predictionService) {
        this.satelliteClient = satelliteClient;
        this.weatherClient = weatherClient;
        this.predictionService = predictionService;
    }

    @Operation(summary = "Estado del backend y de las integraciones opcionales")
    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> integrations = new LinkedHashMap<>();
        integrations.put("satellite_enabled", satelliteClient.isEnabled());
        integrations.put("weather_pull_enabled", weatherClient.isEnabled());
        integrations.put("ml_enabled", predictionService.isEnabled());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("timestamp", Instant.now());
        body.put("integrations", integrations);
        return body;
    }
}
