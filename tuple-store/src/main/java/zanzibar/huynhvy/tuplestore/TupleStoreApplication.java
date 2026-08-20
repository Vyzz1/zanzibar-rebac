package zanzibar.huynhvy.tuplestore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
// Scan shared too, so the shared auth beans are picked up.
@SpringBootApplication(
    scanBasePackages = {"zanzibar.huynhvy.tuplestore", "zanzibar.huynhvy.shared"})
public class TupleStoreApplication {
  public static void main(String[] args) {
    SpringApplication.run(TupleStoreApplication.class, args);
  }
}
