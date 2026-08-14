package zanzibar.huynhvy.tuplestore.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import zanzibar.huynhvy.tuplestore.config.RabbitConfig;
import zanzibar.huynhvy.tuplestore.outbox.OutboxEvent;
import zanzibar.huynhvy.tuplestore.rabbitmq.TupleEventPublisher;

@ExtendWith(MockitoExtension.class)
class TupleEventPublisherTest {

  @Mock private RabbitTemplate rabbitTemplate;

  @InjectMocks private TupleEventPublisher publisher;

  @Test
  void publishes_a_create_to_the_exchange_with_a_create_header() throws Exception {
    publisher.publish(OutboxEvent.create("agg-1", "TUPLE_CREATED", "{\"x\":1}"));

    assertThat(headerOf(capturePostProcessor("{\"x\":1}"))).isEqualTo("CREATE");
  }

  @Test
  void publishes_a_delete_with_a_delete_header() throws Exception {
    publisher.publish(OutboxEvent.create("agg-1", "TUPLE_DELETED", "{\"x\":1}"));

    assertThat(headerOf(capturePostProcessor("{\"x\":1}"))).isEqualTo("DELETE");
  }

  private MessagePostProcessor capturePostProcessor(String payload) {
    ArgumentCaptor<MessagePostProcessor> captor =
        ArgumentCaptor.forClass(MessagePostProcessor.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq(RabbitConfig.TUPLE_CHANGES_EXCHANGE), eq(""), eq(payload), captor.capture());
    return captor.getValue();
  }

  private static Object headerOf(MessagePostProcessor postProcessor) throws Exception {
    Message message = MessageBuilder.withBody(new byte[0]).build();
    return postProcessor
        .postProcessMessage(message)
        .getMessageProperties()
        .getHeader(TupleEventPublisher.OPERATION_HEADER);
  }
}
