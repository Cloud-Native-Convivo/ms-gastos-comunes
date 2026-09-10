package com.convivo.gastoscomunes.messaging.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.convivo.gastoscomunes.messaging.inbox.dto.ReservaEspacioCreadaEvent;
import com.convivo.gastoscomunes.service.GastoComunService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Cubre las tres ramas del patrón Inbox del diagrama de saga:
 * duplicado / inválido (compensación) / procesado con éxito.
 */
@ExtendWith(MockitoExtension.class)
class ReservaCreadaInboxServiceTest {

    @Mock
    private InboxEventoRepository inboxRepository;

    @Mock
    private GastoComunService gastoComunService;

    @InjectMocks
    private ReservaCreadaInboxService service;

    private ReservaEspacioCreadaEvent eventoValido() {
        return new ReservaEspacioCreadaEvent(
                "evt-1", "reserva_espacio_creada", "reserva-1", "espacio-1", "unidad-A302", "usuario-sub-1",
                "Reserva Espacio", new BigDecimal("15000"), Instant.now());
    }

    @Test
    void descartaEventoDuplicado() {
        when(inboxRepository.existsById("evt-1")).thenReturn(true);

        ResultadoProcesamiento resultado = service.procesar(eventoValido());

        assertThat(resultado).isEqualTo(ResultadoProcesamiento.DUPLICADO);
        verify(inboxRepository, never()).save(any());
        verify(gastoComunService, never()).crearDesdeReserva(any(), any(), any(), any());
    }

    @Test
    void marcaInvalidoCuandoFaltaUnidadId() {
        ReservaEspacioCreadaEvent invalido = new ReservaEspacioCreadaEvent(
                "evt-2", "reserva_espacio_creada", "reserva-2", "espacio-1", null, "usuario-sub-1",
                "Reserva Espacio", new BigDecimal("15000"), Instant.now());
        when(inboxRepository.existsById("evt-2")).thenReturn(false);

        ResultadoProcesamiento resultado = service.procesar(invalido);

        assertThat(resultado).isEqualTo(ResultadoProcesamiento.INVALIDO);
        verify(inboxRepository, never()).save(any());
        verify(gastoComunService, never()).crearDesdeReserva(any(), any(), any(), any());
    }

    @Test
    void procesaYCreaGastoComunCuandoEsValidoYNoEsDuplicado() {
        ReservaEspacioCreadaEvent evento = eventoValido();
        when(inboxRepository.existsById("evt-1")).thenReturn(false);

        ResultadoProcesamiento resultado = service.procesar(evento);

        assertThat(resultado).isEqualTo(ResultadoProcesamiento.PROCESADO);
        verify(inboxRepository, times(1)).save(any(InboxEvento.class));
        verify(gastoComunService, times(1))
                .crearDesdeReserva(eq("unidad-A302"), eq("Reserva Espacio"), eq(new BigDecimal("15000")), eq("reserva-1"));
    }
}
