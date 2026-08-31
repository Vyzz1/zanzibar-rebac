package zanzibar.huynhvy.namespace.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import zanzibar.huynhvy.namespace.config.RabbitConfig;
import zanzibar.huynhvy.namespace.domain.UpsertNamespaceUseCase;
import zanzibar.huynhvy.namespace.outbox.NamespaceOutboxPoller;
import zanzibar.huynhvy.shared.domain.NamespaceChange;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.This;
import zanzibar.huynhvy.shared.testing.BaseIntegrationTest;

/**
 * Proves a config write reaches the broker: the row is enqueued in the write's transaction and the
 * poller publishes it to the fanout exchange consumers bind to.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
// Park the scheduled poller so these tests decide when draining happens. At the default 500ms it
// would publish underneath them and the "enqueued but not yet published" assertion would be a race.
@TestPropertySource(properties = "outbox.poll-interval-ms=3600000")
class NamespaceOutboxIntegrationTest extends BaseIntegrationTest {

  private static final long RECEIVE_TIMEOUT_MS = 5_000;
  // A private queue bound to the fanout exchange, standing in for check-service.
  private static final String TEST_QUEUE = "test.namespace-changes";

  @Autowired private UpsertNamespaceUseCase upsert;
  @Autowired private NamespaceOutboxPoller poller;
  @Autowired private RabbitTemplate rabbitTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AmqpAdmin amqpAdmin;

  @BeforeEach
  void clean() {
    // Not auto-delete: receiveAndConvert(timeout) uses a consumer, which would otherwise drop an
    // auto-delete queue when it cancels, breaking a following receive().
    Queue queue = new Queue(TEST_QUEUE, false, false, false);
    amqpAdmin.declareQueue(queue);
    amqpAdmin.declareBinding(
        BindingBuilder.bind(queue).to(new FanoutExchange(RabbitConfig.NAMESPACE_CHANGES_EXCHANGE)));

    jdbcTemplate.execute("TRUNCATE namespace.outbox_events, namespace.namespace_configs");
    while (rabbitTemplate.receive(TEST_QUEUE) != null) {
      // drain leftovers from previous tests
    }
  }

  @Test
  void a_config_write_enqueues_an_event_in_the_same_transaction() {
    upsert.upsert("doc", Map.of("viewer", new This()));

    // Enqueued but not yet published: the write committed, the broker has not been touched.
    assertThat(countUnpublished()).isEqualTo(1);
    assertThat(rabbitTemplate.receive(TEST_QUEUE)).isNull();
  }

  @Test
  void the_poller_publishes_the_change_with_the_version_just_written() throws Exception {
    upsert.upsert("doc", Map.of("viewer", new This()));
    upsert.upsert("doc", Map.of("viewer", new This())); // -> version 2

    poller.poll();

    NamespaceChange first = nextChange();
    NamespaceChange second = nextChange();
    assertThat(first).isEqualTo(new NamespaceChange("doc", 1));
    assertThat(second).isEqualTo(new NamespaceChange("doc", 2));
    assertThat(countUnpublished()).isZero();
  }

  @Test
  void a_published_event_is_never_sent_twice() throws Exception {
    upsert.upsert("doc", Map.of("viewer", new This()));

    poller.poll(); // publishes + marks
    poller.poll(); // nothing left to publish

    assertThat(nextChange()).isEqualTo(new NamespaceChange("doc", 1));
    assertThat(rabbitTemplate.receive(TEST_QUEUE)).isNull();
  }

  private NamespaceChange nextChange() throws Exception {
    Object body = rabbitTemplate.receiveAndConvert(TEST_QUEUE, RECEIVE_TIMEOUT_MS);
    assertThat(body).isNotNull();
    return objectMapper.readValue((String) body, NamespaceChange.class);
  }

  private Integer countUnpublished() {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM namespace.outbox_events WHERE published = false", Integer.class);
  }
}
