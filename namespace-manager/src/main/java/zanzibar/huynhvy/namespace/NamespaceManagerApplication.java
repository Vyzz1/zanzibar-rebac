package zanzibar.huynhvy.namespace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Scan shared too, so the shared auth beans are picked up.
@SpringBootApplication(scanBasePackages = {"zanzibar.huynhvy.namespace", "zanzibar.huynhvy.shared"})
public class NamespaceManagerApplication {
  public static void main(String[] args) {
    SpringApplication.run(NamespaceManagerApplication.class, args);
  }
}
