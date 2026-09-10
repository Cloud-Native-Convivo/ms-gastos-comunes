package com.convivo.gastoscomunes.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de validación JWT de Microsoft Entra ID.
 *
 * <p>Refleja deliberadamente las mismas variables que usa el BFF
 * (ENTRA_TENANT_ID / ENTRA_API_CLIENT_ID / CLAIM_DE_ROL / CLAIM_MAPEO) para
 * que ambos componentes validen el mismo tenant y token de forma
 * independiente ("revalidar siempre, no confiar solo en el Gateway").</p>
 *
 * @param issuer         Issuer estricto del tenant Entra (v2.0).
 * @param jwksUri        URL de descubrimiento de llaves públicas (JWKS).
 * @param audience       Client ID de la API de Convivo registrada en Entra.
 * @param claimDeRol     Claim del que se leen los roles del usuario.
 * @param claimSeparador Separador si el claim viene como string plano.
 * @param claimMap       Mapeo valor-del-claim -> rol de negocio (opcional).
 */
@ConfigurationProperties(prefix = "convivo.entra")
public record EntraProperties(
        String issuer,
        String jwksUri,
        String audience,
        String claimDeRol,
        String claimSeparador,
        Map<String, String> claimMap) {

    public EntraProperties {
        if (claimDeRol == null || claimDeRol.isBlank()) {
            claimDeRol = "roles";
        }
        if (claimSeparador == null || claimSeparador.isBlank()) {
            claimSeparador = ",";
        }
        if (claimMap == null) {
            claimMap = Map.of();
        }
    }
}
