package zanzibar.huynhvy.namespace.config;

import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the fanout exchange config changes are published to.
 *
 * <p>Separate from {@code tuple-changes} rather than sharing it: consumers there parse every
 * message as a {@link zanzibar.huynhvy.shared.domain.RelationTuple}, and watch-service's stream
 * queue retains them for replay to clients. Mixing a differently-shaped payload into that log would
 * make every existing consumer handle a message it has no use for.
 */
@Configuration
public class RabbitConfig {

  public static final String NAMESPACE_CHANGES_EXCHANGE = "namespace-changes";

  @Bean
  public FanoutExchange namespaceChangesExchange() {
    return ExchangeBuilder.fanoutExchange(NAMESPACE_CHANGES_EXCHANGE).durable(true).build();
  }
}
