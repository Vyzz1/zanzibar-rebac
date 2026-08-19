package zanzibar.huynhvy.watch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Scan shared too, so the shared ZookieValidator bean is picked up.
@SpringBootApplication(scanBasePackages = {"zanzibar.huynhvy.watch", "zanzibar.huynhvy.shared"})
public class WatchServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(WatchServiceApplication.class, args);
  }
}
