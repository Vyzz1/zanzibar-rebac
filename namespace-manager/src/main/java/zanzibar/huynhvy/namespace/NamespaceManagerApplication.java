package zanzibar.huynhvy.namespace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
// namespace-manager never handles Zookies, so it scans only the auth half of shared.
@SpringBootApplication(
    scanBasePackages = {"zanzibar.huynhvy.namespace", "zanzibar.huynhvy.shared.auth"})
public class NamespaceManagerApplication {
  public static void main(String[] args) {
    SpringApplication.run(NamespaceManagerApplication.class, args);
  }
}
