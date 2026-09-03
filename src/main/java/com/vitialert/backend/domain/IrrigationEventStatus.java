package com.vitialert.backend.domain;

/** Estado del ciclo de vida de un evento de riego reconstruido a partir de la telemetria. */
public enum IrrigationEventStatus {

    /** La electrovalvula sigue abierta: el evento todavia no tiene fin ni volumen aplicado. */
    OPEN,

    /** Evento cerrado con volumen aplicado calculado de forma confiable. */
    CLOSED,

    /** Evento cerrado, pero el volumen aplicado es dudoso (reinicio del contador del ESP32 o dato faltante). */
    CLOSED_WITH_WARNING
}
