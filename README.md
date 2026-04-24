# DB Control API

요청 헤더 기반 `RoutingDataSource`를 공통 라이브러리로 분리한 멀티 모듈 Gradle 프로젝트입니다.
핵심은 `tenant_id` 컬럼 방식이 아니라, `X-Project` 같은 헤더 값을 기준으로 프로젝트별 물리 DB를 라우팅하는 것입니다.
Java 21, Spring Boot 3.x 기준으로 구성했습니다.

## What It Solves

같은 internal API와 같은 JPA Repository 코드를 유지하면서도, 요청마다 다른 물리 DB를 선택하고 싶을 때 사용합니다.

예시:

- `X-Project: tad` -> `tad_db`
- `X-Project: fa` -> `fa_db`
- `X-Project: discord` -> `discord_db`

이 방식의 목적은 서비스 코드에서 프로젝트별 `if/else` 분기를 없애고, 인프라 레이어에서 DB 선택을 끝내는 것입니다.

## Modules

- `header-routing-datasource-spring-boot-starter`
  - 재사용 가능한 공통 starter 라이브러리
  - `ThreadLocal` 기반 `ProjectContext`
  - 헤더 검증용 `ProjectContextFilter`
  - `AbstractRoutingDataSource` 기반 `ProjectRoutingDataSource`
  - `application.yml` 바인딩용 `ProjectRoutingProperties`
- `example-internal-api`
  - starter를 사용하는 예제 앱
  - `auth.tb_user` 엔티티, JPA Repository, Service, Controller 포함

## How It Works

1. 요청이 들어오면 `ProjectContextFilter`가 `X-Project` 헤더를 읽습니다.
2. 헤더 값이 비어 있으면 400, 등록되지 않은 값이면 403 예외를 발생시킵니다.
3. 정상 값이면 `ProjectContext(ThreadLocal)`에 현재 project key를 저장합니다.
4. JPA가 DB 커넥션이 필요할 때 `ProjectRoutingDataSource`가 `ProjectContext.get()` 값을 읽습니다.
5. 등록된 project key에 매핑된 실제 `HikariDataSource`로 라우팅합니다.
6. 요청이 끝나면 `finally`에서 `ProjectContext.clear()`를 호출해 스레드 오염을 막습니다.

즉, 업무 코드에서는 프로젝트 분기를 몰라도 되고 `Repository`는 평소처럼 그대로 사용하면 됩니다.

## Recommended API Structure

이 라이브러리는 내부망 API 구조에서 가장 안전하고 자연스럽습니다.

권장 구조:

```text
Frontend
  -> Public API / BFF / External API server
      -> Internal API server
          -> Header-routing datasource
              -> Project-specific physical DB
```

예시:

```text
Frontend
  -> 125 external-api
      -> 105 internal-api
          -> tad_db / fa_db / discord_db
```

권장 흐름:

1. 프론트는 일반 인증 토큰과 비즈니스 요청만 보냅니다.
2. 외부 API 서버가 사용자 권한과 프로젝트 접근 가능 여부를 검증합니다.
3. 외부 API 서버가 내부 호출을 만들 때만 `X-Project`를 생성합니다.
4. internal-api는 이 헤더를 보고 물리 DB를 선택합니다.

핵심 원칙:

- `X-Project`는 외부 사용자가 직접 제어하는 입력이 아니라, 내부 서버가 검증 후 생성한 값이어야 합니다.
- 이 라이브러리는 public API보다 internal API에서 쓰는 것이 더 적합합니다.

## When This Is Safe

다음 조건이면 비교적 안전하게 쓸 수 있습니다.

- internal-api가 내부망 또는 private network 안에서만 열려 있음
- internal-api에 외부가 직접 접근할 수 없음
- `X-Project` 헤더를 신뢰된 내부 서버만 생성함
- 외부 API 서버가 사용자와 프로젝트 권한을 먼저 검증함

## Risk Factors

다음 경우에는 위험해질 수 있습니다.

- 프론트가 `X-Project` 헤더를 직접 보내는 구조
- public API가 헤더 값만 믿고 DB를 선택하는 구조
- 사용자 권한 검증 없이 프로젝트 key만으로 DB 라우팅하는 구조
- `ThreadLocal` 기반인데 `@Async`, 별도 스레드풀, 메시지 소비 등으로 컨텍스트 전파가 필요한 구조

정리하면:

- 헤더는 DB 라우팅 힌트로는 좋음
- 헤더 자체가 권한의 근거가 되면 위험함

## Quick Start

### 1. 의존성 추가

로컬 Maven 저장소를 쓸 경우:

```gradle
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'io.headerroute:header-routing-datasource-spring-boot-starter:1.0.0'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    runtimeOnly 'org.postgresql:postgresql'
}
```

같은 저장소 내부에서는:

```gradle
dependencies {
    implementation project(':header-routing-datasource-spring-boot-starter')
}
```

### 2. `application.yml` 설정

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

새 프로젝트를 붙일 때는 코드 수정 없이 `projects` 아래 설정만 추가하면 됩니다.

### 3. JPA는 평소처럼 사용

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

Controller, Service, Repository 어디에도 프로젝트 분기 코드를 넣지 않습니다.

### 4. 요청 예시

```bash
curl -H "X-Project: tad" http://localhost:8081/internal/auth/users/1
curl -H "X-Project: fa" http://localhost:8081/internal/auth/users/1
curl -H "X-Project: discord" "http://localhost:8081/internal/auth/users/by-email?email=user@example.com"
```

같은 API라도 헤더 값에 따라 다른 물리 DB로 라우팅됩니다.

## Error Policy

- `X-Project` 누락 -> `400 Bad Request`
- 등록되지 않은 프로젝트 -> `403 Forbidden`
- 사용자 없음 -> `404 Not Found`

## Debug Logging

예제 앱에서는 아래 패키지에 대해 DEBUG 로그를 켜 두었습니다.

```yaml
logging:
  level:
    io.headerroute.routing.datasource: DEBUG
```

로그 예시:

```text
Resolved project header. uri=/internal/auth/users/1, header=X-Project, project=tad
Routing datasource lookup for project=tad
Clearing project context. uri=/internal/auth/users/1, project=tad
```

## Library Publishing

### 로컬 Maven 저장소로 publish

```bash
./gradlew :header-routing-datasource-spring-boot-starter:publishToMavenLocal
```

### GitHub Packages로 publish

환경변수:

```bash
export GITHUB_ACTOR=your_github_id
export GITHUB_TOKEN=your_github_token
```

배포:

```bash
./gradlew :header-routing-datasource-spring-boot-starter:publish
```

다른 프로젝트:

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
    implementation 'io.headerroute:header-routing-datasource-spring-boot-starter:1.0.0'
}
```

## Build And Run

빌드:

```bash
./gradlew build
```

예제 앱 실행:

```bash
./gradlew :example-internal-api:bootRun
```
