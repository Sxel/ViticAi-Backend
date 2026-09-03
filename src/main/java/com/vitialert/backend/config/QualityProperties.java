package com.vitialert.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Umbrales de los chequeos de calidad de sensor. Ninguno de ellos altera la decision
 * de riego: solamente producen {@code QualityFlag} y advertencias en el log.
 *
 * @param flowNoiseThresholdLMin caudal por debajo del cual se considera "sin flujo" (ruido del sensor)
 * @param frozenSensorEnabled    activa la deteccion de sensor de humedad congelado
 * @param frozenSensorMinutes    minutos sin variacion del valor RAW para sospechar sensor congelado
 * @param plausibleTempMinC      limite inferior operativo plausible (por debajo se marca OUT_OF_RANGE)
 * @param plausibleTempMaxC      limite superior operativo plausible
 * @param plausibleWindMaxKmh    viento maximo operativo plausible
 * @param adcMin                 valor RAW minimo del ADC (pegado a este valor = error de sensor)
 * @param adcMax                 valor RAW maximo del ADC de 12 bits del ESP32
 */
@ConfigurationProperties(prefix = "vitialert.quality")
public record QualityProperties(
        @DefaultValue("0.2") double flowNoiseThresholdLMin,
        @DefaultValue("true") boolean frozenSensorEnabled,
        @DefaultValue("60") long frozenSensorMinutes,
        @DefaultValue("-20.0") double plausibleTempMinC,
        @DefaultValue("55.0") double plausibleTempMaxC,
        @DefaultValue("120.0") double plausibleWindMaxKmh,
        @DefaultValue("0") int adcMin,
        @DefaultValue("4095") int adcMax
) {
}
