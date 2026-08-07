package zanzibar.huynhvy.check.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import zanzibar.huynhvy.shared.domain.RelationTuple;
import zanzibar.huynhvy.shared.domain.Zookie;
import zanzibar.huynhvy.shared.security.ZookieValidator;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckUseCase {

  private final GraphTraverser graphTraverser;
  private final NamespaceConfigProvider namespaceConfigProvider;
  private final ZookieValidator zookieValidator;

  /**
   * Answers "does {@code subject} have {@code relation} on {@code object}?" by evaluating the
   * relation's userset rewrite (direct tuples plus any derived relations) via {@link
   * GraphTraverser}.
   *
   * @param zookie optional consistency token; if present it must carry a valid HMAC
   */
  public boolean check(RelationTuple tuple, String zookie) {
    if (zookie != null && !zookie.isBlank() && !zookieValidator.validate(new Zookie(zookie))) {
      throw new IllegalArgumentException("Invalid Zookie");
    }

    boolean allowed =
        graphTraverser.evaluate(
            tuple.namespace(),
            tuple.objectId(),
            tuple.relation(),
            tuple.subjectId(),
            namespaceConfigProvider::get);
    log.debug("Check {} -> {}", tuple, allowed);
    return allowed;
  }
}
