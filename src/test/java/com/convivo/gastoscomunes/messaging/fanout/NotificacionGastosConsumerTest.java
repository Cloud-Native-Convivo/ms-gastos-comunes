package com.convivo.gastoscomunes.messaging.fanout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.convivo.gastoscomunes.config.MailProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class NotificacionGastosConsumerTest {

    private static final long TAG = 3L;
    private static final String JSON = """
            {"ticket_id":"t-1","email":"a@b.cl","unidad_id":"101-A"}""";

    @Mock
    private EmailEnviadoRepository repository;

    @Mock
    private Channel canal;

    @Mock
    private JavaMailSender mailSender;

    private NotificacionGastosConsumer consumer;

    @BeforeEach
    void crear() {
        consumer = new NotificacionGastosConsumer(
                new ObjectMapper(), repository, mailSender, new MailProperties(false, "no-reply@convivo.cl"));
    }

    private static Message mensaje(String cuerpo, boolean redelivered) {
        MessageProperties propiedades = new MessageProperties();
        propiedades.setDeliveryTag(TAG);
        propiedades.setRedelivered(redelivered);
        return new Message(cuerpo.getBytes(StandardCharsets.UTF_8), propiedades);
    }

    private static Message mensaje(String cuerpo) {
        return mensaje(cuerpo, false);
    }

    @Test
    void reservaElTicketEnCursoAntesDeDespacharYLoCompletaAlTerminar() throws Exception {
        when(repository.findById("t-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(EmailEnviado.class))).thenReturn(new EmailEnviado("t-1"));

        consumer.recibir(mensaje(JSON), canal);

        verify(repository)
                .saveAndFlush(org.mockito.ArgumentMatchers.argThat(
                        e -> e.getTicketId().equals("t-1") && e.getEstado() == EstadoNotificacion.EN_CURSO));
        verify(repository)
                .save(org.mockito.ArgumentMatchers.argThat(
                        e -> e.getTicketId().equals("t-1") && e.getEstado() == EstadoNotificacion.COMPLETADO));
        verify(canal).basicAck(TAG, false);
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void conMailHabilitadoEnviaElCorreoRealAlDestinatarioDelEvento() throws Exception {
        NotificacionGastosConsumer consumerConMail = new NotificacionGastosConsumer(
                new ObjectMapper(), repository, mailSender, new MailProperties(true, "no-reply@convivo.cl"));
        when(repository.findById("t-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(EmailEnviado.class))).thenReturn(new EmailEnviado("t-1"));

        consumerConMail.recibir(mensaje(JSON), canal);

        verify(mailSender)
                .send(org.mockito.ArgumentMatchers.argThat((SimpleMailMessage m) -> "no-reply@convivo.cl".equals(m.getFrom())
                        && java.util.Arrays.asList(m.getTo()).contains("a@b.cl")
                        && m.getSubject().contains("en proceso de generación")));
    }

    @Test
    void dosCopiasCasiJuntasPierdeLaCarreraDeInsercionYSeDescartaSinRetomar() throws Exception {
        // Primera copia: no existe fila -> inserta. Segunda copia (este test):
        // tampoco ve la fila todavia (llega casi junto) pero el INSERT choca
        // con la llave primaria -- se descarta sin reintentar el envio.
        when(repository.findById("t-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(EmailEnviado.class)))
                .thenThrow(new DataIntegrityViolationException("llave primaria duplicada"));

        consumer.recibir(mensaje(JSON, false), canal);

        verify(canal).basicAck(TAG, false);
        verify(repository, never()).save(any());
    }

    @Test
    void segundaCopiaEnCursoSinRedeliverySeDescartaSinRetomar() throws Exception {
        EmailEnviado enCurso = new EmailEnviado("t-1"); // EN_CURSO por defecto, otra copia la esta procesando
        when(repository.findById("t-1")).thenReturn(Optional.of(enCurso));

        consumer.recibir(mensaje(JSON, false), canal);

        verify(repository, never()).saveAndFlush(any());
        verify(repository, never()).save(any());
        verify(canal).basicAck(TAG, false);
    }

    @Test
    void ticketEnCursoYReentregaRedeliveredSeRetomaYDespacha() throws Exception {
        // Simula que el proceso que reservo el ticket murio antes de completar:
        // RabbitMQ reentrega el mismo mensaje (redelivered=true) a otro consumidor.
        EmailEnviado enCurso = new EmailEnviado("t-1");
        when(repository.findById("t-1")).thenReturn(Optional.of(enCurso));

        consumer.recibir(mensaje(JSON, true), canal);

        verify(repository, never()).saveAndFlush(any());
        assertEquals(EstadoNotificacion.COMPLETADO, enCurso.getEstado());
        verify(repository).save(enCurso);
        verify(canal).basicAck(TAG, false);
    }

    @Test
    void ticketYaCompletadoSeDescartaAunqueSeaRedelivered() throws Exception {
        EmailEnviado completado = new EmailEnviado("t-1");
        completado.completar();
        when(repository.findById("t-1")).thenReturn(Optional.of(completado));

        consumer.recibir(mensaje(JSON, true), canal);

        verify(repository, never()).saveAndFlush(any());
        verify(repository, never()).save(any());
        verify(canal).basicAck(TAG, false);
    }

    @Test
    void siFallaElEnvioNoSeBorraLaReservaQuedaEnCursoParaReintentoPorReentrega() throws Exception {
        when(repository.findById("t-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(EmailEnviado.class))).thenReturn(new EmailEnviado("t-1"));
        NotificacionGastosConsumer consumerSpia = spy(consumer);
        doThrow(new RuntimeException("smtp caido")).when(consumerSpia).procesarNotificacion(any(JsonNode.class));

        consumerSpia.recibir(mensaje(JSON), canal);

        verify(repository, never()).deleteById(any());
        verify(repository, never()).save(any());
        verify(canal).basicReject(TAG, false);
        verify(canal, never()).basicAck(TAG, false);
    }

    @Test
    void ticketVacioSeDescartaSinReservar() throws Exception {
        consumer.recibir(mensaje("""
                {"email":"a@b.cl"}"""), canal);

        verify(repository, never()).saveAndFlush(any());
        verify(canal).basicAck(TAG, false);
    }

    @Test
    void cuerpoIlegibleSeRechazaSinRequeueHaciaLaDlq() throws Exception {
        consumer.recibir(mensaje("no es json"), canal);

        verify(canal).basicReject(TAG, false);
        verify(canal, never()).basicAck(TAG, false);
    }
}
