package zanzibar.huynhvy.watch.config;

import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zanzibar.huynhvy.shared.security.ApiTokenAuthenticator;
import zanzibar.huynhvy.shared.security.AuthFailureRecorder;
import zanzibar.huynhvy.shared.security.AuthServerInterceptor;

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
