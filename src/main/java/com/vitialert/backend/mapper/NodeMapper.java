package com.vitialert.backend.mapper;

import com.vitialert.backend.domain.Node;
import com.vitialert.backend.dto.NodeDto;
import org.springframework.stereotype.Component;

@Component
public class NodeMapper {

    public NodeDto toDto(Node node) {
        return new NodeDto(
                node.getId(),
                node.getExternalId(),
                node.getNombre(),
                node.getDescripcion(),
                node.isActivo(),
                node.getFinca(),
                node.getSector(),
                node.getLatitud(),
                node.getLongitud(),
                node.getCreatedAt(),
                node.getUpdatedAt());
    }
}
