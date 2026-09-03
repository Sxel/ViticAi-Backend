package com.vitialert.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Traza de cada decision de riego devuelta al ESP32.
 *
 * <p>Guardar las tres decisiones (local, backend y final) permite auditar el sistema
 * y comparar el comportamiento del firmware contra el del modelo cuando este exista.</p>
 */
@Entity
@Table(name = "decision_record")
public class DecisionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "node_id", nullable = false)
    private Node node;

    @Column(name = "decision_timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "decision_local")
    private Boolean decisionLocal;

    /** Decision calculada por el backend. Null mientras no exista motor avanzado disponible. */
    @Column(name = "decision_backend")
    private Boolean decisionBackend;

    @Column(name = "decision_final", nullable = false)
    private boolean decisionFinal;

    @Enumerated(EnumType.STRING)
    @Column(name = "accion", nullable = false, length = 15)
    private DecisionAction accion;

    @Column(name = "motivo", length = 500)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 25)
    private DecisionSource source;

    @Column(name = "model_version", length = 60)
    private String modelVersion;

    protected DecisionRecord() {
        // requerido por JPA
    }

    public DecisionRecord(Node node,
                          Instant timestamp,
                          Boolean decisionLocal,
                          Boolean decisionBackend,
                          boolean decisionFinal,
                          DecisionAction accion,
                          String motivo,
                          DecisionSource source,
                          String modelVersion) {
        this.node = node;
        this.timestamp = timestamp;
        this.decisionLocal = decisionLocal;
        this.decisionBackend = decisionBackend;
        this.decisionFinal = decisionFinal;
        this.accion = accion;
        this.motivo = motivo;
        this.source = source;
        this.modelVersion = modelVersion;
    }

    public Long getId() {
        return id;
    }

    public Node getNode() {
        return node;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Boolean getDecisionLocal() {
        return decisionLocal;
    }

    public Boolean getDecisionBackend() {
        return decisionBackend;
    }

    public boolean isDecisionFinal() {
        return decisionFinal;
    }

    public DecisionAction getAccion() {
        return accion;
    }

    public String getMotivo() {
        return motivo;
    }

    public DecisionSource getSource() {
        return source;
    }

    public String getModelVersion() {
        return modelVersion;
    }
}
