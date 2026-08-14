package zanzibar.huynhvy.tuplestore.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import zanzibar.huynhvy.api.AuthorizationServiceGrpc;
import zanzibar.huynhvy.api.DeleteTuplesRequest;
import zanzibar.huynhvy.api.DeleteTuplesResponse;
import zanzibar.huynhvy.api.WriteTuplesRequest;
import zanzibar.huynhvy.api.WriteTuplesResponse;
import zanzibar.huynhvy.shared.domain.RelationTuple;
import zanzibar.huynhvy.shared.domain.Zookie;
import zanzibar.huynhvy.tuplestore.domain.DeleteTuplesUseCase;
import zanzibar.huynhvy.tuplestore.domain.WriteTuplesUseCase;

@GrpcService
@RequiredArgsConstructor
public class TupleStoreGrpcService extends AuthorizationServiceGrpc.AuthorizationServiceImplBase {

  private final WriteTuplesUseCase writeTuplesUseCase;
  private final DeleteTuplesUseCase deleteTuplesUseCase;

  @Override
  public void writeTuples(
      WriteTuplesRequest request, StreamObserver<WriteTuplesResponse> responseObserver) {
    try {
      Zookie zookie = writeTuplesUseCase.execute(toTuples(request.getTuplesList()));
      responseObserver.onNext(WriteTuplesResponse.newBuilder().setZookie(zookie.token()).build());
      responseObserver.onCompleted();
    } catch (IllegalArgumentException e) {
      responseObserver.onError(
          Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void deleteTuples(
      DeleteTuplesRequest request, StreamObserver<DeleteTuplesResponse> responseObserver) {
    try {
      Zookie zookie = deleteTuplesUseCase.execute(toTuples(request.getTuplesList()));
      responseObserver.onNext(DeleteTuplesResponse.newBuilder().setZookie(zookie.token()).build());
      responseObserver.onCompleted();
    } catch (IllegalArgumentException e) {
      responseObserver.onError(
          Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
    }
  }

  private static List<RelationTuple> toTuples(List<zanzibar.huynhvy.api.RelationTuple> protos) {
    return protos.stream()
        .map(
            t ->
                new RelationTuple(
                    t.getNamespace(), t.getObjectId(), t.getRelation(), t.getSubjectId()))
        .toList();
  }
}
