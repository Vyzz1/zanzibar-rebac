package zanzibar.huynhvy.check.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import io.grpc.Status;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import zanzibar.huynhvy.check.client.NamespaceConfigClient;
import zanzibar.huynhvy.check.domain.CheckUseCase;
import zanzibar.huynhvy.shared.domain.RelationTuple;
import zanzibar.huynhvy.shared.testing.BaseIntegrationTest;

/**
 * Proves the Redis-backed Check cache and its Zookie freshness gate against real Postgres + Redis.
 * Config client is mocked (namespaces have no config → direct lookup).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(
    properties = {"spring.jpa.hibernate.ddl-auto=none", "namespace.config.cache-ttl-seconds=0"})
class CheckCacheIntegrationTest extends BaseIntegrationTest {

  private static final String ZOOKIE_SECRET = "dev-only-secret-change-me";
  private static final RelationTuple BOB_VIEWER =
      new RelationTuple("doc", "report.pdf", "viewer", "user:bob");

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

  @Autowired private CheckUseCase checkUseCase;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoBean private NamespaceConfigClient namespaceConfigClient;

  @BeforeEach
  void setUp() {
    doThrow(Status.NOT_FOUND.asRuntimeException()).when(namespaceConfigClient).fetch(anyString());

    jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS tuplestore");
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS tuplestore.relation_tuples (
          id               BIGSERIAL   PRIMARY KEY,
          namespace        TEXT        NOT NULL,
          object_id        TEXT        NOT NULL,
          relation         TEXT        NOT NULL,
          subject_id       TEXT        NOT NULL,
          commit_timestamp TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
        )
        """);
    jdbcTemplate.execute("TRUNCATE tuplestore.relation_tuples");
  }

  @Test
  void a_second_check_is_served_from_cache_even_after_the_tuple_is_deleted() {
    seed(BOB_VIEWER);
    assertThat(checkUseCase.check(BOB_VIEWER, null)).isTrue(); // miss → DB → cached

    deleteAllTuples(); // the DB no longer grants it

    // No Zookie: the cached ALLOW is served even though the DB would now say DENY.
    assertThat(checkUseCase.check(BOB_VIEWER, null)).isTrue();
  }

  @Test
  void a_zookie_newer_than_the_cache_forces_a_fresh_read() {
    seed(BOB_VIEWER);
    assertThat(checkUseCase.check(BOB_VIEWER, null)).isTrue(); // populate cache

    deleteAllTuples();

    // A Zookie stamped in the future is newer than the cached snapshot, so the cache is bypassed
    // and the fresh DB read (tuple gone) returns DENY.
    String futureZookie = mintZookie(farFutureNanos());
    assertThat(checkUseCase.check(BOB_VIEWER, futureZookie)).isFalse();
  }

  private static long farFutureNanos() {
    return (System.currentTimeMillis() + 600_000L) * 1_000_000L; // now + 10 min, in epoch nanos
  }

  private static String mintZookie(long commitNanos) {
    try {
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

  private void deleteAllTuples() {
    jdbcTemplate.execute("TRUNCATE tuplestore.relation_tuples");
  }

  private void seed(RelationTuple tuple) {
    jdbcTemplate.update(
        "INSERT INTO tuplestore.relation_tuples(namespace, object_id, relation, subject_id)"
            + " VALUES (?, ?, ?, ?)",
        tuple.namespace(),
        tuple.objectId(),
        tuple.relation(),
        tuple.subjectId());
  }
}
