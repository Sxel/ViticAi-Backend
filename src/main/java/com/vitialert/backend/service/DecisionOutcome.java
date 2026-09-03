package com.vitialert.backend.service;

import com.vitialert.backend.domain.DecisionAction;
import com.vitialert.backend.domain.DecisionSource;

/**
 * Resultado de la evaluacion de riego.
 *
 * @param decisionLocal   lo que decidio el ESP32 por su cuenta
 * @param decisionBackend lo que decidio el backend, o null si todavia no hay motor avanzado
 * @param decisionFinal   lo que efectivamente se ordena al nodo
 */
public record DecisionOutcome(Boolean decisionLocal,
                              Boolean decisionBackend,
                              boolean decisionFinal,
                              DecisionAction accion,
                              DecisionSource source,
                              String motivo,
                              String modelVersion) {
}
