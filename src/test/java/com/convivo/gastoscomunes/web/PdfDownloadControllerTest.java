package com.convivo.gastoscomunes.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.convivo.gastoscomunes.service.PdfStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PdfDownloadControllerTest {

    private MockMvc mockMvc;
    private PdfStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new PdfStorageService();
        mockMvc = MockMvcBuilders.standaloneSetup(new PdfDownloadController(storage)).build();
    }

    @Test
    void debeDescargarPdfExistente() throws Exception {
        storage.guardarPdf("ticket-abc", "%PDF-1.4 test".getBytes());

        mockMvc.perform(get("/api/v1/gastos/pdf/ticket-abc/descargar"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"gastos-comunes-ticket-abc.pdf\""));
    }

    @Test
    void debeRetornar404SiPdfNoExiste() throws Exception {
        mockMvc.perform(get("/api/v1/gastos/pdf/inexistente/descargar"))
                .andExpect(status().isNotFound());
    }
}
