package zanzibar.huynhvy.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resolves a bearer token to the calling service. Tokens are compared in constant time so a wrong
 * token cannot be discovered one character at a time.
 */
@Slf4j
@Component
public class ApiTokenAuthenticator {

  private static final String BEARER_PREFIX = "Bearer ";

  private final AuthProperties properties;

  public ApiTokenAuthenticator(AuthProperties properties) {
    this.properties = properties;
    if (properties.enabled() && properties.clients().isEmpty()) {
      log.warn("Auth is enabled but no clients are configured — every call will be rejected");
    }
  }

  public boolean isEnabled() {
    return properties.enabled();
  }

  /**
   * The client behind an {@code Authorization} header value, or empty if the header is missing,
   * malformed, or the token is unknown.
   */
  public Optional<ApiClient> authenticate(String authorizationHeader) {
    if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
      return Optional.empty();
    }
    return findByToken(authorizationHeader.substring(BEARER_PREFIX.length()));
  }

  private Optional<ApiClient> findByToken(String presented) {
    byte[] presentedBytes = presented.getBytes(StandardCharsets.UTF_8);
    List<ApiClient> clients = properties.clients();
    for (ApiClient client : clients) {
      byte[] expected = client.token().getBytes(StandardCharsets.UTF_8);
      if (MessageDigest.isEqual(expected, presentedBytes)) {
        return Optional.of(client);
      }
    }
    return Optional.empty();
  }
}
