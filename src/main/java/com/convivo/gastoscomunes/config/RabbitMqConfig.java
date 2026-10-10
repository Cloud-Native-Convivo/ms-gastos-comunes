package com.convivo.gastoscomunes.config;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.config.StatelessRetryOperationsInterceptor;
import org.springframework.core.retry.RetryPolicy;

/**
 * Declara la topología RabbitMQ que consume/publica ms-gastos-comunes:
 *
 * <ul>
 *   <li>Consume {@code reserva_espacio_creada} desde el exchange
 *       {@code espacios_events} (publicado por ms-espacios-comunes vía su
 *       propio Outbox) en la cola {@code gastos_reserva_creada_queue}.</li>
 *   <li>Ante fallos técnicos persistentes, el mensaje termina en la Dead
 *       Letter Queue tras agotar reintentos (sin requeue infinito).</li>
 *   <li>Ante fallos de negocio (ej. unidad inexistente), publica
 *       {@code gasto_fallido} de vuelta al mismo exchange, enrutado hacia
 *       la cola {@code gasto_fallido} (consumida por ms-espacios-comunes
 *       para compensar la saga).</li>
 * </ul>
 */
@Configuration
public class RabbitMqConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqConfig.class);

    private final MensajeriaProperties props;

    public RabbitMqConfig(MensajeriaProperties props) {
        this.props = props;
    }

    @Bean
    public TopicExchange espaciosEventsExchange() {
        return new TopicExchange(props.exchangeEventos(), true, false);
    }

    @Bean
    public DirectExchange gastosDlx() {
        return new DirectExchange(props.dlx(), true, false);
    }

    @Bean
    public Queue gastosReservaCreadaQueue() {
        return QueueBuilder.durable(props.colaReservaCreada())
                .withArgument("x-dead-letter-exchange", props.dlx())
                .withArgument("x-dead-letter-routing-key", props.colaDeadLetter())
                .build();
    }

    @Bean
    public Queue gastosReservaCreadaDlq() {
        return QueueBuilder.durable(props.colaDeadLetter()).build();
    }

    @Bean
    public Queue gastosComandosQueue() {
        return QueueBuilder.durable("gastos_comandos_queue").build();
    }


    @Bean
    public Binding bindingReservaCreada(Queue gastosReservaCreadaQueue, TopicExchange espaciosEventsExchange) {
        return BindingBuilder.bind(gastosReservaCreadaQueue)
                .to(espaciosEventsExchange)
                .with(props.routingKeyReservaCreada());
    }

    @Bean
    public Binding bindingDeadLetter(Queue gastosReservaCreadaDlq, DirectExchange gastosDlx) {
        return BindingBuilder.bind(gastosReservaCreadaDlq).to(gastosDlx).with(props.colaDeadLetter());
    }

    @Bean
    public FanoutExchange gastosPdfFanout() {
        return new FanoutExchange("gastos.pdf.fanout", true, false);
    }

    @Bean
    public Queue gastosPdfGeneracionQueue() {
        return QueueBuilder.durable("gastos_pdf_generacion_queue")
                .withArgument("x-dead-letter-exchange", props.dlx())
                .withArgument("x-dead-letter-routing-key", props.colaDeadLetter())
                .build();
    }

    @Bean
    public Queue gastosNotificacionesEmailQueue() {
        return QueueBuilder.durable("gastos_notificaciones_email_queue")
                .withArgument("x-dead-letter-exchange", props.dlx())
                .withArgument("x-dead-letter-routing-key", props.colaDeadLetter())
                .build();
    }

    @Bean
    public Binding bindingGastosPdfGeneracion(Queue gastosPdfGeneracionQueue, FanoutExchange gastosPdfFanout) {
        return BindingBuilder.bind(gastosPdfGeneracionQueue).to(gastosPdfFanout);
    }

    @Bean
    public Binding bindingGastosNotificacionesEmail(Queue gastosNotificacionesEmailQueue, FanoutExchange gastosPdfFanout) {
        return BindingBuilder.bind(gastosNotificacionesEmailQueue).to(gastosPdfFanout);
    }

    /**
     * Mensajes en texto plano (JSON serializado a mano, ver
     * {@code messaging.outbox.OutboxPublisherService}): evita depender del
     * header {@code __TypeId__} de Jackson, que no tiene sentido en un
     * sistema donde el productor (ms-espacios-comunes) es Python/FastAPI.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new SimpleMessageConverter());
        template.setMandatory(true);
        template.setConfirmCallback((correlation, ack, cause) -> {
            if (!ack) {
                log.error("RabbitMQ no confirmó la publicación (correlationId={}): {}", correlation, cause);
            }
        });
        template.setReturnsCallback(returned -> log.error(
                "Mensaje retornado sin poder enrutarse: exchange={}, routingKey={}, replyText={}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyText()));
        return template;
    }

    /**
     * Reintentos acotados antes de descartar-sin-requeue: el mensaje cae
     * entonces a la Dead Letter Queue por la política x-dead-letter-*
     * configurada en la cola (evita loops infinitos de reentrega).
     */
    @Bean
    public RabbitListenerContainerFactory<?> rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        // Aplica spring.rabbitmq.listener.simple.* (incluye auto-startup,
        // prefetch, concurrency) desde application-*.yml — sin esto el
        // factory ignoraba RABBITMQ_AUTOSTART y el listener siempre
        // arrancaba, aunque el perfil local lo pida desactivado.
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(new SimpleMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setAdviceChain(retryInterceptor());
        return factory;
    }

    private StatelessRetryOperationsInterceptor retryInterceptor() {
        // maxAttempts de spring-retry incluia el intento inicial; maxRetries no.
        RetryPolicy politica = RetryPolicy.builder()
                .maxRetries(props.listenerMaxReintentos() - 1)
                .delay(Duration.ofMillis(500))
                .multiplier(2.0)
                .maxDelay(Duration.ofSeconds(5))
                .build();
        return RetryInterceptorBuilder.stateless()
                .retryPolicy(politica)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }
}
