package com.vitialert.backend.mapper;

import com.vitialert.backend.domain.IrrigationEvent;
import com.vitialert.backend.dto.IrrigationEventDto;
import org.springframework.stereotype.Component;

@Component
public class IrrigationEventMapper {

    public IrrigationEventDto toDto(IrrigationEvent event) {
        return new IrrigationEventDto(
                event.getId(),
                event.getNode().getExternalId(),
                event.getStartedAt(),
                event.getEndedAt(),
                event.getDuracionSegundos(),
                event.getVolumenInicialL(),
                event.getVolumenFinalL(),
                event.getVolumenAplicadoL(),
                event.getCaudalPromedioLMin(),
                event.getEstado() == null ? null : event.getEstado().name(),
                event.getObservaciones());
    }
}
