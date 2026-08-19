package zanzibar.huynhvy.watch.integration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.stub.StreamObserver;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import zanzibar.huynhvy.api.WatchEvent;
import zanzibar.huynhvy.shared.testing.BaseIntegrationTest;
import zanzibar.huynhvy.watch.config.RabbitConfig;
import zanzibar.huynhvy.watch.stream.StreamRegistry;

/**
 * Proves the event pipeline end to end against a real RabbitMQ: a message on {@code tuple-changes}
 * is consumed, parsed, and fanned out to a registered Watch stream for that namespace.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WatchIntegrationTest extends BaseIntegrationTest {

  @Autowired private RabbitTemplate rabbitTemplate;
  @Autowired private StreamRegistry registry;
  @Autowired private ConnectionFactory connectionFactory;

  @Test
  void a_published_tuple_change_reaches_a_stream_watching_that_namespace() throws Exception {
    BlockingQueue<WatchEvent> received = new LinkedBlockingQueue<>();
    registry.register("doc", collectInto(received));

    rabbitTemplate.convertAndSend(
        RabbitConfig.TUPLE_CHANGES_EXCHANGE,
        "",
        "{\"namespace\":\"doc\",\"objectId\":\"report.pdf\","
            + "\"relation\":\"viewer\",\"subjectId\":\"user:bob\"}");

    WatchEvent event = received.poll(10, TimeUnit.SECONDS);
    assertThat(event).as("stream should receive the fanned-out event").isNotNull();
    assertThat(event.getOperation()).isEqualTo(WatchEvent.Operation.CREATE);
    assertThat(event.getTuple().getNamespace()).isEqualTo("doc");
    assertThat(event.getTuple().getObjectId()).isEqualTo("report.pdf");
    assertThat(event.getTuple().getSubjectId()).isEqualTo("user:bob");
  }

  @Test
  void a_delete_message_reaches_the_stream_as_a_delete_event() throws Exception {
    BlockingQueue<WatchEvent> received = new LinkedBlockingQueue<>();
    registry.register("doc", collectInto(received));

    rabbitTemplate.convertAndSend(
        RabbitConfig.TUPLE_CHANGES_EXCHANGE,
        "",
        "{\"namespace\":\"doc\",\"objectId\":\"report.pdf\","
            + "\"relation\":\"viewer\",\"subjectId\":\"user:bob\"}",
        message -> {
          message.getMessageProperties().setHeader("operation", "DELETE");
          return message;
        });

    WatchEvent event = received.poll(10, TimeUnit.SECONDS);
    assertThat(event).isNotNull();
    assertThat(event.getOperation()).isEqualTo(WatchEvent.Operation.DELETE);
  }

  @Test
  void an_event_for_another_namespace_is_not_delivered() throws Exception {
    BlockingQueue<WatchEvent> received = new LinkedBlockingQueue<>();
    registry.register("doc", collectInto(received));

    rabbitTemplate.convertAndSend(
        RabbitConfig.TUPLE_CHANGES_EXCHANGE,
        "",
        "{\"namespace\":\"folder\",\"objectId\":\"root\","
            + "\"relation\":\"viewer\",\"subjectId\":\"user:carol\"}");

    assertThat(received.poll(2, TimeUnit.SECONDS)).isNull();
  }

  /**
   * The point of using a stream rather than a classic queue: a consumer that attaches *after* a
   * message was published can still read it from the start of the log. This is what will let Watch
   * resume from a cursor; with a classic queue the message would already be gone.
   */
  @Test
  void the_stream_retains_published_events_for_a_later_consumer() throws Exception {
    String marker = "replay-me.pdf";
    rabbitTemplate.convertAndSend(
        RabbitConfig.TUPLE_CHANGES_EXCHANGE,
        "",
        "{\"namespace\":\"doc\",\"objectId\":\""
            + marker
            + "\","
            + "\"relation\":\"viewer\",\"subjectId\":\"user:bob\"}");

    BlockingQueue<String> replayed = new LinkedBlockingQueue<>();
    SimpleMessageListenerContainer container =
        new SimpleMessageListenerContainer(connectionFactory);
    container.setQueueNames(RabbitConfig.WATCH_STREAM);
    container.setPrefetchCount(10);
    container.setConsumerArguments(Map.of("x-stream-offset", "first")); // read the log from the top
    container.setMessageListener(
        (MessageListener) message -> replayed.add(new String(message.getBody(), UTF_8)));
    container.start();

    try {
      assertThat(pollFor(replayed, marker))
          .as("a consumer starting at offset=first should still see the earlier event")
          .isTrue();
    } finally {
      container.stop();
    }
  }

  /** Drains until a message containing {@code marker} shows up, or the budget runs out. */
  private static boolean pollFor(BlockingQueue<String> messages, String marker) throws Exception {
    long deadline = System.currentTimeMillis() + 10_000;
    while (System.currentTimeMillis() < deadline) {
      String message = messages.poll(1, TimeUnit.SECONDS);
      if (message != null && message.contains(marker)) {
        return true;
      }
    }
    return false;
  }

  private static StreamObserver<WatchEvent> collectInto(BlockingQueue<WatchEvent> sink) {
    return new StreamObserver<>() {
      @Override
      public void onNext(WatchEvent value) {
        sink.add(value);
      }

      @Override
      public void onError(Throwable t) {}

      @Override
      public void onCompleted() {}
    };
  }
}
