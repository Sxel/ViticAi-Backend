package com.vitialert.backend.service;

import com.vitialert.backend.config.NodeProperties;
import com.vitialert.backend.domain.Node;
import com.vitialert.backend.exception.ResourceNotFoundException;
import com.vitialert.backend.repository.NodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NodeService {

    private static final Logger log = LoggerFactory.getLogger(NodeService.class);

    private final NodeRepository nodeRepository;
    private final NodeProperties nodeProperties;

    public NodeService(NodeRepository nodeRepository, NodeProperties nodeProperties) {
        this.nodeRepository = nodeRepository;
        this.nodeProperties = nodeProperties;
    }

    /**
     * Resuelve el nodo del payload y, si esta habilitado el auto registro, lo da de alta
     * la primera vez que aparece. Esto permite apuntar la maqueta actual al backend nuevo
     * sin ningun alta manual previa.
     *
     * <p>El alta ocurre una sola vez por nodo, por lo que no se agrega bloqueo: en el peor
     * caso dos POST simultaneos del mismo nodo nuevo provocan un fallo de unicidad y el
     * ESP32 reintenta en el siguiente envio.</p>
     */
    @Transactional
    public Node resolveForIngest(String externalId) {
        return nodeRepository.findByExternalId(externalId)
                .orElseGet(() -> {
                    if (!nodeProperties.autoRegister()) {
                        throw new ResourceNotFoundException("Nodo desconocido: " + externalId);
                    }
                    Node node = new Node(externalId);
                    node.setNombre("Nodo " + externalId);
                    node.setDescripcion("Alta automatica en la primera recepcion de telemetria.");
                    Node saved = nodeRepository.save(node);
                    log.info("Nodo dado de alta automaticamente: externalId={}, id={}", externalId, saved.getId());
                    return saved;
                });
    }

    @Transactional(readOnly = true)
    public Node requireByExternalId(String externalId) {
        return nodeRepository.findByExternalId(externalId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el nodo " + externalId));
    }

    @Transactional(readOnly = true)
    public List<Node> findAll() {
        return nodeRepository.findAllByOrderByExternalIdAsc();
    }

    public long offlineAfterMinutes() {
        return nodeProperties.offlineAfterMinutes();
    }
}
