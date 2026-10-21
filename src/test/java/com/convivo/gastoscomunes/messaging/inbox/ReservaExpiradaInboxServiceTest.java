package com.convivo.gastoscomunes.messaging.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.convivo.gastoscomunes.messaging.inbox.dto.ReservaExpiradaEvent;
import com.convivo.gastoscomunes.service.GastoComunService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

/**
 * El Inbox y la anulación del cobro deben quedar en la MISMA transacción
 * (igual que ReservaCreadaInboxService con ReservaEspacioCreadaListener):
 * si anularPorReservaExpirada falla, el registro en el Inbox también debe
 * revertirse — si no, una reentrega del mismo evento lo trataría como ya
 * procesado aunque el cobro nunca se haya anulado.
 */
@ExtendWith(MockitoExtension.class)
class ReservaExpiradaInboxServiceTest {

    @Mock
    private InboxEventoRepository inboxRepository;

    @Mock
    private GastoComunService gastoComunService;

    @InjectMocks
    private ReservaExpiradaInboxService service;

    private static ReservaExpiradaEvent eventoValido() {
        return new ReservaExpiradaEvent("evt-exp-1", "res-1");
    }

    @Test
    void descartaEventoDuplicado() {
        when(inboxRepository.existsById("evt-exp-1")).thenReturn(true);

        ResultadoProcesamiento resultado = service.procesar(eventoValido());

        assertThat(resultado).isEqualTo(ResultadoProcesamiento.DUPLICADO);
        verify(inboxRepository, never()).save(any());
        verify(gastoComunService, never()).anularPorReservaExpirada(any());
    }

    @Test
    void marcaInvalidoCuandoFaltaReservaId() {
        ReservaExpiradaEvent invalido = new ReservaExpiradaEvent("evt-exp-2", null);

        ResultadoProcesamiento resultado = service.procesar(invalido);

        assertThat(resultado).isEqualTo(ResultadoProcesamiento.INVALIDO);
        verify(inboxRepository, never()).save(any());
        verify(gastoComunService, never()).anularPorReservaExpirada(any());
    }

    @Test
    void procesaYAnulaCuandoEsValidoYNoEsDuplicado() {
        when(inboxRepository.existsById("evt-exp-1")).thenReturn(false);

        ResultadoProcesamiento resultado = service.procesar(eventoValido());

        assertThat(resultado).isEqualTo(ResultadoProcesamiento.PROCESADO);
        verify(inboxRepository, times(1)).save(any(InboxEvento.class));
        verify(gastoComunService, times(1)).anularPorReservaExpirada("res-1");
    }

    @Test
    void siAnularFallaLaExcepcionSePropagaParaQueTransactionalRevierta() {
        when(inboxRepository.existsById("evt-exp-1")).thenReturn(false);
        doThrow(new RuntimeException("fallo simulado de base de datos"))
                .when(gastoComunService).anularPorReservaExpirada("res-1");

        assertThatThrownBy(() -> service.procesar(eventoValido()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("fallo simulado de base de datos");
        // El Inbox ya se habia "guardado" en esta misma invocacion (mock, no hay DB real
        // en este test), pero la excepcion debe salir SIN ser atrapada: @Transactional es
        // lo que hace que Spring revierta tambien ese save() al propagarse.
        verify(inboxRepository, times(1)).save(any(InboxEvento.class));
    }

    @Test
    void procesarEstaAnotadoComoTransactional() throws NoSuchMethodException {
        Method metodo = ReservaExpiradaInboxService.class.getMethod("procesar", ReservaExpiradaEvent.class);
        assertThat(metodo.isAnnotationPresent(Transactional.class))
                .as("procesar() debe ser @Transactional: es lo que une el save() del Inbox "
                        + "y anularPorReservaExpirada() en una sola transacción")
                .isTrue();
    }
}
