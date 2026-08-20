package zanzibar.huynhvy.shared.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * API credentials, e.g.
 *
 * <pre>{@code
 * auth:
 *   enabled: true
 *   clients:
 *     - name: app-backend
 *       token: ${WRITER_TOKEN}
 *       scopes: [read, write]
 * }</pre>
 *
 * Disabling auth is meant for local development only; it leaves every endpoint open.
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue List<ApiClient> clients,
    /** This service's own token, sent on outgoing calls to the other services. */
    @DefaultValue("") String outboundToken) {}
