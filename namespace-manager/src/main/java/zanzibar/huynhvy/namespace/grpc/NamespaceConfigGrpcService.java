package zanzibar.huynhvy.namespace.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import zanzibar.huynhvy.api.GetNamespaceConfigRequest;
import zanzibar.huynhvy.api.GetNamespaceConfigResponse;
import zanzibar.huynhvy.api.NamespaceServiceGrpc;
import zanzibar.huynhvy.namespace.domain.GetNamespaceUseCase;
import zanzibar.huynhvy.namespace.domain.RawNamespaceConfig;
import zanzibar.huynhvy.namespace.exception.NamespaceNotFoundException;

/**
 * Serves the latest namespace config to the check path as a JSON string. check-service parses it
 * with the shared {@code UsersetRewrite} model, so this side stays parse-free.
 */
@GrpcService
@RequiredArgsConstructor
public class NamespaceConfigGrpcService extends NamespaceServiceGrpc.NamespaceServiceImplBase {

  private final GetNamespaceUseCase getNamespace;

  @Override
  public void getNamespaceConfig(
      GetNamespaceConfigRequest request,
      StreamObserver<GetNamespaceConfigResponse> responseObserver) {
    try {
      RawNamespaceConfig config = getNamespace.getLatestRaw(request.getNamespace());
      responseObserver.onNext(
          GetNamespaceConfigResponse.newBuilder()
              .setNamespace(config.namespace())
              .setVersion(config.version())
              .setConfigJson(config.configJson())
              .build());
      responseObserver.onCompleted();
    } catch (NamespaceNotFoundException e) {
      responseObserver.onError(
          Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
    }
  }
}
