package com.convivo.gastoscomunes.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puebla {@link UsuarioContexto} y el MDC de logging a partir del JWT ya
 * autenticado por Spring Security (Nivel 3 del flujo). Se registra
 * <b>después</b> de {@code BearerTokenAuthenticationFilter} (ver
 * {@code SecurityConfig}), así que solo corre cuando la petición ya tiene
 * una {@link Authentication} válida.
 *
 * <p>También compara el header {@code X-Usuario-Sub} (agregado por el BFF)
 * contra el claim {@code oid} del token: si difieren, alguien está llamando
 * al microservicio con un header inconsistente con su propio token, lo cual
 * se registra como advertencia de seguridad.</p>
 */
public class IdentityContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdentityContextFilter.class);

    private static final String HEADER_USUARIO_SUB = "X-Usuario-Sub";
    private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    private static final String MDC_CORRELATION_ID = "correlationId";
    private static final String MDC_USUARIO_SUB = "usuarioSub";

    private final UsuarioContexto usuarioContexto;

    public IdentityContextFilter(UsuarioContexto usuarioContexto) {
        this.usuarioContexto = usuarioContexto;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId = firstNonBlank(request.getHeader(HEADER_CORRELATION_ID), UUID.randomUUID().toString());
        usuarioContexto.setCorrelationId(correlationId);
        MDC.put(MDC_CORRELATION_ID, correlationId);

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
                poblarDesdeJwt(request, jwt, auth.getAuthorities());
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_USUARIO_SUB);
        }
    }

    private void poblarDesdeJwt(
            HttpServletRequest request, Jwt jwt, Iterable<? extends GrantedAuthority> autoridades) {
        String usuarioSub = jwt.getClaimAsString("oid");
        usuarioContexto.setUsuarioSub(usuarioSub);
        usuarioContexto.setUnidadId(jwt.getClaimAsString("unidad_id"));
        MDC.put(MDC_USUARIO_SUB, usuarioSub);

        Set<String> roles = new LinkedHashSet<>();
        for (GrantedAuthority autoridad : autoridades) {
            String nombre = autoridad.getAuthority();
            if (nombre.startsWith("ROLE_")) {
                roles.add(nombre.substring("ROLE_".length()).toLowerCase());
            }
        }
        if (roles.isEmpty()) {
            String headerRoles = request.getHeader("X-Usuario-Roles");
            if (headerRoles != null && !headerRoles.isBlank()) {
                for (String parte : headerRoles.split(",")) {
                    String limpio = parte.trim().toLowerCase();
                    if (!limpio.isBlank()) {
                        roles.add(limpio.equals("admin") ? "administrador" : limpio);
                    }
                }
            }
        }
        usuarioContexto.setRoles(roles);

        if (!roles.isEmpty() && !autoridades.iterator().hasNext()) {
            java.util.List<GrantedAuthority> nuevasAutoridades = roles.stream()
                    .map(r -> (GrantedAuthority) new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + r.toUpperCase()))
                    .toList();
            org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken nuevoAuth =
                    new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                            jwt, nuevasAutoridades, usuarioSub);
            SecurityContextHolder.getContext().setAuthentication(nuevoAuth);
        }

        String headerSub = request.getHeader(HEADER_USUARIO_SUB);
        if (headerSub != null && usuarioSub != null && !headerSub.equals(usuarioSub)) {
            log.warn(
                    "X-Usuario-Sub ({}) no coincide con el claim 'oid' del JWT ({}); "
                            + "se usa el valor del token, que es el validado criptográficamente",
                    headerSub,
                    usuarioSub);
        }
    }

    private static String firstNonBlank(String... valores) {
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                return valor;
            }
        }
        return null;
    }
}
