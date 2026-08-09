package zanzibar.huynhvy.watch.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Declares the durable queue tuple-store publishes tuple changes to, so the listener can bind. */
@Configuration
public class RabbitConfig {

  public static final String TUPLE_CHANGES_QUEUE = "tuple-changes";

  @Bean
  public Queue tupleChangesQueue() {
    return QueueBuilder.durable(TUPLE_CHANGES_QUEUE).build();
  }
}
