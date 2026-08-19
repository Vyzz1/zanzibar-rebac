package zanzibar.huynhvy.watch.grpc;

import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import zanzibar.huynhvy.api.AuthorizationServiceGrpc;
import zanzibar.huynhvy.api.WatchEvent;
import zanzibar.huynhvy.api.WatchRequest;
import zanzibar.huynhvy.watch.stream.CursorOutOfRangeException;
import zanzibar.huynhvy.watch.stream.WatchCursor;
import zanzibar.huynhvy.watch.stream.WatchCursorResolver;
import zanzibar.huynhvy.watch.stream.WatchSubscriber;

/**
 * Streams tuple changes for a namespace. The request's Zookie is a resume cursor: with one, the
 * client is replayed everything since the write it already saw and then continues live; without
 * one, it only sees changes from now on. The stream stays open until the client cancels.
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class WatchGrpcService extends AuthorizationServiceGrpc.AuthorizationServiceImplBase {

  private final WatchCursorResolver cursorResolver;
  private final WatchSubscriber subscriber;

  @Override
  public void watch(WatchRequest request, StreamObserver<WatchEvent> responseObserver) {
    WatchCursor cursor;
    try {
      cursor = cursorResolver.resolve(request.getZookie());
    } catch (IllegalArgumentException e) {
      responseObserver.onError(
          Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
      return;
    } catch (CursorOutOfRangeException e) {
      responseObserver.onError(
          Status.OUT_OF_RANGE.withDescription(e.getMessage()).asRuntimeException());
      return;
    }

    AutoCloseable subscription =
        subscriber.subscribe(request.getNamespace(), cursor, responseObserver);

    if (responseObserver instanceof ServerCallStreamObserver<WatchEvent> serverObserver) {
      serverObserver.setOnCancelHandler(() -> close(subscription));
    }
    // Deliberately no onCompleted(): the stream stays open until the client cancels.
  }

  private void close(AutoCloseable subscription) {
    try {
      subscription.close();
    } catch (Exception e) {
      log.warn("Failed to stop a cancelled Watch subscription", e);
    }
  }
}
