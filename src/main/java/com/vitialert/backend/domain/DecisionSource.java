package com.vitialert.backend.domain;

/** Origen efectivo de la decision final enviada al ESP32. */
public enum DecisionSource {

    /** No hay decision de backend disponible: se replica la decision local del ESP32. */
    LOCAL_FALLBACK,

    /** Reglas de negocio evaluadas por el backend (todavia no implementadas). */
    BACKEND_RULES,

    /** Inferencia entregada por el servicio Python de Machine Learning. */
    ML_MODEL,

    /** Intervencion manual de un operador. */
    MANUAL
}
