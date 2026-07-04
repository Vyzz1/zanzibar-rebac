package zanzibar.huynhvy.shared.security;

import org.springframework.stereotype.Component;
import zanzibar.huynhvy.shared.domain.Zookie;

@Component
public class ZookieValidator {
  public boolean validate(Zookie zookie) {
    return false;
  }
}
