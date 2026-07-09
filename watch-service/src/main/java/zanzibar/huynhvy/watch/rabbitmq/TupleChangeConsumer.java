package zanzibar.huynhvy.watch.rabbitmq;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class TupleChangeConsumer {
  @RabbitListener(queues = "tuple-changes")
  public void consume(String message) {}
}
