package zanzibar.huynhvy.namespace.config;

import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zanzibar.huynhvy.shared.auth.ApiTokenAuthenticator;
import zanzibar.huynhvy.shared.auth.AuthFailureRecorder;
import zanzibar.huynhvy.shared.auth.AuthServerInterceptor;

/** Authenticates inbound gRPC calls. */
@Configuration
public class GrpcAuthConfig {

  @Bean
  @GrpcGlobalServerInterceptor
  public ServerInterceptor authServerInterceptor(
      ApiTokenAuthenticator authenticator, AuthFailureRecorder failures) {
    return new AuthServerInterceptor(authenticator, failures);
  }
}
