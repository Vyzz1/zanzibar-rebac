package zanzibar.huynhvy.namespace.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import zanzibar.huynhvy.shared.auth.ApiClient;
import zanzibar.huynhvy.shared.auth.ApiScope;
import zanzibar.huynhvy.shared.auth.ApiTokenAuthenticator;
import zanzibar.huynhvy.shared.auth.AuthFailureRecorder;

/**
 * Applies the same token model as the gRPC interceptor to the config REST API. Reading a namespace
 * needs {@code read}; changing one needs {@code admin}, because a namespace config decides how
 * every relation is derived — editing it can grant access far more broadly than writing a single
 * tuple.
 *
 * <p>Only {@code /actuator/health} is left open, for liveness probes.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class ApiTokenFilter extends OncePerRequestFilter {

  private final ApiTokenAuthenticator authenticator;
  private final AuthFailureRecorder failures;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !authenticator.isEnabled() || "/actuator/health".equals(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Optional<ApiClient> client =
        authenticator.authenticate(request.getHeader(HttpHeaders.AUTHORIZATION));
    if (client.isEmpty()) {
      reject(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthenticated", request, "unknown");
      return;
    }

    ApiScope required = requiredScope(request.getMethod());
    if (!client.get().hasScope(required)) {
      reject(
          response,
          HttpServletResponse.SC_FORBIDDEN,
          "insufficient_scope",
          request,
          client.get().name());
      return;
    }

    chain.doFilter(request, response);
  }

  private static ApiScope requiredScope(String httpMethod) {
    return "GET".equals(httpMethod) || "HEAD".equals(httpMethod) ? ApiScope.READ : ApiScope.ADMIN;
  }

  private void reject(
      HttpServletResponse response,
      int status,
      String reason,
      HttpServletRequest request,
      String clientName)
      throws IOException {
    failures.record(reason);
    log.warn(
        "Rejected {} {} from '{}': {}",
        request.getMethod(),
        request.getRequestURI(),
        clientName,
        reason);
    response.setStatus(status);
    response.setContentType("application/json");
    response.getWriter().write("{\"code\":\"" + reason.toUpperCase() + "\"}");
  }
}
