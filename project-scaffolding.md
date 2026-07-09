You are scaffolding a multi-module Maven project for a Zanzibar-inspired ReBAC
authorization engine. Do not implement business logic — only create project
structure, pom.xml files, and empty placeholder classes with correct package
names and annotations.

## Project root: zanzibar-rebac/

## Global constraints
- Spring Boot version: 3.5.1
- Java version: 21
- Config format: YAML only (no application.properties)
- Maven multi-module (not Gradle)
- All pom.xml use a root parent pom for dependency management

## Module list and groupIds

| Module            | artifactId          | groupId                              |
|-------------------|---------------------|--------------------------------------|
| root              | zanzibar-rebac      | zanzibar.huynhvy                     |
| api               | zanzibar-api        | zanzibar.huynhvy.api                 |
| shared            | zanzibar-shared     | zanzibar.huynhvy.shared              |
| check-service     | check-service       | zanzibar.huynhvy.check               |
| tuple-store       | tuple-store         | zanzibar.huynhvy.tuplestore          |
| watch-service     | watch-service       | zanzibar.huynhvy.watch               |
| namespace-manager | namespace-manager   | zanzibar.huynhvy.namespace           |

## Root pom.xml requirements
- packaging: pom
- Declare all modules
- dependencyManagement section must import:
  - spring-boot-dependencies BOM 3.5.1
  - grpc-bom 1.65.0
  - testcontainers-bom 1.20.1
- Common dependencies (apply to all service modules via <dependencies> in root):
  - spring-boot-starter
  - spring-boot-starter-test
  - lombok
- Do NOT hardcode versions anywhere except in dependencyManagement

## Module: api (zanzibar-api)
- packaging: jar
- Purpose: will hold protobuf-generated classes (add placeholder only)
- Dependencies: grpc-stub, grpc-protobuf, javax.annotation-api
- Add protobuf-maven-plugin config (commented out — proto files not created yet)
- Create empty package: zanzibar.huynhvy.api
- Create placeholder file: AuthorizationServiceGrpc.java with comment
  "// generated from proto — placeholder"

## Module: shared (zanzibar-shared)
- packaging: jar
- Dependencies: none beyond common
- Create the following empty classes with correct package zanzibar.huynhvy.shared:
  - domain/RelationTuple.java — record with fields: String namespace,
    String objectId, String relation, String subjectId
  - domain/Zookie.java — record with fields: String token
  - domain/UsersetRewrite.java — sealed interface with empty permit list
    (add comment listing: Union, Intersection, Exclusion, This, ComputedUserset)
  - security/ZookieValidator.java — @Component, single method
    boolean validate(Zookie zookie) returns false (placeholder)
  - observability/TracingConfig.java — @Configuration empty class
  - testing/BaseIntegrationTest.java — abstract class annotated
    @SpringBootTest, import Testcontainers

## Module: check-service
- packaging: jar
- Parent: root pom
- Dependencies:
  - zanzibar-api (sibling module)
  - zanzibar-shared (sibling module)
  - spring-boot-starter-web
  - net.devh:grpc-server-spring-boot-starter:3.1.0.RELEASE
  - spring-boot-starter-data-redis
  - spring-boot-starter-data-jpa
  - postgresql (runtime)
  - micrometer-registry-prometheus
  - testcontainers (test scope): postgresql, redis
- Main class: CheckServiceApplication.java in package zanzibar.huynhvy.check
- Create empty placeholder classes:
  - grpc/CheckGrpcService.java — @GrpcService, empty class
  - grpc/interceptor/ZookieInterceptor.java — implements
    ServerInterceptor (grpc), empty
  - grpc/interceptor/TracingInterceptor.java — implements
    ServerInterceptor, empty
  - domain/CheckUseCase.java — @Service, empty
  - domain/GraphTraverser.java — @Component, empty
  - domain/CycleDetector.java — @Component, empty
  - cache/TupleCache.java — @Component, empty
  - cache/CacheKeyStrategy.java — @Component, empty
  - repository/TupleReadRepository.java — interface extends JpaRepository
  - config/RedisConfig.java — @Configuration, empty
  - config/GrpcServerConfig.java — @Configuration, empty
