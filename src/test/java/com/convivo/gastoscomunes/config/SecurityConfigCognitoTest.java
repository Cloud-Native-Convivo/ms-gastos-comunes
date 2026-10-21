package com.convivo.gastoscomunes.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Reglas de aceptación de tokens de Cognito (residentes), espejo de
 * jwt-cognito.strategy.ts del BFF. Partición de equivalencia por claim:
 * cada caso de rechazo altera un único claim respecto del token válido.
 */
class SecurityConfigCognitoTest {

    private static final String ISSUER = "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_prueba";
    private static final CognitoProperties COGNITO =
            new CognitoProperties(ISSUER, ISSUER + "/.well-known/jwks.json", "cliente-front");

    private static Jwt token(Consumer<Jwt.Builder> ajuste) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(ISSUER)
                .subject("residente-1")
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("token_use", "access")
                .claim("client_id", "cliente-front");
        ajuste.accept(builder);
        return builder.build();
    }

    private static boolean esValido(Jwt jwt) {
        return !SecurityConfig.validadorCognito(COGNITO).validate(jwt).hasErrors();
    }

    @Test
    void aceptaAccessTokenDelClientDelFront() {
        assertThat(esValido(token(b -> {}))).isTrue();
    }

    @Test
    void rechazaIdToken() {
        assertThat(esValido(token(b -> b.claim("token_use", "id")))).isFalse();
    }

    @Test
    void rechazaTokenDeOtroClient() {
        assertThat(esValido(token(b -> b.claim("client_id", "otro-client")))).isFalse();
    }

    @Test
    void rechazaTokenDeOtroPool() {
        assertThat(esValido(token(b -> b.issuer("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_otro"))))
                .isFalse();
    }

    @Test
    void rechazaTokenVencido() {
        assertThat(esValido(token(b -> b.issuedAt(Instant.now().minusSeconds(7200))
                        .expiresAt(Instant.now().minusSeconds(3600)))))
                .isFalse();
    }

    @Test
    void todoUsuarioDeCognitoEsResidenteAunqueTraigaGrupos() {
        JwtAuthenticationToken autenticacion = (JwtAuthenticationToken) SecurityConfig.conversorCognito()
                .convert(token(b -> b.claim("cognito:groups", List.of("admin"))));

        // Spring Security 7 agrega además la autoridad de factor FACTOR_BEARER, que no es un rol.
        assertThat(autenticacion.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .filteredOn(autoridad -> autoridad.startsWith("ROLE_"))
                .containsExactly("ROLE_RESIDENTE");
        assertThat(autenticacion.getName()).isEqualTo("residente-1");
    }

    @Test
    void sinVariablesNoSeAceptaCognito() {
        assertThat(new CognitoProperties("", null, " ").configurado()).isFalse();
        assertThat(COGNITO.configurado()).isTrue();
    }
}
