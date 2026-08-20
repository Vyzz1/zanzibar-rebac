package zanzibar.huynhvy.loadtest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import zanzibar.huynhvy.api.AuthorizationServiceGrpc;
import zanzibar.huynhvy.api.DeleteTuplesRequest;
import zanzibar.huynhvy.api.RelationTuple;
import zanzibar.huynhvy.api.WriteTuplesRequest;

/**
 * Puts the fixture in place: a namespace whose {@code viewer} is partly derived, and the tuples the
 * scenarios check against.
 *
 * <p>Tuples are deleted before being written, because {@code relation_tuples} is unique on
 * (namespace, object, relation, subject) and a second run would otherwise collide. Deleting a tuple
 * that is not there is a no-op, so this works on an empty database too.
 */
final class Seeder {

  /** viewer = union(this, computedUserset(editor)) — so a check can be direct or derived. */
  private static final String CONFIG =
      """
      {"editor":{"this":{}},\
      "viewer":{"union":{"children":[{"this":{}},{"computedUserset":{"relation":"editor"}}]}}}""";

  private final Config config;
  private final AuthorizationServiceGrpc.AuthorizationServiceBlockingStub writeStub;

  Seeder(Config config, AuthorizationServiceGrpc.AuthorizationServiceBlockingStub writeStub) {
    this.config = config;
    this.writeStub = writeStub;
  }

  void seed() throws IOException, InterruptedException {
    putNamespaceConfig();

    List<RelationTuple> fixture =
        List.of(
            // Direct: bob is written straight onto the object.
            tuple(config.namespace(), config.objectId(), "viewer", "user:bob"),
            // Derived: alice is an editor, and editors are viewers.
            tuple(config.namespace(), config.objectId(), "editor", "user:alice"),
            // Group: the object grants viewer to a userset carol belongs to.
            tuple(config.groupNamespace(), config.objectId(), "viewer", "group:eng#member"),
            tuple("group", "eng", "member", "user:carol"));

    writeStub.deleteTuples(DeleteTuplesRequest.newBuilder().addAllTuples(fixture).build());
    writeStub.writeTuples(WriteTuplesRequest.newBuilder().addAllTuples(fixture).build());
  }

  private void putNamespaceConfig() throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(config.namespaceRestUrl() + "/api/v1/namespaces/" + config.namespace()))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + config.token())
            .timeout(Duration.ofSeconds(10))
            .PUT(HttpRequest.BodyPublishers.ofString(CONFIG))
            .build();

    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() / 100 != 2) {
      throw new IllegalStateException(
          "Seeding the namespace config failed: HTTP "
              + response.statusCode()
              + " "
              + response.body());
    }
  }

  private static RelationTuple tuple(
      String namespace, String objectId, String relation, String subjectId) {
    return RelationTuple.newBuilder()
        .setNamespace(namespace)
        .setObjectId(objectId)
        .setRelation(relation)
        .setSubjectId(subjectId)
        .build();
  }
}