- application.yml:
  spring.application.name: check-service
  server.port: 8081
  grpc.server.port: 9091
  spring.datasource: url/username/password placeholders
  spring.data.redis.host/port placeholders
  management.endpoints.web.exposure.include: health,prometheus

## Module: tuple-store
- packaging: jar
- Dependencies:
  - zanzibar-api, zanzibar-shared
  - spring-boot-starter-web
  - net.devh:grpc-server-spring-boot-starter:3.1.0.RELEASE
  - spring-boot-starter-data-jpa
  - postgresql (runtime)
  - spring-kafka
  - org.jooq:jooq
  - testcontainers (test): postgresql, kafka
- Main class: TupleStoreApplication.java in zanzibar.huynhvy.tuplestore
- Placeholder classes:
  - grpc/TupleStoreGrpcService.java — @GrpcService, empty
  - domain/WriteTuplesUseCase.java — @Service, empty
  - domain/ZookieMinter.java — @Component, single method
    Zookie mint(long commitTimestamp) returns new Zookie("placeholder")
  - repository/TupleWriteRepository.java — interface
  - repository/TupleWriteRepositoryImpl.java — @Repository, empty impl
  - outbox/OutboxPoller.java — @Component, @Scheduled placeholder method
  - outbox/OutboxRepository.java — interface extends JpaRepository
  - kafka/TupleEventPublisher.java — @Component, empty
- application.yml:
  spring.application.name: tuple-store
  server.port: 8082
  grpc.server.port: 9092
  spring.datasource: placeholders
  spring.kafka.bootstrap-servers: placeholder
  management endpoints same as check-service

## Module: watch-service
- packaging: jar
- Dependencies:
  - zanzibar-api, zanzibar-shared
  - spring-boot-starter-web
  - net.devh:grpc-server-spring-boot-starter:3.1.0.RELEASE
  - spring-kafka
  - testcontainers (test): kafka
- Main class: WatchServiceApplication.java in zanzibar.huynhvy.watch
- Placeholder classes:
  - grpc/WatchGrpcService.java — @GrpcService, empty
  - kafka/TupleChangeConsumer.java — @Component, @KafkaListener placeholder
  - stream/StreamRegistry.java — @Component, empty
  - stream/WatchCursor.java — record with field: String offset
- application.yml:
  spring.application.name: watch-service
  server.port: 8083
  grpc.server.port: 9093
  spring.kafka.bootstrap-servers: placeholder

## Module: namespace-manager
- packaging: jar
- Dependencies:
  - zanzibar-api, zanzibar-shared
  - spring-boot-starter-web
  - spring-boot-starter-data-jpa
  - postgresql (runtime)
  - testcontainers (test): postgresql
- Main class: NamespaceManagerApplication.java in zanzibar.huynhvy.namespace
- Placeholder classes:
  - controller/NamespaceController.java — @RestController, @RequestMapping("/api/v1/namespaces"), empty
  - domain/NamespaceConfig.java — @Entity, empty
  - domain/ValidateNamespaceUseCase.java — @Service, empty
  - repository/NamespaceConfigRepository.java — interface extends JpaRepository
- application.yml:
  spring.application.name: namespace-manager
  server.port: 8084
  spring.datasource: placeholders

## infra/ directory (outside Maven modules)
Create the following files at project root level (not inside any module):

infra/docker-compose.yml — include services:
  - postgres:16-alpine, port 5432, env POSTGRES_PASSWORD=secret,
    POSTGRES_DB=zanzibar
  - redis:7-alpine, port 6379
  - confluentinc/cp-kafka:7.6.0, port 9092,
    env KAFKA_ZOOKEEPER_CONNECT, KAFKA_ADVERTISED_LISTENERS
  - confluentinc/cp-zookeeper:7.6.0, port 2181

## Output instructions
- Create ALL files. Do not skip any file listed above.
- Every Java file must compile (correct imports, no syntax errors).
- Placeholder classes have empty method bodies returning default values.
- Do not implement any real logic.
- After creating all files, run: find zanzibar-rebac -type f | sort
  to verify the file tree and print it.
- Do not explain what you are doing — just create the files.
