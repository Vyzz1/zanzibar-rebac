package zanzibar.huynhvy.tuplestore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
// tuple-store mints its own Zookies, so it scans only the auth half of shared.
@SpringBootApplication(
    scanBasePackages = {"zanzibar.huynhvy.tuplestore", "zanzibar.huynhvy.shared.auth"})
public class TupleStoreApplication {
  public static void main(String[] args) {
    SpringApplication.run(TupleStoreApplication.class, args);
  }
}
