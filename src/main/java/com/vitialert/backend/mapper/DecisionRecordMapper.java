package com.vitialert.backend.mapper;

import com.vitialert.backend.domain.DecisionRecord;
import com.vitialert.backend.dto.DecisionRecordDto;
import org.springframework.stereotype.Component;

@Component
public class DecisionRecordMapper {

    public DecisionRecordDto toDto(DecisionRecord record) {
        return new DecisionRecordDto(
                record.getId(),
                record.getNode().getExternalId(),
                record.getTimestamp(),
                record.getDecisionLocal(),
                record.getDecisionBackend(),
                record.isDecisionFinal(),
                record.getAccion() == null ? null : record.getAccion().name(),
                record.getMotivo(),
                record.getSource() == null ? null : record.getSource().name(),
                record.getModelVersion());
    }
}
