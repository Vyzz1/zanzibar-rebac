package zanzibar.huynhvy.namespace.controller;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zanzibar.huynhvy.namespace.domain.GetNamespaceUseCase;
import zanzibar.huynhvy.namespace.domain.NamespaceView;
import zanzibar.huynhvy.namespace.domain.UpsertNamespaceUseCase;
import zanzibar.huynhvy.shared.domain.UsersetRewrite;

@RestController
@RequestMapping("/api/v1/namespaces")
@RequiredArgsConstructor
public class NamespaceController {

  private final UpsertNamespaceUseCase upsertNamespace;
  private final GetNamespaceUseCase getNamespace;

  /** Body is the relations map (relation name to its rewrite); creates the next version. */
  @PutMapping("/{namespace}")
  public NamespaceView put(
      @PathVariable String namespace, @RequestBody Map<String, UsersetRewrite> relations) {
    return upsertNamespace.upsert(namespace, relations);
  }

  @GetMapping("/{namespace}")
  public NamespaceView getLatest(@PathVariable String namespace) {
    return getNamespace.getLatest(namespace);
  }

  @GetMapping("/{namespace}/versions/{version}")
  public NamespaceView getVersion(@PathVariable String namespace, @PathVariable int version) {
    return getNamespace.getVersion(namespace, version);
  }
}
