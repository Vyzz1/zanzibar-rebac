package zanzibar.huynhvy.shared.auth;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * Attaches this service's own token to outgoing gRPC calls, so service-to-service traffic (e.g.
 * check-service fetching namespace config) survives the server-side auth it now faces. Without it
 * those lookups would fail and quietly degrade into "namespace has no config".
 */
public class AuthClientInterceptor implements ClientInterceptor {

  private final String bearerToken;

  public AuthClientInterceptor(String token) {
    this.bearerToken = "Bearer " + token;
  }

  @Override
  public <R, S> ClientCall<R, S> interceptCall(
      MethodDescriptor<R, S> method, CallOptions callOptions, Channel next) {
    return new ForwardingClientCall.SimpleForwardingClientCall<>(
        next.newCall(method, callOptions)) {
      @Override
      public void start(Listener<S> responseListener, Metadata headers) {
        headers.put(AuthServerInterceptor.AUTHORIZATION, bearerToken);
        super.start(responseListener, headers);
      }
    };
  }
}
