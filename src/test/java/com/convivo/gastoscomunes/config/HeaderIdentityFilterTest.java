package com.convivo.gastoscomunes.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.convivo.gastoscomunes.security.UsuarioContexto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Filtro de identidad por headers del perfil {@code local} (sin validación JWT). */
class HeaderIdentityFilterTest {

    private final UsuarioContexto usuario = new UsuarioContexto();
    private final SecurityConfigLocal.HeaderIdentityFilter filtro =
            new SecurityConfigLocal.HeaderIdentityFilter(usuario);

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void tomaIdentidadDeLosHeadersYNormalizaAdmin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Usuario-Roles", " Admin , residente,, ");
        request.addHeader("X-Usuario-Sub", "sub-1");
        request.addHeader("X-Correlation-Id", "corr-1");
        MockFilterChain cadena = new MockFilterChain();

        filtro.doFilter(request, new MockHttpServletResponse(), cadena);

        assertThat(cadena.getRequest()).isSameAs(request);
        assertThat(usuario.getRoles()).containsExactly("administrador", "residente");
        assertThat(usuario.getUsuarioSub()).isEqualTo("sub-1");
        assertThat(usuario.getCorrelationId()).isEqualTo("corr-1");
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getName()).isEqualTo("sub-1");
        assertThat(auth.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMINISTRADOR", "ROLE_RESIDENTE");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void sinHeadersUsaValoresDeDesarrollo() throws Exception {
        filtro.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(usuario.getRoles()).containsExactly("administrador");
        assertThat(usuario.getUsuarioSub()).isEqualTo("dev-user-local");
        assertThat(usuario.getCorrelationId()).isNotBlank();
    }

    @Test
    void respetaUnaAutenticacionPrevia() throws Exception {
        var previa = new UsernamePasswordAuthenticationToken("ya-autenticado", null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(previa);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Usuario-Roles", "  ");

        filtro.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(previa);
    }
}
