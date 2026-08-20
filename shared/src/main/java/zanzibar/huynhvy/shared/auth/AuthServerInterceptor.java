package zanzibar.huynhvy.shared.auth;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Rejects gRPC calls that carry no valid credentials, or whose client lacks the scope the method
 * needs.
 *
 * <p>The two failures are kept distinct: an unknown token is {@code UNAUTHENTICATED} ("I don't know
 * who you are"), while a known client without the scope is {@code PERMISSION_DENIED} ("I know you,
 * and you may not do this"). Without the scope check any caller able to Check could also grant
 * itself access with WriteTuples.
 */
@Slf4j
@RequiredArgsConstructor
public class AuthServerInterceptor implements ServerInterceptor {

  public static final Metadata.Key<String> AUTHORIZATION =
      Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

  private final ApiTokenAuthenticator authenticator;
  private final AuthFailureRecorder failures;

  @Override
  public <R, S> ServerCall.Listener<R> interceptCall(
      ServerCall<R, S> call, Metadata headers, ServerCallHandler<R, S> next) {
    String method = call.getMethodDescriptor().getFullMethodName();
    if (!authenticator.isEnabled() || GrpcMethodScopes.isOpen(method)) {
      return next.startCall(call, headers);
    }

    Optional<ApiClient> client = authenticator.authenticate(headers.get(AUTHORIZATION));
    if (client.isEmpty()) {
      return deny(
          call,
          Status.UNAUTHENTICATED.withDescription("Missing or invalid API token"),
          "unauthenticated",
          method,
          "unknown");
    }

    ApiScope required = GrpcMethodScopes.required(method);
    if (!client.get().hasScope(required)) {
      return deny(
          call,
          Status.PERMISSION_DENIED.withDescription("Token lacks the " + required + " scope"),
          "insufficient_scope",
          method,
          client.get().name());
    }

    return next.startCall(call, headers);
  }

  private <R, S> ServerCall.Listener<R> deny(
      ServerCall<R, S> call, Status status, String reason, String method, String clientName) {
    failures.record(reason);
    log.warn("Rejected {} from '{}': {}", method, clientName, reason);
    call.close(status, new Metadata());
    return new ServerCall.Listener<>() {};
  }
}
