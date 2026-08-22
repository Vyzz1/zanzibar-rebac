package zanzibar.huynhvy.tuplestore.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import zanzibar.huynhvy.shared.domain.Zookie;
import zanzibar.huynhvy.tuplestore.config.RabbitConfig;
import zanzibar.huynhvy.tuplestore.domain.ZookieMinter;
import zanzibar.huynhvy.tuplestore.outbox.OutboxEvent;
import zanzibar.huynhvy.tuplestore.rabbitmq.TupleEventPublisher;

@ExtendWith(MockitoExtension.class)
class TupleEventPublisherTest {

  private static final OffsetDateTime COMMITTED_AT =
      OffsetDateTime.of(2026, 8, 13, 12, 0, 0, 0, ZoneOffset.UTC);

  @Mock private RabbitTemplate rabbitTemplate;
  @Mock private ZookieMinter zookieMinter;

  @InjectMocks private TupleEventPublisher publisher;

  @Test
  void publishes_a_create_to_the_exchange_with_a_create_header() throws Exception {
    mintsFromCommit();

    publisher.publish(OutboxEvent.create("agg-1", "TUPLE_CREATED", "{\"x\":1}", COMMITTED_AT));

    assertThat(headerOf(capturePostProcessor("{\"x\":1}"), TupleEventPublisher.OPERATION_HEADER))
        .isEqualTo("CREATE");
  }

  @Test
  void publishes_a_delete_with_a_delete_header() throws Exception {
    mintsFromCommit();

    publisher.publish(OutboxEvent.create("agg-1", "TUPLE_DELETED", "{\"x\":1}", COMMITTED_AT));

    assertThat(headerOf(capturePostProcessor("{\"x\":1}"), TupleEventPublisher.OPERATION_HEADER))
        .isEqualTo("DELETE");
  }

  @Test
  void carries_a_zookie_minted_from_the_commit_timestamp() throws Exception {
    mintsFromCommit();

    publisher.publish(OutboxEvent.create("agg-1", "TUPLE_CREATED", "{\"x\":1}", COMMITTED_AT));

    assertThat(headerOf(capturePostProcessor("{\"x\":1}"), TupleEventPublisher.ZOOKIE_HEADER))
        .isEqualTo("zk-at-commit");
    verify(zookieMinter).mint(COMMITTED_AT);
  }

  @Test
  void publishes_without_a_zookie_when_the_event_predates_commit_timestamps() throws Exception {
    // A row written before the column existed: better no cursor than one pointing at the wrong
    // moment, which would silently skip changes on resume.
    publisher.publish(OutboxEvent.create("agg-1", "TUPLE_CREATED", "{\"x\":1}", null));

    assertThat(headerOf(capturePostProcessor("{\"x\":1}"), TupleEventPublisher.ZOOKIE_HEADER))
        .isNull();
  }

  private void mintsFromCommit() {
    when(zookieMinter.mint(COMMITTED_AT)).thenReturn(new Zookie("zk-at-commit"));
  }

  private MessagePostProcessor capturePostProcessor(String payload) {
    ArgumentCaptor<MessagePostProcessor> captor =
        ArgumentCaptor.forClass(MessagePostProcessor.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq(RabbitConfig.TUPLE_CHANGES_EXCHANGE), eq(""), eq(payload), captor.capture());
    return captor.getValue();
  }

  private static Object headerOf(MessagePostProcessor postProcessor, String header)
      throws Exception {
    Message message = MessageBuilder.withBody(new byte[0]).build();
    return postProcessor.postProcessMessage(message).getMessageProperties().getHeader(header);
  }
}
