package zanzibar.huynhvy.watch.grpc;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import zanzibar.huynhvy.api.AuthorizationServiceGrpc;
import zanzibar.huynhvy.api.WatchEvent;
import zanzibar.huynhvy.api.WatchRequest;

@GrpcService
public class WatchGrpcService extends AuthorizationServiceGrpc.AuthorizationServiceImplBase {

  @Override
  public void watch(WatchRequest request, StreamObserver<WatchEvent> responseObserver) {
    // TODO: register the stream and push tuple-change events. Placeholder ends the stream.
    responseObserver.onCompleted();
  }
}
