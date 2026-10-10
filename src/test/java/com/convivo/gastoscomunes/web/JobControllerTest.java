package com.convivo.gastoscomunes.web;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.convivo.gastoscomunes.domain.SolicitudJob;
import com.convivo.gastoscomunes.domain.SolicitudJobRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
    "convivo.security.entra.jwks-uri=http://localhost",
    "convivo.security.entra.issuer-uri-v1=http://localhost",
    "convivo.security.entra.issuer-uri-v2=http://localhost",
    "convivo.security.entra.audience=aud",
    "convivo.security.entra.roles-claim=roles",
    "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    @Autowired
    private SolicitudJobRepository solicitudJobRepository;

    @BeforeEach
    void setUp() {
        SolicitudJob job = new SolicitudJob(
                "ticket-123",
                "GASTOS",
                "REGISTRAR_GASTO",
                "user-admin-1",
                "COMPLETADO",
                "{\"id\":10,\"monto\":50000}",
                null,
                Instant.now(),
                Instant.now()
        );
        solicitudJobRepository.save(job);
    }

    @Test
    void debeRetornarEstadoDeJobPorTicketId() throws Exception {
        mockMvc.perform(get("/api/v1/gastos/jobs/ticket-123")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket_id", is("ticket-123")))
                .andExpect(jsonPath("$.modulo", is("GASTOS")))
                .andExpect(jsonPath("$.estado", is("COMPLETADO")));
    }

    @Test
    void debeRetornar404SiJobNoExiste() throws Exception {
        mockMvc.perform(get("/api/v1/gastos/jobs/ticket-inexistente")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))))
                .andExpect(status().isNotFound());
    }
}
