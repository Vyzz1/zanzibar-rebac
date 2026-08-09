package zanzibar.huynhvy.watch.grpc;

import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import zanzibar.huynhvy.api.AuthorizationServiceGrpc;
import zanzibar.huynhvy.api.WatchEvent;
import zanzibar.huynhvy.api.WatchRequest;
import zanzibar.huynhvy.watch.stream.StreamRegistry;

/**
 * Streams tuple-change events for a namespace. The stream is registered and left open — events are
 * pushed by the RabbitMQ consumer via {@link StreamRegistry} — and unregistered when the client
 * cancels. Live-only: the {@code zookie} resume cursor is not yet honored (streams from now on).
 */
@GrpcService
@RequiredArgsConstructor
public class WatchGrpcService extends AuthorizationServiceGrpc.AuthorizationServiceImplBase {

  private final StreamRegistry registry;

  @Override
  public void watch(WatchRequest request, StreamObserver<WatchEvent> responseObserver) {
    String namespace = request.getNamespace();
    registry.register(namespace, responseObserver);

    if (responseObserver instanceof ServerCallStreamObserver<WatchEvent> serverObserver) {
      serverObserver.setOnCancelHandler(() -> registry.unregister(namespace, responseObserver));
    }
    // Deliberately no onCompleted(): the stream stays open until the client cancels.
  }
}
