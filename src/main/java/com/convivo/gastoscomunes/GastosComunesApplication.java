package com.convivo.gastoscomunes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada de ms-gastos-comunes.
 *
 * <p>Microservicio de dominio "Gastos Comunes" de Convivo: expone la API
 * REST de cobros, cuotas y pagos detrás del BFF (Nivel 3 de la cadena de
 * validación JWT: RBAC de dominio + ownership) y consume, de forma
 * asíncrona y transaccional (patrón Outbox/Inbox), los eventos de reservas
 * publicados por ms-espacios-comunes vía RabbitMQ (Amazon MQ).</p>
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@ConfigurationPropertiesScan
public class GastosComunesApplication {

    public static void main(String[] args) {
        SpringApplication.run(GastosComunesApplication.class, args);
    }
}
