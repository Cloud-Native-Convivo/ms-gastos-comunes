package com.convivo.gastoscomunes.config;

import com.convivo.gastoscomunes.security.UsuarioContexto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Configuración de seguridad para el perfil {@code local}.
 *
 * <p>En desarrollo local no hay sesión MSAL en el frontend (MSAL no envía
 * Bearer a localhost), así que omitimos la validación JWT y confiamos en los
 * headers {@code X-Usuario-Roles} / {@code X-Usuario-Sub} que el BFF ya
 * propaga. Todas las rutas se autorizan sin token; {@link UsuarioContexto}
 * se puebla desde dichos headers para que la lógica de negocio
 * ({@code @PreAuthorize}, {@code GastoComunService}) siga funcionando igual.
 *
 * <p><b>NUNCA activo en producción</b>: la anotación {@code @Profile("local")}
 * lo excluye de cualquier despliegue que no sea el perfil local.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("local")
public class SecurityConfigLocal {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfigLocal.class);

    private final UsuarioContexto usuarioContexto;

    public SecurityConfigLocal(UsuarioContexto usuarioContexto) {
        this.usuarioContexto = usuarioContexto;
        log.warn("*** SecurityConfigLocal ACTIVO: validación JWT DESHABILITADA (solo para desarrollo local) ***");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(new HeaderIdentityFilter(usuarioContexto), AnonymousAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Filtro que puebla {@link UsuarioContexto} desde los headers que inyecta
     * el BFF ({@code X-Usuario-Roles}, {@code X-Usuario-Sub}) sin validar JWT.
     *
     * <p>El rol {@code admin} se normaliza a {@code administrador} para
     * mantener consistencia con el mapping que ya hace el BFF y el frontend
     * Angular ({@code authInterceptor}).
     */
    static class HeaderIdentityFilter extends OncePerRequestFilter {

        private final UsuarioContexto usuarioContexto;

        HeaderIdentityFilter(UsuarioContexto usuarioContexto) {
            this.usuarioContexto = usuarioContexto;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain chain) throws ServletException, IOException {

            String correlationId = firstNonBlank(
                    request.getHeader("X-Correlation-Id"), UUID.randomUUID().toString());
            usuarioContexto.setCorrelationId(correlationId);
            MDC.put("correlationId", correlationId);

            String rolesHeader = request.getHeader("X-Usuario-Roles");
            Set<String> roles = new LinkedHashSet<>();
            if (rolesHeader != null && !rolesHeader.isBlank()) {
                Arrays.stream(rolesHeader.split(","))
                        .map(String::trim)
                        .map(String::toLowerCase)
                        .filter(r -> !r.isBlank())
                        .forEach(r -> roles.add(r.equals("admin") ? "administrador" : r));
            }
            usuarioContexto.setRoles(roles);

            String sub = request.getHeader("X-Usuario-Sub");
            usuarioContexto.setUsuarioSub(sub);
            if (sub != null && !sub.isBlank()) {
                MDC.put("usuarioSub", sub);
            }

            String unidad = firstNonBlank(
                    request.getHeader("X-Usuario-Unidad"),
                    request.getHeader("X-Usuario-Unidad-Id"));
            usuarioContexto.setUnidadId(unidad);

            // Poner una autenticación en el SecurityContext para que
            // @PreAuthorize evalúe las autoridades de forma consistente (fail-closed si no tiene roles).
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                var authorities = roles.stream()
                        .map(r -> (org.springframework.security.core.GrantedAuthority)
                                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        "ROLE_" + r.toUpperCase()))
                        .toList();
                var principal = (sub != null && !sub.isBlank()) ? sub : "anonymous";
                var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }

            try {
                chain.doFilter(request, response);
            } finally {
                MDC.remove("correlationId");
                MDC.remove("usuarioSub");
            }
        }

        private static String firstNonBlank(String... valores) {
            for (String v : valores) {
                if (v != null && !v.isBlank()) return v;
            }
            return null;
        }
    }
}
