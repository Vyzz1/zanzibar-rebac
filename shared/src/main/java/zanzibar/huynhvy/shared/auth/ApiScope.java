package zanzibar.huynhvy.shared.auth;

/**
 * What a calling service is allowed to do with the API. Scopes do not imply one another — a client
 * lists every scope it needs, so a token's reach is exactly what its configuration says.
 */
public enum ApiScope {
  /** Ask questions: Check, BatchCheck, ReadTuples, Expand, Watch, GetNamespaceConfig. */
  READ,
  /** Change grants: WriteTuples, DeleteTuples. */
  WRITE,
  /** Change the rules themselves: namespace config writes. */
  ADMIN
}
