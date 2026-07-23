package zanzibar.huynhvy.check.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import zanzibar.huynhvy.check.repository.TupleReadRepository;
import zanzibar.huynhvy.shared.domain.RelationTuple;
import zanzibar.huynhvy.shared.domain.Zookie;
import zanzibar.huynhvy.shared.security.ZookieValidator;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckUseCase {

  private final TupleReadRepository tupleReadRepository;
  private final ZookieValidator zookieValidator;

  /**
   * Direct-lookup check: is there a stored tuple granting {@code subject} the {@code relation} on
   * the object? Recursive userset rewrites (editor→viewer, group membership, …) are not evaluated
   * yet.
   *
   * @param zookie optional consistency token; if present it must carry a valid HMAC
   */
  public boolean check(RelationTuple tuple, String zookie) {
    if (zookie != null && !zookie.isBlank() && !zookieValidator.validate(new Zookie(zookie))) {
      throw new IllegalArgumentException("Invalid Zookie");
    }

    boolean allowed =
        tupleReadRepository.existsByNamespaceAndObjectIdAndRelationAndSubjectId(
            tuple.namespace(), tuple.objectId(), tuple.relation(), tuple.subjectId());
    log.debug("Check {} -> {}", tuple, allowed);
    return allowed;
  }
}
