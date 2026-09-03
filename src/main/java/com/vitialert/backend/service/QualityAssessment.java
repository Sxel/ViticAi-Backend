package com.vitialert.backend.service;

import com.vitialert.backend.domain.QualityFlag;

/**
 * Resultado del control de calidad de una lectura.
 *
 * @param flag  marca resultante
 * @param notes detalle legible de todas las anomalias detectadas (null si no hay ninguna)
 */
public record QualityAssessment(QualityFlag flag, String notes) {

    public static QualityAssessment valid() {
        return new QualityAssessment(QualityFlag.VALID, null);
    }
}
