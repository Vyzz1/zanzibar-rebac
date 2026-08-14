package zanzibar.huynhvy.tuplestore.rabbitmq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import zanzibar.huynhvy.tuplestore.config.RabbitConfig;
import zanzibar.huynhvy.tuplestore.outbox.OutboxEvent;
import zanzibar.huynhvy.tuplestore.outbox.TupleChangeType;

@Slf4j
@Component
@RequiredArgsConstructor
public class TupleEventPublisher {

  /** AMQP header carrying CREATE / DELETE, so consumers know the kind of change. */
  public static final String OPERATION_HEADER = "operation";

  private final RabbitTemplate rabbitTemplate;

  public void publish(OutboxEvent event) {
    String operation = operationFor(event.getEventType());
    // Fanout exchange ignores the routing key; the operation rides in a header.
    rabbitTemplate.convertAndSend(
        RabbitConfig.TUPLE_CHANGES_EXCHANGE,
        "",
        event.getPayload(),
        message -> {
          message.getMessageProperties().setHeader(OPERATION_HEADER, operation);
          return message;
        });
    log.debug(
        "Published {} outbox event {} to exchange {}",
        operation,
        event.getId(),
        RabbitConfig.TUPLE_CHANGES_EXCHANGE);
  }

  private String operationFor(String eventType) {
    if (TupleChangeType.DELETED.eventType().equals(eventType)) {
      return TupleChangeType.DELETED.operation();
    }
    if (!TupleChangeType.CREATED.eventType().equals(eventType)) {
      log.warn(
          "Unknown outbox event type '{}'; publishing as {}",
          eventType,
          TupleChangeType.CREATED.operation());
    }
    return TupleChangeType.CREATED.operation();
  }
}
