package zanzibar.huynhvy.tuplestore.grpc;

import io.grpc.stub.StreamObserver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import zanzibar.huynhvy.api.AuthorizationServiceGrpc;
import zanzibar.huynhvy.api.WriteTuplesRequest;
import zanzibar.huynhvy.api.WriteTuplesResponse;
import zanzibar.huynhvy.shared.domain.RelationTuple;
import zanzibar.huynhvy.shared.domain.Zookie;
import zanzibar.huynhvy.tuplestore.domain.WriteTuplesUseCase;

@GrpcService
@RequiredArgsConstructor
public class TupleStoreGrpcService extends AuthorizationServiceGrpc.AuthorizationServiceImplBase {

  private final WriteTuplesUseCase writeTuplesUseCase;

  @Override
  public void writeTuples(
      WriteTuplesRequest request, StreamObserver<WriteTuplesResponse> responseObserver) {
    List<RelationTuple> tuples =
        request.getTuplesList().stream()
            .map(
                t ->
                    new RelationTuple(
                        t.getNamespace(), t.getObjectId(), t.getRelation(), t.getSubjectId()))
            .toList();

    Zookie zookie = writeTuplesUseCase.execute(tuples);

    responseObserver.onNext(WriteTuplesResponse.newBuilder().setZookie(zookie.token()).build());
    responseObserver.onCompleted();
  }
}
