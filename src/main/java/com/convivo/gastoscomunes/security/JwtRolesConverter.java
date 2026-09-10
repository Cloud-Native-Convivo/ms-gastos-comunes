package com.convivo.gastoscomunes.security;

import com.convivo.gastoscomunes.config.EntraProperties;
import com.convivo.gastoscomunes.domain.RolConvivo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Extrae los roles de negocio del JWT validado y los expone como
 * {@link GrantedAuthority} con prefijo {@code ROLE_} (para usar con
 * {@code hasRole(...)} / {@code @PreAuthorize}).
 *
 * <p>Lee el claim configurado (por defecto {@code roles}), acepta tanto un
 * array como un string separado (ej. {@code "admin,comite"}) y aplica el
 * mismo mapeo valor-del-claim -> rol de negocio que usa el BFF
 * (CLAIM_DE_ROL / CLAIM_SEPARADOR / CLAIM_MAPEO), porque el claim/App Role
 * definitivo de Entra todavía no está definido.</p>
 */
@Component
public class JwtRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final EntraProperties entra;

    public JwtRolesConverter(EntraProperties entra) {
        this.entra = entra;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Object raw = jwt.getClaims().get(entra.claimDeRol());
        List<String> valores = valoresCrudos(raw);

        Set<GrantedAuthority> autoridades = new LinkedHashSet<>();
        for (String valor : valores) {
            String mapeado = entra.claimMap().getOrDefault(valor, valor);
            RolConvivo.desde(mapeado)
                    .ifPresent(rol -> autoridades.add(
                            new SimpleGrantedAuthority("ROLE_" + rol.name())));
        }
        return autoridades;
    }

    private List<String> valoresCrudos(Object raw) {
        List<String> valores = new ArrayList<>();
        if (raw instanceof Collection<?> coleccion) {
            for (Object item : coleccion) {
                if (item != null) {
                    valores.add(String.valueOf(item));
                }
            }
        } else if (raw instanceof String texto && !texto.isBlank()) {
            String separador = entra.claimSeparador();
            for (String parte : texto.split(java.util.regex.Pattern.quote(separador))) {
                if (!parte.isBlank()) {
                    valores.add(parte.trim());
                }
            }
        }
        return valores.stream()
                .map(v -> v.toLowerCase(Locale.ROOT))
                .toList();
    }
}
