package zanzibar.huynhvy.shared.auth;

import java.util.Set;

/**
 * A calling service, identified by a pre-shared token and limited to the scopes it was granted.
 * {@code name} exists so rejections and audit logs say who was refused, never the token.
 */
public record ApiClient(String name, String token, Set<ApiScope> scopes) {

  public boolean hasScope(ApiScope scope) {
    return scopes != null && scopes.contains(scope);
  }
}
