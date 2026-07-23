package zanzibar.huynhvy.shared.testing;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Base class for integration tests. Starts PostgreSQL and RabbitMQ once per JVM (singleton
 * containers shared across every subclass, torn down by Ryuk at exit) and wires them into Spring
 * via {@link DynamicPropertySource}. Subclasses add {@code @SpringBootTest}.
 */
public abstract class BaseIntegrationTest {

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  private static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:3.13-alpine");

  static {
    POSTGRES.start();
    RABBITMQ.start();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);

    registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
    registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
    registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
    registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);

    // Keep the scheduled poller from racing with tests; tests invoke poll() explicitly.
    registry.add("outbox.poll-interval-ms", () -> 3_600_000);
    // Avoid binding a fixed gRPC port across test contexts.
    registry.add("grpc.server.port", () -> 0);
  }
}
