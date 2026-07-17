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
    rabbitTemplate.convertAndSend(RabbitConfig.TUPLE_CHANGES_QUEUE, event.getPayload());
    log.debug(
        "Published outbox event {} to queue {}", event.getId(), RabbitConfig.TUPLE_CHANGES_QUEUE);
  }
}
