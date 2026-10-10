package com.convivo.gastoscomunes.messaging.fanout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.convivo.gastoscomunes.domain.SolicitudJob;
import com.convivo.gastoscomunes.domain.SolicitudJobRepository;
import com.convivo.gastoscomunes.repository.GastoComunRepository;
import com.convivo.gastoscomunes.service.PdfGastosGeneradorService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

class FanoutConsumersTest {

    private SolicitudJobRepository jobRepository;
    private GastoComunRepository gastoRepository;
    private PdfGastosGeneradorService pdfGeneradorService;
    private ObjectMapper objectMapper;

    private PdfGastosConsumer pdfConsumer;
    private NotificacionGastosConsumer notificacionConsumer;

    @BeforeEach
    void setUp() {
        jobRepository = mock(SolicitudJobRepository.class);
        gastoRepository = mock(GastoComunRepository.class);
        pdfGeneradorService = mock(PdfGastosGeneradorService.class);
        objectMapper = new ObjectMapper();

        pdfConsumer = new PdfGastosConsumer(jobRepository, gastoRepository, pdfGeneradorService, objectMapper);
        notificacionConsumer = new NotificacionGastosConsumer(objectMapper);
    }

    @Test
    void debeProcesarPdfYCompletarJob() throws Exception {
        when(jobRepository.findById("ticket-100")).thenReturn(Optional.empty());
        when(jobRepository.save(any(SolicitudJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gastoRepository.findByUnidadId(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        JsonNode payload = objectMapper.readTree("""
                {
                    "ticket_id": "ticket-100",
                    "unidad_id": "204-B",
                    "mes": "Agosto 2026",
                    "usuario_id": "usr-test"
                }
                """);

        pdfConsumer.procesarSolicitudPdf(payload);

        verify(pdfGeneradorService).generarComprobante(any(), any(), any(), any(), any(), any());
        verify(jobRepository, org.mockito.Mockito.atLeastOnce()).save(any(SolicitudJob.class));
    }

    @Test
    void debeProcesarNotificacionSinErrores() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                    "ticket_id": "ticket-100",
                    "email": "vecino@convivo.cl",
                    "unidad_id": "204-B"
                }
                """);

        notificacionConsumer.procesarNotificacion(payload);
        assertThat(payload.get("ticket_id").asText()).isEqualTo("ticket-100");
    }
}
