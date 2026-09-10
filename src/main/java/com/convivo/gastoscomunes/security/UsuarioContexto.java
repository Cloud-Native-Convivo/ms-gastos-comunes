package com.convivo.gastoscomunes.security;

import java.util.Set;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

/**
 * Identidad del usuario autenticado para la petición HTTP actual.
 *
 * <p>Se puebla en {@link IdentityContextFilter} <b>a partir del JWT ya
 * validado</b> (claim {@code oid} y roles del token), no de los headers
 * {@code X-Usuario-Sub} / {@code X-Usuario-Roles} que agrega el BFF: el
 * token es la fuente de verdad porque está firmado, mientras que los
 * headers son solo un atajo de conveniencia que el BFF ya calculó a partir
 * del mismo token. El filtro registra una advertencia si el header no
 * coincide con el claim (posible bug de propagación o llamada directa al
 * microservicio sin pasar por el BFF).</p>
 */
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class UsuarioContexto {

    private String usuarioSub;
    private Set<String> roles = Set.of();
    private String unidadId;
    private String correlationId;

    public String getUsuarioSub() {
        return usuarioSub;
    }

    public void setUsuarioSub(String usuarioSub) {
        this.usuarioSub = usuarioSub;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles == null ? Set.of() : roles;
    }

    public boolean tieneRol(String rol) {
        return roles.contains(rol.toLowerCase());
    }

    /** {@code true} si el usuario administra el condominio completo (admin/comité). */
    public boolean esGestorCondominio() {
        return tieneRol("administrador") || tieneRol("comite");
    }

    public String getUnidadId() {
        return unidadId;
    }

    public void setUnidadId(String unidadId) {
        this.unidadId = unidadId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    /**
     * Un usuario puede ver/operar sobre {@code unidadId} si administra el
     * condominio, o si esa es su propia unidad (claim {@code unidad_id},
     * todavía no definido de forma consistente en Entra — ver README).
     */
    public boolean puedeOperarSobreUnidad(String unidadId) {
        if (esGestorCondominio()) {
            return true;
        }
        return unidadId != null && unidadId.equals(this.unidadId);
    }
}
