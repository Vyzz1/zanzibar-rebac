package zanzibar.huynhvy.check.grpc;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import zanzibar.huynhvy.api.AuthorizationServiceGrpc;
import zanzibar.huynhvy.api.CheckRequest;
import zanzibar.huynhvy.api.CheckResponse;
import zanzibar.huynhvy.check.domain.CheckUseCase;
import zanzibar.huynhvy.shared.domain.RelationTuple;

@GrpcService
@RequiredArgsConstructor
public class CheckGrpcService extends AuthorizationServiceGrpc.AuthorizationServiceImplBase {

  private final CheckUseCase checkUseCase;

  @Override
  public void check(CheckRequest request, StreamObserver<CheckResponse> responseObserver) {
    RelationTuple tuple =
        new RelationTuple(
            request.getNamespace(),
            request.getObjectId(),
            request.getRelation(),
            request.getSubjectId());

    boolean allowed = checkUseCase.check(tuple, request.getZookie());

    responseObserver.onNext(CheckResponse.newBuilder().setAllowed(allowed).build());
    responseObserver.onCompleted();
  }
}
