package zanzibar.huynhvy.check.grpc;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import zanzibar.huynhvy.api.AuthorizationServiceGrpc;
import zanzibar.huynhvy.api.CheckRequest;
import zanzibar.huynhvy.api.CheckResponse;

@GrpcService
public class CheckGrpcService extends AuthorizationServiceGrpc.AuthorizationServiceImplBase {

  @Override
  public void check(CheckRequest request, StreamObserver<CheckResponse> responseObserver) {
    // TODO: delegate to CheckUseCase. Placeholder fails closed (deny).
    responseObserver.onNext(CheckResponse.newBuilder().setAllowed(false).build());
    responseObserver.onCompleted();
  }
}
