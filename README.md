# DB Control API

This project provides a reusable Spring Boot starter for request-header-based `RoutingDataSource`.
It is designed for internal APIs that must route the same JPA code to different physical databases by project.

The core idea is not `tenant_id` filtering inside one database.
Instead, the application selects a physical database from a request header such as `X-Project`.

Java 21 and Spring Boot 3.x are used in this repository.
See the Korean version here: [README_kor.md](./README_kor.md).

## Maven Central Coordinates

```text
groupId    = io.github.daewook0401.headerroute
artifactId = header-routing-datasource-spring-boot-starter
version    = 0.1.0
```

## What It Solves

Use this starter when:

- one internal API serves multiple projects
- each project has its own physical database
- you want to avoid project-specific `if/else` logic in controllers, services, and repositories

Examples:

- `X-Project: tad` -> `tad_db`
- `X-Project: fa` -> `fa_db`
- `X-Project: discord` -> `discord_db`

## Modules

- `header-routing-datasource-spring-boot-starter`
  - reusable starter library
  - `ThreadLocal`-based `ProjectContext`
  - `ProjectContextFilter` for header validation
  - `ProjectRoutingDataSource` based on `AbstractRoutingDataSource`
  - `ProjectRoutingProperties` for `application.yml` binding
- `example-internal-api`
  - sample application using the starter

## How It Works

1. `ProjectContextFilter` reads `X-Project` for each request.
2. Missing header returns `400 Bad Request`.
3. Unregistered header value returns `403 Forbidden`.
4. A valid project key is stored in `ProjectContext` via `ThreadLocal`.
5. `ProjectRoutingDataSource` reads the current key and selects the matching `HikariDataSource`.
6. `ProjectContext.clear()` is always called in `finally`.

Business code stays unchanged. JPA repositories are used normally.

## Recommended API Structure

Recommended:

```text
Frontend
  -> Public API / BFF / External API
      -> Internal API
          -> Header-routing datasource
              -> Project-specific physical DB
```

Typical flow:

1. The frontend sends only user/business requests.
2. The public API validates user access to the project.
3. Only the trusted internal caller creates `X-Project`.
4. The internal API uses that header to route to the physical database.

This starter is better for internal APIs than directly public APIs.

## Risk Factors

This design becomes risky when:

- the frontend sends `X-Project` directly
- a public API trusts the header without access validation
- the project key is treated as authorization rather than routing context
- the request flow later jumps to `@Async`, another pool, or a message consumer without context propagation

## Quick Start

### 1. Add the dependency

After publication to Maven Central:

```gradle
repositories {
    mavenCentral()
}

dependencies {
    implementation 'io.github.daewook0401.headerroute:header-routing-datasource-spring-boot-starter:0.1.0'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    runtimeOnly 'org.postgresql:postgresql'
}
```

Inside the same multi-module repository:

```gradle
dependencies {
    implementation project(':header-routing-datasource-spring-boot-starter')
}
```

### 2. Configure `application.yml`

```yaml
app:
  routing:
    datasource:
      header-name: X-Project
      strict: true
      projects:
        tad:
          jdbc-url: jdbc:postgresql://192.168.219.105:5001/tad_db
          username: tad_app
          password: tad_app_password
          driver-class-name: org.postgresql.Driver
        fa:
          jdbc-url: jdbc:postgresql://192.168.219.105:5001/fa_db
          username: fa_app
          password: fa_app_password
          driver-class-name: org.postgresql.Driver
```

### 3. Keep using JPA normally

```java
@Entity
@Table(schema = "auth", name = "tb_user")
public class User {
    @Id
    private Long id;
}
```

```java
public interface UserRepository extends JpaRepository<User, Long> {
}
```

### 4. Send requests with `X-Project`

```bash
curl -H "X-Project: tad" http://localhost:8081/internal/auth/users/1
curl -H "X-Project: fa" http://localhost:8081/internal/auth/users/1
```

The same controller and repository can now reach different physical databases depending on the header.

## Consumer Example

Minimal consumer `build.gradle`:

```gradle
dependencies {
    implementation 'io.github.daewook0401.headerroute:header-routing-datasource-spring-boot-starter:0.1.0'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    runtimeOnly 'org.postgresql:postgresql'
}
```

Minimal request pattern:

- frontend -> public API
- public API validates user and project access
- public API -> internal API with `X-Project`

Not recommended:

- frontend -> internal API with user-controlled `X-Project`

## Error Policy

- missing `X-Project` -> `400 Bad Request`
- unregistered project -> `403 Forbidden`
- missing user -> `404 Not Found`

## Debug Logging

```yaml
logging:
  level:
    io.headerroute.routing.datasource: DEBUG
```

Example logs:

```text
Resolved project header. uri=/internal/auth/users/1, header=X-Project, project=tad
Routing datasource lookup for project=tad
Clearing project context. uri=/internal/auth/users/1, project=tad
```

## Maven Central Publishing

This repository is now prepared for Sonatype Central Portal publishing through:

- Gradle `maven-publish`
- Gradle `signing`
- Sonatype OSSRH staging compatibility endpoint

Before the first Central release, you still need:

- a verified Sonatype Central namespace
  recommended: `io.github.daewook0401`
- a Sonatype Central Portal user token
- a PGP private key and passphrase for signing

Store them in `~/.gradle/gradle.properties`.
An example is provided in [gradle.properties.example](./gradle.properties.example).

Example:

```properties
centralPortalUsername=your_central_portal_token_username
centralPortalPassword=your_central_portal_token_password
centralNamespace=io.github.daewook0401
centralPublishingType=automatic
signingKey=your_ascii_armored_private_key
signingPassword=your_signing_passphrase
```

Release command:

```bash
./gradlew :header-routing-datasource-spring-boot-starter:publishReleaseToMavenCentral
```

If `centralPublishingType=automatic`, a valid deployment proceeds automatically.
If `centralPublishingType=user_managed`, upload succeeds and final publication is completed in the Central Portal UI.

List current staging repositories:

```bash
./gradlew :header-routing-datasource-spring-boot-starter:listCentralStagingRepositories
```

## Build And Run

Build:

```bash
./gradlew build
```

Run the sample app:

```bash
./gradlew :example-internal-api:bootRun
```

## License

This project is licensed under the MIT License.
See [LICENSE](./LICENSE).
