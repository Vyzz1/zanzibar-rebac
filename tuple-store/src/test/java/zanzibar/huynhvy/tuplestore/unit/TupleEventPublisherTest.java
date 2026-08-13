package zanzibar.huynhvy.tuplestore.unit;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import zanzibar.huynhvy.tuplestore.config.RabbitConfig;
import zanzibar.huynhvy.tuplestore.outbox.OutboxEvent;
import zanzibar.huynhvy.tuplestore.rabbitmq.TupleEventPublisher;

@ExtendWith(MockitoExtension.class)
class TupleEventPublisherTest {

  @Mock private RabbitTemplate rabbitTemplate;

  @InjectMocks private TupleEventPublisher publisher;

  @Test
  void publishes_the_payload_to_the_tuple_changes_exchange() {
    OutboxEvent event = OutboxEvent.create("agg-1", "TUPLE_CREATED", "{\"x\":1}");

    publisher.publish(event);

    verify(rabbitTemplate).convertAndSend(RabbitConfig.TUPLE_CHANGES_EXCHANGE, "", "{\"x\":1}");
  }
}
