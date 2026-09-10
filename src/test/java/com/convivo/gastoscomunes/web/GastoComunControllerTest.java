package com.convivo.gastoscomunes.web;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.convivo.gastoscomunes.domain.GastoComun;
import com.convivo.gastoscomunes.domain.OrigenGasto;
import com.convivo.gastoscomunes.dto.GastoComunRequest;
import com.convivo.gastoscomunes.repository.GastoComunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prueba de integración de la API de Gastos Comunes: ownership por unidad
 * (propietario/residente solo su unidad; administrador/comité, todo) y
 * autorización por rol (crear cobros manuales requiere ADMINISTRADOR/COMITE).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class GastoComunControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GastoComunRepository gastoComunRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Long gastoUnidadA;

    @BeforeEach
    void seed() {
        GastoComun gasto = GastoComun.crear(
                "unidad-A302", "Cuota ordinaria septiembre", new BigDecimal("45000"), OrigenGasto.MANUAL, null, null);
        gastoUnidadA = gastoComunRepository.save(gasto).getId();
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/gastos-comunes")).andExpect(status().isUnauthorized());
    }

    @Test
    void administradorPuedeListarTodosLosGastos() throws Exception {
        mockMvc.perform(get("/api/v1/gastos-comunes")
                        .with(jwt().jwt(builder -> builder.claim("oid", "admin-1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void propietarioPuedeVerGastosDeSuPropiaUnidad() throws Exception {
        mockMvc.perform(get("/api/v1/gastos-comunes/unidad/unidad-A302")
                        .with(jwt().jwt(builder -> builder.claim("oid", "prop-1").claim("unidad_id", "unidad-A302"))
                                .authorities(new SimpleGrantedAuthority("ROLE_PROPIETARIO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].unidadId", is("unidad-A302")));
    }

    @Test
    void propietarioNoPuedeVerGastosDeOtraUnidad() throws Exception {
        mockMvc.perform(get("/api/v1/gastos-comunes/unidad/unidad-A302")
                        .with(jwt().jwt(builder -> builder.claim("oid", "prop-2").claim("unidad_id", "unidad-B101"))
                                .authorities(new SimpleGrantedAuthority("ROLE_PROPIETARIO"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void propietarioNoPuedeCrearCobrosManuales() throws Exception {
        String body = objectMapper.writeValueAsString(
                new GastoComunRequest("unidad-A302", "Multa", new BigDecimal("10000"), null));

        mockMvc.perform(post("/api/v1/gastos-comunes")
                        .with(jwt().jwt(builder -> builder.claim("oid", "prop-1").claim("unidad_id", "unidad-A302"))
                                .authorities(new SimpleGrantedAuthority("ROLE_PROPIETARIO")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorPuedeCrearCobroManual() throws Exception {
        String body = objectMapper.writeValueAsString(
                new GastoComunRequest("unidad-A302", "Multa", new BigDecimal("10000"), null));

        mockMvc.perform(post("/api/v1/gastos-comunes")
                        .with(jwt().jwt(builder -> builder.claim("oid", "admin-1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado", is("PENDIENTE")));
    }
}
