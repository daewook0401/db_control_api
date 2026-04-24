# DB Control API

이 프로젝트는 요청 헤더 기반 `RoutingDataSource`를 재사용 가능한 Spring Boot starter 라이브러리로 제공합니다.
같은 internal API와 같은 JPA repository 코드를 유지하면서도, 요청마다 다른 물리 DB를 선택해야 할 때를 위한 구조입니다.

핵심은 `tenant_id` 컬럼 필터링이 아니라, `X-Project` 같은 헤더를 기준으로 물리 DB를 선택하는 것입니다.

이 저장소는 Java 21, Spring Boot 3.x 기준으로 구성했습니다.

영문 문서는 [README.md](./README.md) 에 있습니다.

## 무엇을 해결하나

다음과 같은 상황에서 사용합니다.

- 하나의 internal API가 여러 프로젝트를 처리해야 할 때
- 각 프로젝트가 서로 다른 물리 DB를 가질 때
- `Controller`, `Service`, `Repository`에 프로젝트별 분기 코드를 넣고 싶지 않을 때

예시:

- `X-Project: tad` -> `tad_db`
- `X-Project: fa` -> `fa_db`
- `X-Project: discord` -> `discord_db`

목표는 서비스 코드 곳곳의 `if/else`를 없애고, DB 선택을 인프라 레이어에서 끝내는 것입니다.

## 모듈 구성

- `header-routing-datasource-spring-boot-starter`
  - 재사용 가능한 starter 라이브러리
  - `ThreadLocal` 기반 `ProjectContext`
  - 헤더 검증용 `ProjectContextFilter`
  - `AbstractRoutingDataSource` 기반 `ProjectRoutingDataSource`
  - `application.yml` 바인딩용 `ProjectRoutingProperties`
- `example-internal-api`
  - starter를 사용하는 샘플 애플리케이션
  - `auth.tb_user` 엔티티, JPA repository, service, controller 포함

## 동작 방식

1. 요청이 들어오면 `ProjectContextFilter`가 `X-Project` 헤더를 읽습니다.
2. 헤더가 없으면 `400 Bad Request`를 반환합니다.
3. 등록되지 않은 값이면 `403 Forbidden`를 반환합니다.
4. 정상 값이면 `ProjectContext`에 현재 project key를 `ThreadLocal`로 저장합니다.
5. JPA가 커넥션을 사용할 때 `ProjectRoutingDataSource`가 `ProjectContext.get()` 값을 읽습니다.
6. 해당 project key에 매핑된 `HikariDataSource`가 선택됩니다.
7. 요청이 끝나면 `finally`에서 `ProjectContext.clear()`를 호출합니다.

즉, 업무 코드에서는 프로젝트별 분기를 알 필요가 없고, JPA repository는 기존처럼 그대로 사용할 수 있습니다.

## 추천 API 구조

이 라이브러리는 내부망 API 구조에서 가장 자연스럽고 안전합니다.

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

1. 프론트는 일반 인증 정보와 비즈니스 요청만 보냅니다.
2. 외부 API 서버가 사용자 권한과 프로젝트 접근 가능 여부를 검증합니다.
3. 신뢰된 내부 서버가 내부 호출을 만들 때만 `X-Project`를 생성합니다.
4. internal-api는 그 헤더를 보고 물리 DB를 선택합니다.

핵심 원칙:

- `X-Project`는 외부 클라이언트가 직접 제어하는 값이 아니라, 내부 서버가 검증 후 생성한 값이어야 합니다.
- 이 라이브러리는 public API보다 internal API에 더 적합합니다.

## 안전하게 사용할 수 있는 조건

다음 조건이라면 비교적 안전하게 사용할 수 있습니다.

- internal-api가 private network 또는 내부망 안에서만 열려 있음
- 외부 사용자가 internal-api에 직접 접근할 수 없음
- 신뢰된 내부 서버만 `X-Project` 헤더를 생성함
- 외부 API 서버가 내부 호출 전에 프로젝트 권한을 먼저 검증함

## 위험 요소

다음과 같은 구조는 위험할 수 있습니다.

- 프론트가 `X-Project` 헤더를 직접 보내는 구조
- public API가 헤더 값만 믿고 바로 DB를 선택하는 구조
- 사용자 권한 검증 없이 프로젝트 key만으로 DB를 라우팅하는 구조
- `ThreadLocal` 기반인데 이후 `@Async`, 별도 스레드풀, 메시지 소비 등으로 스레드가 바뀌는 구조

정리하면:

- 헤더는 DB 라우팅 힌트로는 적합함
- 헤더 자체가 권한의 근거가 되면 위험함

## Quick Start

### 1. 의존성 추가

로컬 Maven 저장소를 사용할 경우:

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

같은 멀티 모듈 저장소 안에서는:

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

새 프로젝트를 추가할 때는 코드 수정 없이 `projects` 아래에 설정만 추가하면 됩니다.

### 3. JPA는 그대로 사용

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

Controller, Service, Repository 어디에도 프로젝트별 분기 코드를 넣지 않습니다.

### 4. 요청 예시

```bash
curl -H "X-Project: tad" http://localhost:8081/internal/auth/users/1
curl -H "X-Project: fa" http://localhost:8081/internal/auth/users/1
curl -H "X-Project: discord" "http://localhost:8081/internal/auth/users/by-email?email=user@example.com"
```

같은 API라도 헤더 값에 따라 서로 다른 물리 DB로 라우팅됩니다.

## 에러 정책

- `X-Project` 누락 -> `400 Bad Request`
- 등록되지 않은 프로젝트 -> `403 Forbidden`
- 사용자 없음 -> `404 Not Found`

## 디버그 로그

샘플 앱에서는 아래 패키지에 대해 DEBUG 로그를 켜 두었습니다.

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

## 라이브러리 배포

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

토큰에는 `write:packages` 같은 패키지 배포 권한이 포함되어 있어야 합니다.

배포:

```bash
./gradlew :header-routing-datasource-spring-boot-starter:publish
```

다른 프로젝트에서 사용:

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

## 빌드 및 실행

빌드:

```bash
./gradlew build
```

샘플 앱 실행:

```bash
./gradlew :example-internal-api:bootRun
```
