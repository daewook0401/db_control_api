# DB Control API

This project provides a reusable Spring Boot starter for request-header-based `RoutingDataSource`.
It is designed for cases where the same internal API and the same JPA repository code must route to different physical databases per project.

The key idea is not `tenant_id` column filtering. Instead, the application selects a physical database by using a header such as `X-Project`.

Java 21 and Spring Boot 3.x are used in this repository.

See the Korean version here: [README_kor.md](./README_kor.md)

## What It Solves

Use this starter when:

- one internal API serves multiple projects
- each project has its own physical database
- you want to keep `Controller`, `Service`, and `Repository` code free of project-specific branching

Examples:

- `X-Project: tad` -> `tad_db`
- `X-Project: fa` -> `fa_db`
- `X-Project: discord` -> `discord_db`

The purpose is to remove scattered `if/else` logic from application code and centralize database selection in infrastructure code.

## Modules

- `header-routing-datasource-spring-boot-starter`
  - reusable starter library
  - `ThreadLocal`-based `ProjectContext`
  - `ProjectContextFilter` for request header validation
  - `ProjectRoutingDataSource` based on `AbstractRoutingDataSource`
  - `ProjectRoutingProperties` for `application.yml` binding
- `example-internal-api`
  - sample application using the starter
  - includes `auth.tb_user` entity, JPA repository, service, and controller

## How It Works

1. `ProjectContextFilter` reads the `X-Project` header for each request.
2. If the header is missing, it returns `400 Bad Request`.
3. If the header value is not registered, it returns `403 Forbidden`.
4. If valid, it stores the project key in `ProjectContext` using `ThreadLocal`.
5. When JPA needs a connection, `ProjectRoutingDataSource` reads `ProjectContext.get()`.
6. The matching `HikariDataSource` is selected for the current request.
7. After the request finishes, `ProjectContext.clear()` is always called in `finally`.

Because of this flow, business code does not need project-specific routing logic. JPA repositories remain unchanged.

## Recommended API Structure

This starter is best suited for internal API environments.

Recommended structure:

```text
Frontend
  -> Public API / BFF / External API server
      -> Internal API server
          -> Header-routing datasource
              -> Project-specific physical DB
```

Example:

```text
Frontend
  -> 125 external-api
      -> 105 internal-api
          -> tad_db / fa_db / discord_db
```

Recommended flow:

1. The frontend sends only normal authentication and business requests.
2. The external API server validates the user and project access.
3. Only the trusted internal caller creates `X-Project`.
4. The internal API uses that header to select the physical database.

Core principle:

- `X-Project` should be an internally derived value, not a value directly trusted from the public client.
- This library is better for internal APIs than public-facing APIs.

## When This Is Safe

This approach is reasonably safe when:

- the internal API is reachable only inside a private network
- external users cannot call the internal API directly
- only trusted internal services can create `X-Project`
- project authorization is validated before the internal call is made

## Risk Factors

This design becomes risky when:

- the frontend sends `X-Project` directly
- a public API trusts the header and switches databases immediately
- the system routes by project key without validating user access
- the request flow depends on `ThreadLocal` but later jumps to `@Async`, another thread pool, or a message consumer

In short:

- the header is fine as a routing hint
- the header should not be treated as the source of authorization

## Quick Start

### 1. Add the dependency

If using the local Maven repository:

```gradle
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'io.headerroute:header-routing-datasource-spring-boot-starter:0.0.1'
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
        discord:
          jdbc-url: jdbc:postgresql://192.168.219.105:5001/discord_db
          username: discord_app
          password: discord_app_password
          driver-class-name: org.postgresql.Driver
```

To onboard a new project, add a new entry under `projects` without changing application code.

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

No project-specific branching is needed in controllers, services, or repositories.

### 4. Send requests with `X-Project`

```bash
curl -H "X-Project: tad" http://localhost:8081/internal/auth/users/1
curl -H "X-Project: fa" http://localhost:8081/internal/auth/users/1
curl -H "X-Project: discord" "http://localhost:8081/internal/auth/users/by-email?email=user@example.com"
```

The same API can now reach different physical databases depending on the header.

## Usage Example

Below is a minimal consumer application example.

### `build.gradle`

```gradle
dependencies {
    implementation 'io.headerroute:header-routing-datasource-spring-boot-starter:0.0.1'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    runtimeOnly 'org.postgresql:postgresql'
}
```

### `application.yml`

```yaml
app:
  routing:
    datasource:
      header-name: X-Project
      strict: true
      projects:
        tad:
          jdbc-url: jdbc:postgresql://localhost:5432/tad_db
          username: tad_app
          password: tad_password
          driver-class-name: org.postgresql.Driver
        fa:
          jdbc-url: jdbc:postgresql://localhost:5432/fa_db
          username: fa_app
          password: fa_password
          driver-class-name: org.postgresql.Driver
```

### Entity

```java
@Entity
@Table(schema = "auth", name = "tb_user")
public class User {

    @Id
    private Long id;

    private String email;
}
```

### Repository

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
}
```

### Controller

```java
@RestController
@RequestMapping("/internal/auth/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) {
        return userRepository.findById(id).orElseThrow();
    }
}
```

### Requests

```bash
curl -H "X-Project: tad" http://localhost:8080/internal/auth/users/1
curl -H "X-Project: fa" http://localhost:8080/internal/auth/users/1
```

The controller and repository stay the same. Only the request header changes, and the physical database changes with it.

## Recommended Request Pattern

Recommended:

- frontend -> public API
- public API validates user and project access
- public API -> internal API with `X-Project`

Not recommended:

- frontend -> internal API with user-controlled `X-Project`

If the public client can directly control the routing header, the database selection boundary becomes part of the attack surface.

## Error Policy

- missing `X-Project` -> `400 Bad Request`
- unregistered project -> `403 Forbidden`
- missing user -> `404 Not Found`

## Debug Logging

The sample application enables DEBUG logs for:

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

## Publishing The Library

### Publish to local Maven

```bash
./gradlew :header-routing-datasource-spring-boot-starter:publishToMavenLocal
```

### Publish to GitHub Packages

Set environment variables:

```bash
export GITHUB_ACTOR=your_github_id
export GITHUB_TOKEN=your_github_token
```

The token should include package publish permissions such as `write:packages`.

Publish:

```bash
./gradlew :header-routing-datasource-spring-boot-starter:publish
```

Use from another project:

```gradle
repositories {
    mavenCentral()
    maven {
        url = uri('https://maven.pkg.github.com/daewook0401/db_control_api')
        credentials {
            username = System.getenv('GITHUB_ACTOR')
            password = System.getenv('GITHUB_TOKEN')
        }
    }
}

dependencies {
    implementation 'io.headerroute:header-routing-datasource-spring-boot-starter:0.0.1'
}
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
