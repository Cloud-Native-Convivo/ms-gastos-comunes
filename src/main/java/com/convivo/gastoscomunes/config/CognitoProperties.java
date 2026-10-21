package com.convivo.gastoscomunes.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de validación JWT de Amazon Cognito (residentes).
 *
 * <p>Mismas variables que usa el BFF (COGNITO_ISSUER / COGNITO_JWKS_URI /
 * COGNITO_APP_CLIENT_ID) para revalidar el access token de residentes de forma
 * independiente, igual que se hace con Entra ID para administración.</p>
 *
 * @param issuer   Issuer exacto del User Pool.
 * @param jwksUri  URL de las llaves públicas (JWKS) del User Pool.
 * @param clientId App Client ID del front de residentes (claim {@code client_id}).
 */
@ConfigurationProperties(prefix = "convivo.cognito")
public record CognitoProperties(String issuer, String jwksUri, String clientId) {

    /** Sin issuer, JWKS o client no se aceptan tokens de Cognito: solo Entra ID. */
    public boolean configurado() {
        return tieneTexto(issuer) && tieneTexto(jwksUri) && tieneTexto(clientId);
    }

    private static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}
