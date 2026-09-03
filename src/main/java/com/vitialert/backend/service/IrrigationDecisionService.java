package com.vitialert.backend.service;

import com.vitialert.backend.domain.DecisionAction;
import com.vitialert.backend.domain.DecisionRecord;
import com.vitialert.backend.domain.DecisionSource;
import com.vitialert.backend.domain.Node;
import com.vitialert.backend.domain.TelemetryReading;
import com.vitialert.backend.dto.FeatureVector;
import com.vitialert.backend.dto.PredictionResponse;
import com.vitialert.backend.repository.DecisionRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Motor de decision de riego.
 *
 * <p>Prioridad definida para el proyecto:</p>
 * <pre>
 * decision del backend
 *      |
 *      +-- si existe y es confiable -> se usa la decision del backend
 *      |
 *      +-- si no                    -> se usa decision_riego_local (fallback)
 * </pre>
 *
 * <p>Hoy no existe ningun motor avanzado habilitado, por lo que la decision final es
 * SIEMPRE {@code decision_riego_local} y la maqueta se comporta exactamente igual que
 * antes. No se implementa ninguna IA ficticia ni logica meteorologica inventada.</p>
 */
@Service
public class IrrigationDecisionService {

    private static final Logger log = LoggerFactory.getLogger(IrrigationDecisionService.class);

    private final PredictionService predictionService;
    private final FeatureService featureService;
    private final DecisionRecordRepository decisionRecordRepository;

    public IrrigationDecisionService(PredictionService predictionService,
                                     FeatureService featureService,
                                     DecisionRecordRepository decisionRecordRepository) {
        this.predictionService = predictionService;
        this.featureService = featureService;
        this.decisionRecordRepository = decisionRecordRepository;
    }

    /**
     * Evalua la decision para la lectura recien recibida y la deja registrada.
     */
    @Transactional
    public DecisionOutcome decide(Node node, TelemetryReading reading) {
        boolean localDecision = reading.isLocalIrrigationDecision();

        Boolean backendDecision = null;
        String modelVersion = null;
        DecisionSource source = DecisionSource.LOCAL_FALLBACK;
        String motivo = "No hay decision de backend disponible: se replica decision_riego_local del ESP32.";

        // Mientras el modelo esta desactivado no se calculan features ni se hace ninguna
        // llamada de red: el camino caliente del POST se mantiene minimo.
        if (predictionService.isEnabled()) {
            FeatureVector features = featureService.computeFeatures(node, reading.getTimestampReceived(), reading);
            Optional<PredictionResponse> prediction = predictionService.predict(features);
            if (prediction.isPresent() && prediction.get().irrigate() != null) {
                backendDecision = prediction.get().irrigate();
                modelVersion = prediction.get().modelVersion();
                source = DecisionSource.ML_MODEL;
                motivo = "Decision tomada por el modelo de Ciencia de Datos"
                        + (prediction.get().probability() == null
                        ? "." : " (probabilidad=" + prediction.get().probability() + ").");
            } else {
                motivo = "El servicio de inferencia no devolvio una prediccion utilizable: "
                        + "se aplica el fallback a decision_riego_local.";
            }
        }

        boolean finalDecision = backendDecision != null ? backendDecision : localDecision;
        DecisionAction accion = resolveAction(finalDecision, reading.isValveOpen());

        decisionRecordRepository.save(new DecisionRecord(
                node,
                reading.getTimestampReceived(),
                reading.getDecisionRiegoLocal(),
                backendDecision,
                finalDecision,
                accion,
                motivo,
                source,
                modelVersion));

        log.debug("Decision nodo={} local={} backend={} final={} accion={} source={}",
                node.getExternalId(), reading.getDecisionRiegoLocal(), backendDecision,
                finalDecision, accion, source);

        return new DecisionOutcome(reading.getDecisionRiegoLocal(), backendDecision, finalDecision,
                accion, source, motivo, modelVersion);
    }

    private static DecisionAction resolveAction(boolean finalDecision, boolean valveCurrentlyOpen) {
        if (finalDecision && !valveCurrentlyOpen) {
            return DecisionAction.ABRIR;
        }
        if (!finalDecision && valveCurrentlyOpen) {
            return DecisionAction.CERRAR;
        }
        return DecisionAction.MANTENER;
    }
}
