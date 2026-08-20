package zanzibar.huynhvy.tuplestore.config;

import io.grpc.ClientInterceptor;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zanzibar.huynhvy.shared.auth.ApiTokenAuthenticator;
import zanzibar.huynhvy.shared.auth.AuthClientInterceptor;
import zanzibar.huynhvy.shared.auth.AuthFailureRecorder;
import zanzibar.huynhvy.shared.auth.AuthProperties;
import zanzibar.huynhvy.shared.auth.AuthServerInterceptor;

/** Authenticates inbound gRPC calls, and presents this service's token on outbound ones. */
@Configuration
public class GrpcAuthConfig {

  @Bean
  @GrpcGlobalServerInterceptor
  public ServerInterceptor authServerInterceptor(
      ApiTokenAuthenticator authenticator, AuthFailureRecorder failures) {
    return new AuthServerInterceptor(authenticator, failures);
  }

  /** tuple-store calls namespace-manager to validate a write's relation. */
  @Bean
  @GrpcGlobalClientInterceptor
  public ClientInterceptor authClientInterceptor(AuthProperties properties) {
    return new AuthClientInterceptor(properties.outboundToken());
  }
}
