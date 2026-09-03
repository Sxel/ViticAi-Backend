package com.vitialert.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI vitiAlertOpenApi() {
        return new OpenAPI().info(new Info()
                .title("VitiAlert / Vitic-AI - Backend")
                .version("0.1.0")
                .description("""
                        Backend orquestador del sistema de soporte de decisiones para gestion hidrica
                        en vitivinicultura.

                        Responsabilidades: recibir y validar la telemetria del ESP32, persistirla de forma
                        inmutable, reconstruir los eventos de riego, construir features temporales por
                        timestamp real, integrar meteorologia y datos satelitales, consultar la inferencia
                        del modelo Python y devolver la decision de riego al nodo.

                        Mientras no exista un motor de decision avanzado habilitado, la valvula sigue
                        gobernada por decision_riego_local del propio ESP32.
                        """)
                .contact(new Contact().name("Tesis - Licenciatura en Analisis de Datos"))
                .license(new License().name("Uso academico")));
    }
}
