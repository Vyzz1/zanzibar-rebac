package zanzibar.huynhvy.shared.auth;

import java.util.Map;

/**
 * Which scope each gRPC method requires. Anything not listed needs {@link ApiScope#ADMIN}, so a
 * newly added method is locked down until it is classified here rather than silently inheriting the
 * weakest scope.
 */
public final class GrpcMethodScopes {

  private static final String AUTHORIZATION = "zanzibar.api.v1.AuthorizationService/";
  private static final String NAMESPACE = "zanzibar.api.v1.NamespaceService/";

  /** gRPC's own health service, left open for liveness probes. */
  private static final String HEALTH_PREFIX = "grpc.health.v1.";

  private static final Map<String, ApiScope> SCOPES =
      Map.of(
          AUTHORIZATION + "Check", ApiScope.READ,
          AUTHORIZATION + "BatchCheck", ApiScope.READ,
          AUTHORIZATION + "ReadTuples", ApiScope.READ,
          AUTHORIZATION + "Expand", ApiScope.READ,
          AUTHORIZATION + "Watch", ApiScope.READ,
          NAMESPACE + "GetNamespaceConfig", ApiScope.READ,
          AUTHORIZATION + "WriteTuples", ApiScope.WRITE,
          AUTHORIZATION + "DeleteTuples", ApiScope.WRITE);

  private GrpcMethodScopes() {}

  public static boolean isOpen(String fullMethodName) {
    return fullMethodName.startsWith(HEALTH_PREFIX);
  }

  public static ApiScope required(String fullMethodName) {
    return SCOPES.getOrDefault(fullMethodName, ApiScope.ADMIN);
  }
}
