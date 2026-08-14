package zanzibar.huynhvy.tuplestore.outbox;

/**
 * The kind of tuple change carried by an outbox event, pairing the stored {@code eventType} with
 * the wire {@code operation} consumers see. Producers and the publisher share these so the two can
 * never drift apart.
 */
public enum TupleChangeType {
  CREATED("TUPLE_CREATED", "CREATE"),
  DELETED("TUPLE_DELETED", "DELETE");

  private final String eventType;
  private final String operation;

  TupleChangeType(String eventType, String operation) {
    this.eventType = eventType;
    this.operation = operation;
  }

  /** The value stored in {@code outbox_events.event_type}. */
  public String eventType() {
    return eventType;
  }

  /** The {@code operation} header value published to consumers (CREATE / DELETE). */
  public String operation() {
    return operation;
  }
}
