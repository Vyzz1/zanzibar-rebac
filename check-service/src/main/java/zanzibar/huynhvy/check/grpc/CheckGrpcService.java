package zanzibar.huynhvy.check.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import zanzibar.huynhvy.api.AuthorizationServiceGrpc;
import zanzibar.huynhvy.api.CheckRequest;
import zanzibar.huynhvy.api.CheckResponse;
import zanzibar.huynhvy.api.ReadTuplesRequest;
import zanzibar.huynhvy.api.ReadTuplesResponse;
import zanzibar.huynhvy.check.domain.CheckUseCase;
import zanzibar.huynhvy.check.domain.ReadTuplesResult;
import zanzibar.huynhvy.check.domain.ReadTuplesUseCase;
import zanzibar.huynhvy.shared.domain.RelationTuple;

@GrpcService
@RequiredArgsConstructor
public class CheckGrpcService extends AuthorizationServiceGrpc.AuthorizationServiceImplBase {

  private final CheckUseCase checkUseCase;
  private final ReadTuplesUseCase readTuplesUseCase;

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

  @Override
  public void readTuples(
      ReadTuplesRequest request, StreamObserver<ReadTuplesResponse> responseObserver) {
    try {
      ReadTuplesResult result =
          readTuplesUseCase.read(
              request.getNamespace(),
              request.getObjectId(),
              request.getRelation(),
              request.getSubjectId(),
              request.getPageSize(),
              request.getPageToken());

      ReadTuplesResponse.Builder response =
          ReadTuplesResponse.newBuilder().setNextPageToken(result.nextPageToken());
      result.tuples().forEach(tuple -> response.addTuples(toProto(tuple)));

      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    } catch (IllegalArgumentException e) {
      responseObserver.onError(
          Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
    }
  }

  private static zanzibar.huynhvy.api.RelationTuple toProto(RelationTuple tuple) {
    return zanzibar.huynhvy.api.RelationTuple.newBuilder()
        .setNamespace(tuple.namespace())
        .setObjectId(tuple.objectId())
        .setRelation(tuple.relation())
        .setSubjectId(tuple.subjectId())
        .build();
  }
}
