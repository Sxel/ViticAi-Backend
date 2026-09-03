package com.vitialert.backend.controller;

import com.vitialert.backend.dto.WeatherImportResultDto;
import com.vitialert.backend.service.WeatherImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Puerta de entrada de la meteorologia procesada por el Data Miner Python.
 *
 * <p>Se importa el CSV ya generado por ese modulo: el Data Miner sigue siendo un proceso
 * independiente y no se porta a Java.</p>
 */
@RestController
@RequestMapping("/api/admin/weather")
@Tag(name = "Administracion - Meteorologia", description = "Importacion del dataset del Data Miner")
public class AdminWeatherController {

    private final WeatherImportService weatherImportService;

    public AdminWeatherController(WeatherImportService weatherImportService) {
        this.weatherImportService = weatherImportService;
    }

    @Operation(summary = "Importa un CSV diario de meteorologia",
            description = "Idempotente por (fecha, fuente): reimportar el mismo archivo actualiza "
                    + "las filas existentes. Las celdas vacias se guardan como null, nunca como cero.")
    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WeatherImportResultDto importCsv(@RequestParam("file") MultipartFile file,
                                            @RequestParam(value = "source", required = false) String source)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Se requiere un archivo CSV en el campo 'file'.");
        }
        return weatherImportService.importCsv(file.getInputStream(), source);
    }
}
