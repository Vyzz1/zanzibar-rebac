package zanzibar.huynhvy.check;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Scan shared too, so the shared ZookieValidator bean is picked up.
@SpringBootApplication(scanBasePackages = {"zanzibar.huynhvy.check", "zanzibar.huynhvy.shared"})
public class CheckServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(CheckServiceApplication.class, args);
  }
}
