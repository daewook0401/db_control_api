# DB Control API

이 프로젝트는 요청 헤더 기반 `RoutingDataSource`를 재사용 가능한 Spring Boot starter 라이브러리로 제공합니다.
같은 JPA 코드를 유지하면서도, 프로젝트별로 서로 다른 물리 DB를 선택해야 하는 internal API 용도에 맞춘 구조입니다.

핵심은 `tenant_id` 컬럼 필터링이 아니라 `X-Project` 같은 헤더를 기준으로 물리 DB를 선택하는 것입니다.

이 저장소는 Java 21, Spring Boot 3.x 기준으로 구성했습니다.
영문 문서는 [README.md](./README.md) 에 있습니다.

## Maven Central 좌표

```text
groupId    = io.github.daewook0401.headerroute
artifactId = header-routing-datasource-spring-boot-starter
version    = 0.1.0
```

## 무엇을 해결하나

다음과 같은 상황에서 사용합니다.

- 하나의 internal API가 여러 프로젝트를 처리해야 할 때
- 각 프로젝트가 서로 다른 물리 DB를 가질 때
- `Controller`, `Service`, `Repository`에 프로젝트별 분기 코드를 넣고 싶지 않을 때

예시:

- `X-Project: tad` -> `tad_db`
- `X-Project: fa` -> `fa_db`
- `X-Project: discord` -> `discord_db`

## 모듈 구성

- `header-routing-datasource-spring-boot-starter`
  - 재사용 가능한 starter 라이브러리
  - `ThreadLocal` 기반 `ProjectContext`
  - 헤더 검증용 `ProjectContextFilter`
  - `AbstractRoutingDataSource` 기반 `ProjectRoutingDataSource`
  - `application.yml` 바인딩용 `ProjectRoutingProperties`
- `example-internal-api`
  - starter를 사용하는 샘플 애플리케이션

## 동작 방식

1. `ProjectContextFilter`가 요청마다 `X-Project` 헤더를 읽습니다.
2. 헤더가 없으면 `400 Bad Request`를 반환합니다.
3. 등록되지 않은 값이면 `403 Forbidden`를 반환합니다.
4. 정상 값이면 `ProjectContext`에 project key를 `ThreadLocal`로 저장합니다.
5. `ProjectRoutingDataSource`가 현재 key를 읽어 맞는 `HikariDataSource`를 선택합니다.
6. 요청이 끝나면 `ProjectContext.clear()`를 `finally`에서 호출합니다.

즉, 업무 코드에서는 프로젝트별 분기를 몰라도 되고 JPA repository를 그대로 쓸 수 있습니다.

## 추천 API 구조

권장 구조:

```text
Frontend
  -> Public API / BFF / External API
      -> Internal API
          -> Header-routing datasource
              -> Project-specific physical DB
```

권장 흐름:

1. 프론트는 사용자 요청만 보냅니다.
2. 외부 API가 사용자와 프로젝트 접근 권한을 검증합니다.
3. 신뢰된 내부 호출에서만 `X-Project`를 생성합니다.
4. internal API가 그 헤더를 보고 물리 DB를 선택합니다.

이 라이브러리는 public API보다 internal API에 더 적합합니다.

## 위험 요소

다음과 같은 경우 위험할 수 있습니다.

- 프론트가 `X-Project`를 직접 보내는 구조
- public API가 헤더 값만 믿고 바로 DB를 선택하는 구조
- 프로젝트 key를 라우팅 정보가 아니라 권한 정보처럼 사용하는 구조
- 이후 `@Async`, 별도 스레드풀, 메시지 소비 등으로 스레드가 바뀌는 구조

## Quick Start

### 1. 의존성 추가

Maven Central 공개 후:

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
```

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

### 4. 요청 예시

```bash
curl -H "X-Project: tad" http://localhost:8081/internal/auth/users/1
curl -H "X-Project: fa" http://localhost:8081/internal/auth/users/1
```

같은 Controller와 Repository가 요청 헤더에 따라 다른 물리 DB를 사용하게 됩니다.

## 사용 예시

최소 소비 프로젝트 `build.gradle`:

```gradle
dependencies {
    implementation 'io.github.daewook0401.headerroute:header-routing-datasource-spring-boot-starter:0.1.0'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    runtimeOnly 'org.postgresql:postgresql'
}
```

최소 요청 패턴:

- frontend -> public API
- public API가 사용자와 프로젝트 권한 검증
- public API -> internal API 호출 시 `X-Project` 생성

비권장:

- frontend -> internal API 직접 호출
- 사용자가 `X-Project`를 직접 제어

## 에러 정책

- `X-Project` 누락 -> `400 Bad Request`
- 등록되지 않은 프로젝트 -> `403 Forbidden`
- 사용자 없음 -> `404 Not Found`

## 디버그 로그

```yaml
logging:
  level:
    io.headerroute.routing.datasource: DEBUG
```

## Maven Central 배포

이 프로젝트는 이제 Sonatype Central Portal 배포 준비를 다음 기준으로 해두었습니다.

- Gradle `maven-publish`
- Gradle `signing`
- Sonatype OSSRH staging 호환 엔드포인트

첫 Central 배포 전에 필요한 것:

- Sonatype Central에서 검증된 namespace
  권장: `io.github.daewook0401`
- Sonatype Central Portal user token
- 서명용 PGP private key와 passphrase

이 값들은 `~/.gradle/gradle.properties`에 두는 것을 권장합니다.
예시는 [gradle.properties.example](./gradle.properties.example) 파일에 넣어두었습니다.

예시:

```properties
centralPortalUsername=your_central_portal_token_username
centralPortalPassword=your_central_portal_token_password
centralNamespace=io.github.daewook0401
centralPublishingType=automatic
signingKey=your_ascii_armored_private_key
signingPassword=your_signing_passphrase
```

배포 명령:

```bash
./gradlew :header-routing-datasource-spring-boot-starter:publishReleaseToMavenCentral
```

`centralPublishingType=automatic`이면 검증 통과 후 자동 공개를 시도합니다.
`centralPublishingType=user_managed`이면 업로드 후 Central Portal UI에서 마지막 publish를 눌러야 합니다.

현재 staging 저장소 조회:

```bash
./gradlew :header-routing-datasource-spring-boot-starter:listCentralStagingRepositories
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

## 라이선스

이 프로젝트는 MIT License를 따릅니다.
[LICENSE](./LICENSE) 파일을 참고하세요.
