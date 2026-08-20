package zanzibar.huynhvy.shared.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds {@code auth.*} for every service that scans this package. */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {}
