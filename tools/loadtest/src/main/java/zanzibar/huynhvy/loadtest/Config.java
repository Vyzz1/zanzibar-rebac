package zanzibar.huynhvy.loadtest;

import java.util.HashMap;
import java.util.Map;

/** Command-line options, all with defaults matching a local `make infra` setup. */
record Config(
    String checkHost,
    int checkPort,
    String tupleHost,
    int tuplePort,
    String namespaceRestUrl,
    String token,
    String zookieSecret,
    int concurrency,
    int durationSeconds,
    int warmupSeconds,
    String namespace,
    String groupNamespace,
    String objectId) {

  static Config parse(String[] args) {
    Map<String, String> options = new HashMap<>();
    for (String arg : args) {
      int eq = arg.indexOf('=');
      if (!arg.startsWith("--") || eq < 0) {
        throw new IllegalArgumentException("Expected --key=value, got: " + arg);
      }
      options.put(arg.substring(2, eq), arg.substring(eq + 1));
    }

    return new Config(
        options.getOrDefault("check-host", "localhost"),
        Integer.parseInt(options.getOrDefault("check-port", "9091")),
        options.getOrDefault("tuple-host", "localhost"),
        Integer.parseInt(options.getOrDefault("tuple-port", "9092")),
        options.getOrDefault("namespace-url", "http://localhost:8084"),
        options.getOrDefault("token", "dev-only-token-change-me"),
        options.getOrDefault("zookie-secret", "dev-only-secret-change-me"),
        Integer.parseInt(options.getOrDefault("concurrency", "32")),
        Integer.parseInt(options.getOrDefault("duration", "20")),
        Integer.parseInt(options.getOrDefault("warmup", "5")),
        options.getOrDefault("namespace", "bench"),
        options.getOrDefault("group-namespace", "benchgrp"),
        options.getOrDefault("object", "report.pdf"));
  }

  static String usage() {
    return """
           Usage: java -jar loadtest.jar [--key=value ...]

             --check-host, --check-port     check-service gRPC   (localhost:9091)
             --tuple-host, --tuple-port     tuple-store gRPC     (localhost:9092)
             --namespace-url                namespace-manager    (http://localhost:8084)
             --token                        API bearer token
             --zookie-secret                must match the services' ZOOKIE_SECRET
             --concurrency                  virtual threads issuing calls (32)
             --duration                     measured seconds per scenario (20)
             --warmup                       unmeasured seconds per scenario (5)
           """;
  }
}
