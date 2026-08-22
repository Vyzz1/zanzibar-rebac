package zanzibar.huynhvy.tuplestore.rabbitmq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import zanzibar.huynhvy.tuplestore.config.RabbitConfig;
import zanzibar.huynhvy.tuplestore.domain.ZookieMinter;
import zanzibar.huynhvy.tuplestore.outbox.OutboxEvent;
import zanzibar.huynhvy.tuplestore.outbox.TupleChangeType;

@Slf4j
@Component
@RequiredArgsConstructor
public class TupleEventPublisher {

  /** AMQP header carrying CREATE / DELETE, so consumers know the kind of change. */
  public static final String OPERATION_HEADER = "operation";

  /**
   * AMQP header carrying the Zookie for this change, so a consumer can hand it back as a resume
   * cursor. Minted here because tuple-store is the only service that may sign one.
   */
  public static final String ZOOKIE_HEADER = "zookie";

  private final RabbitTemplate rabbitTemplate;
  private final ZookieMinter zookieMinter;

  public void publish(OutboxEvent event) {
    String operation = operationFor(event.getEventType());
    String zookie = zookieFor(event);
    // Fanout exchange ignores the routing key; the operation rides in a header.
    rabbitTemplate.convertAndSend(
        RabbitConfig.TUPLE_CHANGES_EXCHANGE,
        "",
        event.getPayload(),
        message -> {
          message.getMessageProperties().setHeader(OPERATION_HEADER, operation);
          if (zookie != null) {
            message.getMessageProperties().setHeader(ZOOKIE_HEADER, zookie);
          }
          return message;
        });
    log.debug(
        "Published {} outbox event {} to exchange {}",
        operation,
        event.getId(),
        RabbitConfig.TUPLE_CHANGES_EXCHANGE);
  }

  /**
   * Null for rows enqueued before the outbox carried a commit timestamp — those go out without a
   * resume token rather than with one pointing at the wrong moment.
   */
  private String zookieFor(OutboxEvent event) {
    if (event.getCommitTimestamp() == null) {
      log.debug(
          "Outbox event {} predates commit timestamps; publishing without a Zookie", event.getId());
      return null;
    }
    return zookieMinter.mint(event.getCommitTimestamp()).token();
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
