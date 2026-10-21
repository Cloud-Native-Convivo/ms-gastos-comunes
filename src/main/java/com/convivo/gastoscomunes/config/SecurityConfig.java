package com.convivo.gastoscomunes.config;

import com.convivo.gastoscomunes.domain.RolConvivo;
import com.convivo.gastoscomunes.security.IdentityContextFilter;
import com.convivo.gastoscomunes.security.JwtRolesConverter;
import com.convivo.gastoscomunes.security.UsuarioContexto;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import org.springframework.context.annotation.Profile;

/**
 * Nivel 3 de la cadena de validación JWT (ver diagrama de secuencia):
 * ms-gastos-comunes valida el Access Token de forma <b>autónoma
 * e independiente</b> del BFF — firma RS256 contra JWKS, issuer y audience
 * — y luego aplica RBAC de dominio (roles Convivo) y ownership sobre las
 * rutas de gastos comunes. Acepta los dos issuers del proyecto: Entra ID
 * (administración) y Cognito (residentes), igual que el BFF.
 *
 * <p>Este bean NO actúa en el perfil {@code local} (ver {@code SecurityConfigLocal}).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("!local")
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
    private final CognitoProperties cognito;
    private final JwtRolesConverter jwtRolesConverter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
            EntraProperties entra,
            CognitoProperties cognito,
            JwtRolesConverter jwtRolesConverter,
            ObjectMapper objectMapper) {
        this.entra = entra;
        this.cognito = cognito;
        this.jwtRolesConverter = jwtRolesConverter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            UsuarioContexto usuarioContexto,
            AuthenticationManagerResolver<HttpServletRequest> resolverPorIssuer)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(RUTAS_PUBLICAS)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationManagerResolver(resolverPorIssuer)
                        .authenticationEntryPoint(JsonAuthEntryPoints.unauthorized(objectMapper)))
                .exceptionHandling(handling -> handling.accessDeniedHandler(JsonAuthEntryPoints.forbidden(objectMapper)))
                // Después del filtro que autentica el Bearer token: para cuando
                // corre IdentityContextFilter, SecurityContextHolder ya tiene el
                // Jwt validado (oid, roles) disponible.
                .addFilterAfter(new IdentityContextFilter(usuarioContexto), BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Elige el validador según el issuer del token: Entra ID (administración)
     * o Cognito (residentes); un issuer desconocido se rechaza con 401. El
     * issuer se lee sin verificar solo para elegir el validador: cada uno
     * vuelve a verificar firma e issuer por su cuenta.
     */
    @Bean
    public AuthenticationManagerResolver<HttpServletRequest> resolverPorIssuer(
            JwtDecoder jwtDecoder, JwtAuthenticationConverter jwtAuthenticationConverter) {
        Map<String, AuthenticationManager> porIssuer = new HashMap<>();
        AuthenticationManager entraManager = proveedor(jwtDecoder, jwtAuthenticationConverter)::authenticate;
        issuersEntra().forEach(issuer -> porIssuer.put(issuer, entraManager));
        if (cognito.configurado()) {
            porIssuer.put(cognito.issuer(), proveedor(decoderCognito(cognito), conversorCognito())::authenticate);
        }
        return new JwtIssuerAuthenticationManagerResolver(porIssuer::get);
    }

    /**
     * Decoder JWT propio: valida firma (JWKS de Entra), expiración, issuer
     * y audience. No reutiliza ninguna validación ya hecha por el BFF.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(entra.jwksUri()).build();

        Set<String> issuersValidos = issuersEntra();
        OAuth2TokenValidator<Jwt> conIssuer = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), new JwtClaimValidator<String>("iss", issuersValidos::contains));
        // Entra ID emite "aud" como el App ID URI (api://<clientId>), no el
        // clientId plano configurado en ENTRA_API_CLIENT_ID/ENTRA_AUDIENCE;
        // el BFF ya acepta ambas formas (ver jwt.strategy.ts), este validador
        // debe hacer lo mismo o rechaza tokens validos con 401.
        String audienceLimpia = entra.audience().replaceFirst("^api://", "");
        String audienceConPrefijo = "api://" + audienceLimpia;
        OAuth2TokenValidator<Jwt> conAudience = new JwtClaimValidator<List<String>>(
                "aud",
                audiences -> audiences != null
                        && (audiences.contains(audienceLimpia) || audiences.contains(audienceConPrefijo)));

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

    /**
     * Mismas reglas que el BFF (jwt-cognito.strategy.ts): firma RS256 contra
     * el JWKS del pool, issuer exacto y solo access tokens ({@code token_use})
     * del client del front de residentes ({@code client_id}). El ID token
     * identifica al usuario ante el front, pero no autoriza llamadas a la API.
     */
    static OAuth2TokenValidator<Jwt> validadorCognito(CognitoProperties cognito) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(cognito.issuer()),
                new JwtClaimValidator<String>("token_use", "access"::equals),
                new JwtClaimValidator<String>("client_id", cognito.clientId()::equals),
                new JwtClaimValidator<String>("sub", sub -> sub != null && !sub.isBlank()));
    }

    /**
     * Todo usuario de Cognito es residente, igual que en el BFF
     * (IdentityMapper.toUsuarioCognito): sus grupos no elevan privilegios.
     * La unidad llega por el header X-Usuario-Unidad (ver IdentityContextFilter).
     */
    static JwtAuthenticationConverter conversorCognito() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                jwt -> List.of(new SimpleGrantedAuthority("ROLE_" + RolConvivo.RESIDENTE.name())));
        converter.setPrincipalClaimName("sub");
        return converter;
    }

    private static JwtDecoder decoderCognito(CognitoProperties cognito) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(cognito.jwksUri()).build();
        decoder.setJwtValidator(validadorCognito(cognito));
        return decoder;
    }

    private static JwtAuthenticationProvider proveedor(JwtDecoder decoder, JwtAuthenticationConverter conversor) {
        JwtAuthenticationProvider proveedor = new JwtAuthenticationProvider(decoder);
        proveedor.setJwtAuthenticationConverter(conversor);
        return proveedor;
    }

    // Entra ID emite v1.0 access tokens con iss="https://sts.windows.net/{tid}/"
    // aunque el issuer configurado (ENTRA_ISSUER) sea el v2.0
    // ("https://login.microsoftonline.com/{tid}/v2.0"); el BFF ya tolera
    // ambos formatos (ver jwt.strategy.ts), este validador debe hacer lo
    // mismo o rechaza tokens reales con 401 "Token ausente, invalido o expirado".
    private Set<String> issuersEntra() {
        Matcher tenantMatcher = Pattern.compile("microsoftonline\\.com/([^/]+)/").matcher(entra.issuer());
        return tenantMatcher.find()
                ? Set.of(entra.issuer(), "https://sts.windows.net/" + tenantMatcher.group(1) + "/")
                : Set.of(entra.issuer());
    }
}
