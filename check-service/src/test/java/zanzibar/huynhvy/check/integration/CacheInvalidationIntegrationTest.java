package zanzibar.huynhvy.check.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import zanzibar.huynhvy.check.cache.TupleCache;
import zanzibar.huynhvy.check.config.RabbitConfig;
import zanzibar.huynhvy.shared.testing.BaseIntegrationTest;

/**
 * Proves cache invalidation end to end: a tuple change published to the fanout exchange is consumed
 * by check-service and evicts the cached results for that object, against real Redis + RabbitMQ.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=none")
class CacheInvalidationIntegrationTest extends BaseIntegrationTest {

  @SuppressWarnings("resource")
  private static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  static {
    REDIS.start();
  }

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  @Autowired private TupleCache tupleCache;
  @Autowired private RabbitTemplate rabbitTemplate;

  @Test
  void a_tuple_change_evicts_the_cached_results_for_that_object() {
    String key = "doc:report.pdf:viewer:user:bob";
    tupleCache.put(key, true, 1L, Map.of("doc", 1), Duration.ofMinutes(5));
    assertThat(tupleCache.get(key)).isPresent();

    // A change on the same object (different relation/subject) must clear the whole object.
    rabbitTemplate.convertAndSend(
        RabbitConfig.TUPLE_CHANGES_EXCHANGE,
        "",
        "{\"namespace\":\"doc\",\"objectId\":\"report.pdf\","
            + "\"relation\":\"editor\",\"subjectId\":\"user:alice\"}");

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(tupleCache.get(key)).isEmpty());
  }

  @Test
  void a_namespace_config_change_evicts_every_cached_result_in_that_namespace() {
    // A config change touches no tuple, so none of these keys would be reached by object eviction.
    String reportKey = "doc:report.pdf:viewer:user:bob";
    String otherObjectKey = "doc:budget.xlsx:editor:user:carol";
    String otherNamespaceKey = "folder:root:viewer:user:bob";
    tupleCache.put(reportKey, true, 1L, Map.of("doc", 1), Duration.ofMinutes(5));
    tupleCache.put(otherObjectKey, true, 1L, Map.of("doc", 1), Duration.ofMinutes(5));
    tupleCache.put(otherNamespaceKey, true, 1L, Map.of("folder", 1), Duration.ofMinutes(5));

    rabbitTemplate.convertAndSend(
        RabbitConfig.NAMESPACE_CHANGES_EXCHANGE, "", "{\"namespace\":\"doc\",\"version\":2}");

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(tupleCache.get(reportKey)).isEmpty();
              assertThat(tupleCache.get(otherObjectKey)).isEmpty();
            });
    // Another namespace's answers are not swept up by the prefix.
    assertThat(tupleCache.get(otherNamespaceKey)).isPresent();
  }
}
