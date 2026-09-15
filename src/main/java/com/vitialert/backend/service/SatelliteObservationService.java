package com.vitialert.backend.service;

import com.vitialert.backend.client.SatelliteClient;
import com.vitialert.backend.domain.Node;
import com.vitialert.backend.domain.SatelliteObservation;
import com.vitialert.backend.repository.NodeRepository;
import com.vitialert.backend.repository.SatelliteObservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/** Persiste capturas satelitales sin bloquear nunca la ingesta IoT. */
@Service
public class SatelliteObservationService {
    private final NodeRepository nodeRepository;
    private final SatelliteObservationRepository repository;
    private final SatelliteClient client;

    public SatelliteObservationService(NodeRepository nodeRepository,
                                       SatelliteObservationRepository repository,
                                       SatelliteClient client) {
        this.nodeRepository = nodeRepository;
        this.repository = repository;
        this.client = client;
    }

    @Transactional
    public Map<String, Object> refreshAll() {
        int eligible = 0;
        int stored = 0;
        for (Node node : nodeRepository.findAllByOrderByExternalIdAsc()) {
            if (node.getLatitud() == null || node.getLongitud() == null) {
                continue;
            }
            eligible++;
            var features = client.fetchFeatures(node.getLatitud(), node.getLongitud());
            if (features.isPresent()) {
                repository.save(SatelliteObservation.from(node, features.get()));
                stored++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eligible_nodes", eligible);
        result.put("stored_observations", stored);
        result.put("satellite_enabled", client.isEnabled());
        return result;
    }
}
