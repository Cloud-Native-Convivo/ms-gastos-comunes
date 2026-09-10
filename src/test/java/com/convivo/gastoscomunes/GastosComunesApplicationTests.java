package com.convivo.gastoscomunes;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Smoke test: el contexto de Spring levanta correctamente en el perfil local (H2, sin Rabbit/Eureka reales). */
@SpringBootTest
@ActiveProfiles("local")
class GastosComunesApplicationTests {

    @Test
    void contextLoads() {}
}
