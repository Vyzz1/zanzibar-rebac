package zanzibar.huynhvy.tuplestore.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import zanzibar.huynhvy.shared.testing.BaseIntegrationTest;
import zanzibar.huynhvy.tuplestore.config.RabbitConfig;
import zanzibar.huynhvy.tuplestore.domain.ZookieMinter;
import zanzibar.huynhvy.tuplestore.outbox.OutboxEvent;
import zanzibar.huynhvy.tuplestore.outbox.OutboxPoller;
import zanzibar.huynhvy.tuplestore.outbox.OutboxRepository;
import zanzibar.huynhvy.tuplestore.rabbitmq.TupleEventPublisher;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OutboxPollerIntegrationTest extends BaseIntegrationTest {

  private static final long RECEIVE_TIMEOUT_MS = 5_000;
  // A private queue bound to the fanout exchange, standing in for a consumer service.
  private static final String TEST_QUEUE = "test.tuple-changes";

  @Autowired private OutboxPoller poller;
  @Autowired private OutboxRepository outboxRepository;
  @Autowired private RabbitTemplate rabbitTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AmqpAdmin amqpAdmin;
  @Autowired private ZookieMinter zookieMinter;

  @BeforeEach
  void clean() {
    // Not auto-delete: receiveAndConvert(timeout) uses a consumer, which would otherwise drop an
    // auto-delete queue when it cancels, breaking a following receive().
    Queue queue = new Queue(TEST_QUEUE, false, false, false);
    amqpAdmin.declareQueue(queue);
    amqpAdmin.declareBinding(
        BindingBuilder.bind(queue).to(new FanoutExchange(RabbitConfig.TUPLE_CHANGES_EXCHANGE)));

    jdbcTemplate.execute("TRUNCATE tuplestore.outbox_events, tuplestore.relation_tuples");
    while (rabbitTemplate.receive(TEST_QUEUE) != null) {
      // drain leftovers from previous tests
    }
  }

  @Test
  void poll_publishes_unpublished_events_and_marks_them_published() throws Exception {
    outboxRepository.save(
        OutboxEvent.create(
            "doc:report#viewer@user:bob", "TUPLE_CREATED", "{\"x\":1}", OffsetDateTime.now()));

    poller.poll();

    Object body = rabbitTemplate.receiveAndConvert(TEST_QUEUE, RECEIVE_TIMEOUT_MS);
    // Compare as JSON: the jsonb column re-serializes the payload (e.g. adds a space after ':').
    assertThat(objectMapper.readTree((String) body)).isEqualTo(objectMapper.readTree("{\"x\":1}"));
    assertThat(countUnpublished()).isZero();
  }

  @Test
  void a_published_event_is_never_sent_twice() {
    outboxRepository.save(OutboxEvent.create("agg", "TUPLE_CREATED", "{}", OffsetDateTime.now()));

    poller.poll(); // publishes + marks
    poller.poll(); // nothing left to publish

    Object first = rabbitTemplate.receiveAndConvert(TEST_QUEUE, RECEIVE_TIMEOUT_MS);
    Object second = rabbitTemplate.receive(TEST_QUEUE);
    assertThat(first).isEqualTo("{}");
    assertThat(second).isNull();
  }

  @Test
  void a_published_event_carries_a_zookie_for_the_moment_it_committed() {
    OffsetDateTime committedAt = OffsetDateTime.now();
    outboxRepository.save(OutboxEvent.create("agg", "TUPLE_CREATED", "{}", committedAt));

    poller.poll();

    Message message = rabbitTemplate.receive(TEST_QUEUE, RECEIVE_TIMEOUT_MS);
    assertThat(message).isNotNull();
    Object zookie = message.getMessageProperties().getHeader(TupleEventPublisher.ZOOKIE_HEADER);
    // A consumer must be able to hand this straight back as a resume cursor, so it has to be a
    // Zookie tuple-store itself would have minted for that commit.
    assertThat(zookie).isEqualTo(zookieMinter.mint(committedAt).token());
  }

  private Integer countUnpublished() {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM tuplestore.outbox_events WHERE published = false", Integer.class);
  }
}
