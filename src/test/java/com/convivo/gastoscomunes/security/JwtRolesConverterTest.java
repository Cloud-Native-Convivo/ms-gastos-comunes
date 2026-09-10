package com.convivo.gastoscomunes.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.convivo.gastoscomunes.config.EntraProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtRolesConverterTest {

    private Jwt jwtConClaim(Object valorRoles) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("oid", "usuario-1")
                .claim("roles", valorRoles)
                .build();
    }

    @Test
    void mapeaRolesDesdeArray() {
        EntraProperties props = new EntraProperties(null, null, null, "roles", ",", Map.of());
        JwtRolesConverter converter = new JwtRolesConverter(props);

        List<String> autoridades = converter.convert(jwtConClaim(List.of("administrador", "conserje"))).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(autoridades).containsExactlyInAnyOrder("ROLE_ADMINISTRADOR", "ROLE_CONSERJE");
    }

    @Test
    void mapeaRolesDesdeStringSeparadoYAplicaClaimMap() {
        EntraProperties props = new EntraProperties(null, null, null, "roles", ",", Map.of("admin", "administrador"));
        JwtRolesConverter converter = new JwtRolesConverter(props);

        List<String> autoridades = converter.convert(jwtConClaim("admin,propietario")).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(autoridades).containsExactlyInAnyOrder("ROLE_ADMINISTRADOR", "ROLE_PROPIETARIO");
    }

    @Test
    void ignoraValoresQueNoMapeanAUnRolConocido() {
        EntraProperties props = new EntraProperties(null, null, null, "roles", ",", Map.of());
        JwtRolesConverter converter = new JwtRolesConverter(props);

        List<String> autoridades = converter.convert(jwtConClaim("rol-inexistente")).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(autoridades).isEmpty();
    }
}
