package zanzibar.huynhvy.watch.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.stub.StreamObserver;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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

  @Test
  void a_published_tuple_change_reaches_a_stream_watching_that_namespace() throws Exception {
    BlockingQueue<WatchEvent> received = new LinkedBlockingQueue<>();
    registry.register("doc", collectInto(received));

    rabbitTemplate.convertAndSend(
        RabbitConfig.TUPLE_CHANGES_QUEUE,
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
  void an_event_for_another_namespace_is_not_delivered() throws Exception {
    BlockingQueue<WatchEvent> received = new LinkedBlockingQueue<>();
    registry.register("doc", collectInto(received));

    rabbitTemplate.convertAndSend(
        RabbitConfig.TUPLE_CHANGES_QUEUE,
        "{\"namespace\":\"folder\",\"objectId\":\"root\","
            + "\"relation\":\"viewer\",\"subjectId\":\"user:carol\"}");

    assertThat(received.poll(2, TimeUnit.SECONDS)).isNull();
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
