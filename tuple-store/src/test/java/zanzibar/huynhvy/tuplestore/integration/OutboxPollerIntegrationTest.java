package zanzibar.huynhvy.tuplestore.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import zanzibar.huynhvy.shared.testing.BaseIntegrationTest;
import zanzibar.huynhvy.tuplestore.config.RabbitConfig;
import zanzibar.huynhvy.tuplestore.outbox.OutboxEvent;
import zanzibar.huynhvy.tuplestore.outbox.OutboxPoller;
import zanzibar.huynhvy.tuplestore.outbox.OutboxRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OutboxPollerIntegrationTest extends BaseIntegrationTest {

  private static final long RECEIVE_TIMEOUT_MS = 5_000;

  @Autowired private OutboxPoller poller;
  @Autowired private OutboxRepository outboxRepository;
  @Autowired private RabbitTemplate rabbitTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void clean() {
    jdbcTemplate.execute("TRUNCATE tuplestore.outbox_events, tuplestore.relation_tuples");
    while (rabbitTemplate.receive(RabbitConfig.TUPLE_CHANGES_QUEUE) != null) {
      // drain leftovers from previous tests
    }
  }

  @Test
  void poll_publishes_unpublished_events_and_marks_them_published() throws Exception {
    outboxRepository.save(
        OutboxEvent.create("doc:report#viewer@user:bob", "TUPLE_CREATED", "{\"x\":1}"));

    poller.poll();

    Object body =
        rabbitTemplate.receiveAndConvert(RabbitConfig.TUPLE_CHANGES_QUEUE, RECEIVE_TIMEOUT_MS);
    // Compare as JSON: the jsonb column re-serializes the payload (e.g. adds a space after ':').
    assertThat(objectMapper.readTree((String) body)).isEqualTo(objectMapper.readTree("{\"x\":1}"));
    assertThat(countUnpublished()).isZero();
  }

  @Test
  void a_published_event_is_never_sent_twice() {
    outboxRepository.save(OutboxEvent.create("agg", "TUPLE_CREATED", "{}"));

    poller.poll(); // publishes + marks
    poller.poll(); // nothing left to publish

    Object first =
        rabbitTemplate.receiveAndConvert(RabbitConfig.TUPLE_CHANGES_QUEUE, RECEIVE_TIMEOUT_MS);
    Object second = rabbitTemplate.receive(RabbitConfig.TUPLE_CHANGES_QUEUE);
    assertThat(first).isEqualTo("{}");
    assertThat(second).isNull();
  }

  private Integer countUnpublished() {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM tuplestore.outbox_events WHERE published = false", Integer.class);
  }
}
