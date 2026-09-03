package com.vitialert.backend.domain;

/** Accion derivada de comparar la decision final con el estado actual de la electrovalvula. */
public enum DecisionAction {
    ABRIR,
    CERRAR,
    MANTENER
}
