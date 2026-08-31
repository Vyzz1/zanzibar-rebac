package zanzibar.huynhvy.namespace.rabbitmq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import zanzibar.huynhvy.namespace.config.RabbitConfig;
import zanzibar.huynhvy.namespace.outbox.NamespaceOutboxEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class NamespaceEventPublisher {

  /** AMQP header naming the kind of change, so a consumer can tell them apart without parsing. */
  public static final String EVENT_TYPE_HEADER = "eventType";

  private final RabbitTemplate rabbitTemplate;

  public void publish(NamespaceOutboxEvent event) {
    String eventType = event.getEventType();
    // Fanout exchange ignores the routing key; the event type rides in a header.
    rabbitTemplate.convertAndSend(
        RabbitConfig.NAMESPACE_CHANGES_EXCHANGE,
        "",
        event.getPayload(),
        message -> {
          message.getMessageProperties().setHeader(EVENT_TYPE_HEADER, eventType);
          return message;
        });
    log.debug(
        "Published {} outbox event {} to exchange {}",
        eventType,
        event.getId(),
        RabbitConfig.NAMESPACE_CHANGES_EXCHANGE);
  }
}
