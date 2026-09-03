package com.vitialert.backend.mapper;

import com.vitialert.backend.domain.Node;
import com.vitialert.backend.domain.QualityFlag;
import com.vitialert.backend.domain.TelemetryReading;
import com.vitialert.backend.dto.TelemetryReadingDto;
import com.vitialert.backend.dto.TelemetryRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class TelemetryMapper {

    /**
     * Construye la lectura inmutable. El timestamp lo aporta el backend, nunca el payload.
     */
    public TelemetryReading toEntity(TelemetryRequest request,
                                     Node node,
                                     Instant timestampReceived,
                                     QualityFlag qualityFlag,
                                     String qualityNotes) {
        return TelemetryReading.builder()
                .node(node)
                .timestampReceived(timestampReceived)
                .temperaturaAmbienteC(request.temperaturaAmbienteC())
                .humedadRelativaPct(request.humedadRelativaPct())
                .humedadSueloPct(request.humedadSueloPct())
                .humedadSueloRaw(request.humedadSueloRaw())
                .velocidadVientoKmh(request.velocidadVientoKmh())
                .caudalLMin(request.caudalLMin())
                .volumenTotalL(request.volumenTotalL())
                .valvulaAbiertaActual(request.valvulaAbiertaActual())
                .decisionRiegoLocal(request.decisionRiegoLocal())
                .quality(qualityFlag, qualityNotes)
                .build();
    }

    public TelemetryReadingDto toDto(TelemetryReading reading) {
        return new TelemetryReadingDto(
                reading.getId(),
                reading.getNode().getExternalId(),
                reading.getTimestampReceived(),
                reading.getTemperaturaAmbienteC(),
                reading.getHumedadRelativaPct(),
                reading.getHumedadSueloPct(),
                reading.getHumedadSueloRaw(),
                reading.getVelocidadVientoKmh(),
                reading.getCaudalLMin(),
                reading.getVolumenTotalL(),
                reading.getValvulaAbiertaActual(),
                reading.getDecisionRiegoLocal(),
                reading.getQualityFlag() == null ? null : reading.getQualityFlag().name(),
                reading.getQualityNotes());
    }
}
