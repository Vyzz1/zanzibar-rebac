package zanzibar.huynhvy.watch.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zanzibar.huynhvy.api.WatchEvent;
import zanzibar.huynhvy.api.WatchRequest;
import zanzibar.huynhvy.watch.grpc.WatchGrpcService;
import zanzibar.huynhvy.watch.stream.CursorOutOfRangeException;
import zanzibar.huynhvy.watch.stream.WatchCursor;
import zanzibar.huynhvy.watch.stream.WatchCursorResolver;
import zanzibar.huynhvy.watch.stream.WatchSubscriber;

class WatchGrpcServiceTest {

  private final WatchCursorResolver resolver = mock(WatchCursorResolver.class);
  private final WatchSubscriber subscriber = mock(WatchSubscriber.class);
  private final WatchGrpcService service = new WatchGrpcService(resolver, subscriber);

  @Test
  void subscribes_with_the_resolved_cursor_and_leaves_the_stream_open() {
    WatchCursor cursor = WatchCursor.live();
    when(resolver.resolve("")).thenReturn(cursor);
    StreamObserver<WatchEvent> observer = observer();

    service.watch(request("doc", ""), observer);

    verify(subscriber).subscribe(eq("doc"), eq(cursor), eq(observer));
    verify(observer, never()).onCompleted();
  }

  @Test
  void an_invalid_zookie_fails_the_stream_with_invalid_argument() {
    when(resolver.resolve(any())).thenThrow(new IllegalArgumentException("Invalid Zookie"));

    assertThat(errorFrom("bad").getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    verify(subscriber, never()).subscribe(any(), any(), any());
  }

  @Test
  void a_cursor_past_retention_fails_the_stream_with_out_of_range() {
    when(resolver.resolve(any())).thenThrow(new CursorOutOfRangeException("too old"));

    assertThat(errorFrom("stale").getStatus().getCode()).isEqualTo(Status.Code.OUT_OF_RANGE);
    verify(subscriber, never()).subscribe(any(), any(), any());
  }

  private StatusRuntimeException errorFrom(String zookie) {
    StreamObserver<WatchEvent> observer = observer();
    service.watch(request("doc", zookie), observer);

    ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
    verify(observer).onError(captor.capture());
    return (StatusRuntimeException) captor.getValue();
  }

  private static WatchRequest request(String namespace, String zookie) {
    return WatchRequest.newBuilder().setNamespace(namespace).setZookie(zookie).build();
  }

  @SuppressWarnings("unchecked")
  private static StreamObserver<WatchEvent> observer() {
    return mock(StreamObserver.class);
  }
}
