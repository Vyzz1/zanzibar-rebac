package zanzibar.huynhvy.tuplestore.domain;

import org.springframework.stereotype.Component;
import zanzibar.huynhvy.shared.domain.Zookie;

@Component
public class ZookieMinter {
  public Zookie mint(long commitTimestamp) {
    return new Zookie("placeholder");
  }
}
