package com.convivo.gastoscomunes.config;

import com.convivo.gastoscomunes.security.IdentityContextFilter;
import com.convivo.gastoscomunes.security.JwtRolesConverter;
import com.convivo.gastoscomunes.security.UsuarioContexto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Nivel 3 de la cadena de validación JWT (ver diagrama de secuencia):
 * ms-gastos-comunes valida el Access Token de Entra ID de forma <b>autónoma
 * e independiente</b> del BFF — firma RS256 contra JWKS, issuer y audience
 * — y luego aplica RBAC de dominio (roles Convivo) y ownership sobre las
 * rutas de gastos comunes.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] RUTAS_PUBLICAS = {
        "/actuator/health",
        "/actuator/health/**",
        "/actuator/info",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
    };

    private final EntraProperties entra;
    private final JwtRolesConverter jwtRolesConverter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(EntraProperties entra, JwtRolesConverter jwtRolesConverter, ObjectMapper objectMapper) {
        this.entra = entra;
        this.jwtRolesConverter = jwtRolesConverter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, UsuarioContexto usuarioContexto, JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(RUTAS_PUBLICAS)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(JsonAuthEntryPoints.unauthorized(objectMapper)))
                .exceptionHandling(handling -> handling.accessDeniedHandler(JsonAuthEntryPoints.forbidden(objectMapper)))
                // Después del filtro que autentica el Bearer token: para cuando
                // corre IdentityContextFilter, SecurityContextHolder ya tiene el
                // Jwt validado (oid, roles) disponible.
                .addFilterAfter(new IdentityContextFilter(usuarioContexto), BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Decoder JWT propio: valida firma (JWKS de Entra), expiración, issuer
     * y audience. No reutiliza ninguna validación ya hecha por el BFF.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(entra.jwksUri()).build();

        OAuth2TokenValidator<Jwt> conIssuer = JwtValidators.createDefaultWithIssuer(entra.issuer());
        OAuth2TokenValidator<Jwt> conAudience = new JwtClaimValidator<List<String>>(
                "aud", audiences -> audiences != null && audiences.contains(entra.audience()));

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(conIssuer, conAudience));
        return decoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwtRolesConverter);
        converter.setPrincipalClaimName("oid");
        return converter;
    }
}
