package com.convivo.gastoscomunes.web;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("local")
@TestPropertySource(properties = {
    "spring.flyway.enabled=false"
})
class GastoComunSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GastoComunRepository gastoComunRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seed() {
        GastoComun gasto1 = GastoComun.crear(
                "unidad-101", "Gasto Unidad 101", new BigDecimal("30000"), OrigenGasto.MANUAL, null, null);
        GastoComun gasto2 = GastoComun.crear(
                "unidad-202", "Gasto Unidad 202", new BigDecimal("50000"), OrigenGasto.MANUAL, null, null);
        gastoComunRepository.save(gasto1);
        gastoComunRepository.save(gasto2);
    }

    @Test
    void residenteNoPuedeCrearCobroManual_retorna403() throws Exception {
        String body = objectMapper.writeValueAsString(
                new GastoComunRequest("unidad-101", "Cobro Ilegal", new BigDecimal("20000"), null));

        mockMvc.perform(post("/api/v1/gastos-comunes")
                        .header("X-Usuario-Roles", "residente")
                        .header("X-Usuario-Sub", "user-res-1")
                        .header("X-Usuario-Unidad", "unidad-101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorPuedeCrearCobroManual_retorna201() throws Exception {
        String body = objectMapper.writeValueAsString(
                new GastoComunRequest("unidad-101", "Cobro Admin", new BigDecimal("25000"), null));

        mockMvc.perform(post("/api/v1/gastos-comunes")
                        .header("X-Usuario-Roles", "administrador")
                        .header("X-Usuario-Sub", "admin-sub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado", is("PENDIENTE")));
    }

    @Test
    void residenteListandoGastos_veSoloGastosDeSuUnidad() throws Exception {
        mockMvc.perform(get("/api/v1/gastos-comunes")
                        .header("X-Usuario-Roles", "residente")
                        .header("X-Usuario-Sub", "user-res-1")
                        .header("X-Usuario-Unidad", "unidad-101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[*].unidadId", everyItem(is("unidad-101"))));
    }

    @Test
    void residenteConsultandoUnidadAjena_retorna403() throws Exception {
        mockMvc.perform(get("/api/v1/gastos-comunes/unidad/unidad-202")
                        .header("X-Usuario-Roles", "residente")
                        .header("X-Usuario-Sub", "user-res-1")
                        .header("X-Usuario-Unidad", "unidad-101"))
                .andExpect(status().isForbidden());
    }

    @Test
    void peticionSinRoles_enEndpointProtegido_retorna403() throws Exception {
        String body = objectMapper.writeValueAsString(
                new GastoComunRequest("unidad-101", "Cobro Sin Rol", new BigDecimal("10000"), null));

        mockMvc.perform(post("/api/v1/gastos-comunes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void residenteSinUnidadAsociada_retorna403() throws Exception {
        mockMvc.perform(get("/api/v1/gastos-comunes")
                        .header("X-Usuario-Roles", "residente")
                        .header("X-Usuario-Sub", "user-res-1"))
                .andExpect(status().isForbidden());
    }
}
