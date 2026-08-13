package zanzibar.huynhvy.tuplestore.rabbitmq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import zanzibar.huynhvy.tuplestore.config.RabbitConfig;
import zanzibar.huynhvy.tuplestore.outbox.OutboxEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class TupleEventPublisher {

  private final RabbitTemplate rabbitTemplate;

  public void publish(OutboxEvent event) {
    // Fanout exchange ignores the routing key.
    rabbitTemplate.convertAndSend(RabbitConfig.TUPLE_CHANGES_EXCHANGE, "", event.getPayload());
    log.debug(
        "Published outbox event {} to exchange {}",
        event.getId(),
        RabbitConfig.TUPLE_CHANGES_EXCHANGE);
  }
}
