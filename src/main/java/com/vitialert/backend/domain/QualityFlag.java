package com.vitialert.backend.domain;

/**
 * Calidad asignada a una lectura de telemetria en el momento de la ingesta.
 *
 * <p>Ninguna de estas marcas modifica la decision de riego: son metadatos de
 * trazabilidad para el analisis de datos posterior.</p>
 */
public enum QualityFlag {

    /** Todos los valores presentes y dentro del rango operativo esperado. */
    VALID,

    /** Valores fisicamente posibles pero inconsistentes entre si (posible fuga, falta de caudal, sensor congelado). */
    SUSPECT,

    /** El sensor devuelve un valor imposible de interpretar (por ejemplo el ADC pegado a un extremo). */
    SENSOR_ERROR,

    /** Falta al menos una variable central del payload. */
    MISSING,

    /** Valor aceptado pero fuera del rango operativo plausible para vitivinicultura. */
    OUT_OF_RANGE
}
