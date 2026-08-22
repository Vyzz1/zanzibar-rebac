package zanzibar.huynhvy.watch.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.stub.StreamObserver;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import zanzibar.huynhvy.api.WatchEvent;
import zanzibar.huynhvy.shared.testing.BaseIntegrationTest;
import zanzibar.huynhvy.watch.config.RabbitConfig;
import zanzibar.huynhvy.watch.stream.CursorOutOfRangeException;
import zanzibar.huynhvy.watch.stream.WatchCursor;
import zanzibar.huynhvy.watch.stream.WatchCursorResolver;
import zanzibar.huynhvy.watch.stream.WatchSubscriber;

/**
 * Exercises Watch against a real RabbitMQ stream: a live subscription sees only new changes, while
 * one resuming from a Zookie is replayed what it missed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WatchIntegrationTest extends BaseIntegrationTest {

  private static final String ZOOKIE_SECRET = "dev-only-secret-change-me";

  @Autowired private RabbitTemplate rabbitTemplate;
  @Autowired private WatchSubscriber subscriber;
  @Autowired private WatchCursorResolver cursorResolver;

  @Test
  void a_live_subscription_receives_changes_published_after_it_starts() throws Exception {
    BlockingQueue<WatchEvent> received = new LinkedBlockingQueue<>();

    try (AutoCloseable ignored = subscriber.subscribe("doc", WatchCursor.live(), into(received))) {
      publish("doc", "live.pdf", "viewer", "user:bob", "CREATE");

      WatchEvent event = received.poll(10, TimeUnit.SECONDS);
      assertThat(event).isNotNull();
      assertThat(event.getOperation()).isEqualTo(WatchEvent.Operation.CREATE);
      assertThat(event.getTuple().getObjectId()).isEqualTo("live.pdf");
    }
  }

  @Test
  void a_delete_arrives_as_a_delete_event() throws Exception {
    BlockingQueue<WatchEvent> received = new LinkedBlockingQueue<>();

    try (AutoCloseable ignored = subscriber.subscribe("doc", WatchCursor.live(), into(received))) {
      publish("doc", "gone.pdf", "viewer", "user:bob", "DELETE");

      assertThat(pollFor(received, "gone.pdf").getOperation())
          .isEqualTo(WatchEvent.Operation.DELETE);
    }
  }

  @Test
  void a_zookie_replays_the_changes_published_while_the_client_was_away() throws Exception {
    // The client last saw a write "now"; it then disconnects and misses what follows.
    String zookie = mintZookie(Instant.now());
    publish("doc", "missed-1.pdf", "viewer", "user:bob", "CREATE");
    publish("doc", "missed-2.pdf", "editor", "user:alice", "CREATE");

    BlockingQueue<WatchEvent> received = new LinkedBlockingQueue<>();
    WatchCursor cursor = cursorResolver.resolve(zookie);

    // Subscribing only now, it must still be given both changes it missed.
    try (AutoCloseable ignored = subscriber.subscribe("doc", cursor, into(received))) {
      assertThat(pollFor(received, "missed-1.pdf")).isNotNull();
      assertThat(pollFor(received, "missed-2.pdf")).isNotNull();
    }
  }

  /**
   * The point of the whole feature: a pure watcher never writes, so the only cursor it can have is
   * the one that came with the last event it processed.
   */
  @Test
  void a_client_can_resume_from_the_zookie_that_came_with_an_event() throws Exception {
    BlockingQueue<WatchEvent> firstRun = new LinkedBlockingQueue<>();
    String lastProcessed;
    try (AutoCloseable ignored = subscriber.subscribe("doc", WatchCursor.live(), into(firstRun))) {
      publish("doc", "seen.pdf", "viewer", "user:bob", "CREATE");
      lastProcessed = pollFor(firstRun, "seen.pdf").getZookie();
      assertThat(lastProcessed).isNotEmpty();
    }

    // Disconnected — and a change happens while it is away.
    publish("doc", "while-away.pdf", "editor", "user:alice", "CREATE");

    BlockingQueue<WatchEvent> resumed = new LinkedBlockingQueue<>();
    try (AutoCloseable ignored =
        subscriber.subscribe("doc", cursorResolver.resolve(lastProcessed), into(resumed))) {
      assertThat(pollFor(resumed, "while-away.pdf")).isNotNull();
    }
  }

  @Test
  void a_live_subscription_does_not_replay_earlier_changes() throws Exception {
    publish("doc", "before.pdf", "viewer", "user:bob", "CREATE");

    BlockingQueue<WatchEvent> received = new LinkedBlockingQueue<>();
    try (AutoCloseable ignored = subscriber.subscribe("doc", WatchCursor.live(), into(received))) {
      // Nothing new is published, so a live cursor should stay silent.
      assertThat(received.poll(3, TimeUnit.SECONDS)).isNull();
    }
  }

  @Test
  void an_event_for_another_namespace_is_not_delivered() throws Exception {
    BlockingQueue<WatchEvent> received = new LinkedBlockingQueue<>();

    try (AutoCloseable ignored = subscriber.subscribe("doc", WatchCursor.live(), into(received))) {
      publish("folder", "root", "viewer", "user:carol", "CREATE");

      assertThat(received.poll(3, TimeUnit.SECONDS)).isNull();
    }
  }

  @Test
  void a_zookie_older_than_retention_is_rejected() {
    String ancient = mintZookie(Instant.now().minus(Duration.ofDays(30)));

    assertThatThrownBy(() -> cursorResolver.resolve(ancient))
        .isInstanceOf(CursorOutOfRangeException.class);
  }

  private void publish(
      String namespace, String objectId, String relation, String subjectId, String operation) {
    String zookie = mintZookie(Instant.now());
    rabbitTemplate.convertAndSend(
        RabbitConfig.TUPLE_CHANGES_EXCHANGE,
        "",
        "{\"namespace\":\""
            + namespace
            + "\",\"objectId\":\""
            + objectId
            + "\",\"relation\":\""
            + relation
            + "\",\"subjectId\":\""
            + subjectId
            + "\"}",
        message -> {
          message.getMessageProperties().setHeader("operation", operation);
          message.getMessageProperties().setHeader("zookie", zookie);
          return message;
        });
  }

  /** Waits for the event about {@code objectId}, ignoring anything else on the shared stream. */
  private static WatchEvent pollFor(BlockingQueue<WatchEvent> events, String objectId)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + 10_000;
    while (System.currentTimeMillis() < deadline) {
      WatchEvent event = events.poll(1, TimeUnit.SECONDS);
      if (event != null && objectId.equals(event.getTuple().getObjectId())) {
        return event;
      }
    }
    throw new AssertionError("no event for " + objectId + " within the timeout");
  }

  private static StreamObserver<WatchEvent> into(BlockingQueue<WatchEvent> sink) {
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

  /** Mirrors tuple-store's ZookieMinter so the test can forge a cursor at a chosen moment. */
  private static String mintZookie(Instant commit) {
    try {
      long commitNanos = commit.getEpochSecond() * 1_000_000_000L + commit.getNano();
      ByteBuffer payload = ByteBuffer.allocate(Byte.BYTES + Long.BYTES);
      payload.put((byte) 1).putLong(commitNanos);

      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(ZOOKIE_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] signature = mac.doFinal(payload.array());

      ByteBuffer token = ByteBuffer.allocate(payload.array().length + signature.length);
      token.put(payload.array()).put(signature);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(token.array());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
