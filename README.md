# SeuStock

SeuStock은 **Team Ugui**가 개발하는 개인 또는 소규모 팀용 재고 관리 애플리케이션입니다. 공간, 선반, 박스 단위로 물품과 재고를 정밀하게 추적할 수 있도록 돕습니다. Spring Boot, Thymeleaf, HTMX 기반의 모던한 SSR 아키텍처를 채택하고 있습니다.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## 프로젝트 사양

| 구분 | 내용 |
| --- | --- |
| Language | Java 25 |
| Framework | Spring Boot 4.0.6 |
| View | Thymeleaf, HTMX 2, Tailwind CSS CDN |
| Persistence | MyBatis, Flyway |
| Database | PostgreSQL, H2(Test) |
| Session | Spring Session Redis |
| Image Storage | MinIO, local file fallback implementation |
| AI | Spring AI Ollama, Gemma, YOLO HTTP service 연동 |
| QR | ZXing 기반 QR 코드 생성 |
| Build | Gradle Wrapper |
| Test | JUnit 5, Spring Boot Test, MyBatis Test |

## 주요 기능

### 사용자

- 회원가입, 로그인, 로그아웃
- 아이디 중복 확인
- Spring Security 기반 인증
- BCrypt 비밀번호 해시 저장
- Redis 기반 세션 저장
- 다국어 지원 (한국어, 영어, 몽골어, 일본어, 중국어) 및 쿠키 기반 언어 설정 유지

### 공간, 선반, 박스

- 사용자별 보관 공간 생성, 조회, 수정, 삭제
- 공간 하위 선반 생성, 이름 수정, 삭제
- 선반 하위 박스 생성, 이름 수정, 삭제
- 공간 상세 화면에서 선반/박스 트리와 재고 패널 확인
- HTMX 기반 모달과 부분 갱신 UI
- 선반/박스 QR 코드 생성 및 QR URL 진입 처리

### 품목

- 품목 생성, 조회, 수정, 삭제
- 품목명, 설명, 대표 이미지 관리
- 품목별 위치별 재고 현황 조회
- 품목별 입출고 이력 조회
- 검색, 정렬, 페이지네이션 지원

### 재고

- 공간, 선반, 박스 위치 기준 재고 조회
- 기존 품목을 선택해 재고 등록
- 새 품목과 재고를 한 번에 등록하는 빠른 재고 등록
- 재고 수량 단위 입고/출고 처리
- 재고 위치 이동
- 재고별 시리얼 번호, 로트 번호, 유통기한, 메모 수정
- 개별 재고 row 단위 수정 및 삭제
- 검색, 정렬, 페이지네이션 지원
- 재고 상태 관리: `IN_STOCK`, `DISPATCHED`, `LOST`, `DAMAGED`, `DISPOSED`
- 재고 트랜잭션 기록: `IN`, `OUT`, `MOVE`, `ADJUST`

### 이미지 및 AI 분석

- 이미지 파일 업로드 및 사용자별 이미지 조회
- 이미지 중복 관리를 위한 content hash 저장
- 운영 MinIO 기반 이미지 저장, local/test 파일 저장 fallback
- Spring AI Ollama/Gemma 기반 이미지 분석
- 저장용 원본 이미지와 AI 분석용 최적화 이미지 분리
- 클라이언트 측 분석용 이미지 리사이징 및 WebP 변환 fallback
- YOLO HTTP 전처리 결과를 Gemma 프롬프트의 보조 단서로 사용
- YOLO 비활성화 또는 호출 실패 시 Gemma 단독 분석으로 fallback
- Gemma/Ollama 호출 실패 시 AI 서비스 사용 불가 메시지 반환

### QR 코드

- 선반/박스 QR 코드 모달 제공
- QR 이미지 생성 API 제공
- QR 진입 시 대상 위치의 재고 화면으로 연결
- `app.qr-base-url` 설정으로 QR에 포함할 기준 URL 분리

## 프로젝트 구조

```text
src/main/java/com/seu/seustock
├── configuration   # MVC, Security, MinIO, Redis serializer, 예외 처리, 타입 핸들러
├── controller      # 화면/API 요청 처리
├── mapper          # MyBatis Mapper 인터페이스
├── model
│   ├── dto         # 화면과 서비스에서 사용하는 DTO
│   ├── enumeration # 재고 상태와 트랜잭션 타입
│   ├── form        # 요청 폼 및 검증 모델
│   └── pagination  # 페이지 요청/응답 모델
└── service         # 비즈니스 로직
    └── ai          # YOLO/Gemma 이미지 분석 파이프라인

src/main/resources
├── db/migration    # Flyway 마이그레이션
├── mapper          # MyBatis XML 매핑
├── static          # JavaScript와 샘플 이미지
└── templates       # Thymeleaf 페이지와 fragments

src/test
├── java            # JUnit, Mapper, Service, Controller 테스트
└── resources       # H2 테스트 설정과 테스트 스키마
```

## 데이터 모델 요약

- `users`: 사용자 계정
- `spaces`: 사용자별 보관 공간
- `shelves`: 공간 하위 선반
- `boxes`: 선반 하위 박스
- `items`: 품목 마스터 정보
- `stocks`: 물리적 재고 단위
- `stock_transactions`: 재고 입고, 출고, 이동, 조정 이력
- `images`: 업로드 이미지 메타데이터
- `item_images`: 품목과 이미지 연결
- `stock_images`: 재고와 이미지 연결

## 실행 방법

### 1. 인프라 실행

```bash
docker compose up -d
```

개발용 Docker Compose는 다음 서비스를 실행합니다.

| 서비스 | 주소 |
| --- | --- |
| PostgreSQL | `localhost:5433` |
| MinIO API | `http://localhost:9000` |
| MinIO Console | `http://localhost:9001` |
| Redis | `localhost:6379` |

### 2. 애플리케이션 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

기본 접속 주소는 `http://localhost:8080`입니다. `local` 프로필은 로컬 PostgreSQL, Ollama, YOLO 서비스 주소를 설정합니다. Redis와 MinIO는 local 프로필에서 선택 사항이며, 기본값은 내장 세션과 local file image storage입니다.

### 3. 테스트 실행

```bash
./gradlew test
```

테스트는 H2와 `src/test/resources/schema-test.sql`을 사용하며, Flyway는 테스트 프로필에서 비활성화됩니다.

## 주요 설정

공통 설정은 `src/main/resources/application.properties`, 로컬 개발 설정은 `src/main/resources/application-local.properties`, 운영 설정은 `src/main/resources/application-prod.properties`에 있습니다.

```properties
server.port=8080
app.base-url=http://localhost:8080
app.qr-base-url=${app.base-url}

spring.datasource.url=jdbc:postgresql://localhost:5433/seustockdb
spring.datasource.username=seustockuser
spring.datasource.password=seustockpass

spring.session.store-type=none
spring.data.redis.host=localhost
spring.data.redis.port=6379

seustock.image-storage.type=local
seustock.upload-dir=uploads/images

seustock.minio.endpoint=http://localhost:9000
seustock.minio.access-key=seustockminio
seustock.minio.secret-key=seustockminiopass
seustock.minio.bucket=seustock-images

spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.model=gemma3:4b
seustock.ai.yolo.enabled=true
seustock.ai.yolo.base-url=http://localhost:8000
```

운영 환경은 `prod` 프로필을 활성화하고, 비밀번호와 외부 서비스 주소는 환경변수로 주입합니다.

```bash
SPRING_PROFILES_ACTIVE=prod java -jar build/libs/SeuStock-*.jar
```

필수 운영 환경변수는 `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `APP_BASE_URL`, `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `REDIS_HOST`, `OLLAMA_BASE_URL`입니다. `APP_QR_BASE_URL`, `MINIO_BUCKET`, `REDIS_PORT`, `REDIS_PASSWORD`, `YOLO_ENABLED`, `YOLO_BASE_URL`은 필요할 때만 지정하면 됩니다.

AI 이미지 분석을 사용하려면 로컬 Ollama 서버와 설정된 Gemma 모델이 준비되어 있어야 합니다. YOLO 전처리를 사용하려면 `/detect` 엔드포인트를 제공하는 로컬 YOLO HTTP 서비스를 실행해야 합니다.

## 외부 의존성 fallback 정책

| 대상 | local/test | prod |
| --- | --- | --- |
| Redis | 선택 사항. 기본은 `spring.session.store-type=none` | 필수. Redis 기반 Spring Session 사용 |
| MinIO | 선택 사항. 기본은 `seustock.image-storage.type=local` | 필수. MinIO 또는 S3 호환 오브젝트 스토리지 사용 |
| YOLO | 선택 사항. 실패 시 Gemma 단독 분석 | 선택 사항. 실패 시 Gemma 단독 분석 |
| Gemma/Ollama | AI 분석 사용 시 필요 | AI 분석 사용 시 필요 |

YOLO는 보조 전처리 서비스입니다. 비활성화되어 있거나 호출에 실패하면 탐지 결과 없이 이미지를 Gemma/Ollama에 바로 전송합니다. Gemma/Ollama가 응답하지 않으면 분석 결과를 임의로 만들지 않고 `현재 AI 서비스를 사용할 수 없습니다. 잠시 후 다시 시도해주세요.` 메시지를 반환합니다.

운영 환경에서는 Redis와 MinIO를 자동 local fallback하지 않습니다. Redis fallback은 다중 인스턴스 세션 일관성을 깨뜨릴 수 있고, MinIO fallback은 인스턴스별 이미지 파일 불일치와 유실 위험이 있기 때문입니다.

## License

Copyright © 2026 **Team Ugui**. This project is licensed under the [MIT License](LICENSE).

## 개발 참고

- Gradle은 시스템 설치본이 아니라 `./gradlew`를 사용합니다.
- Java toolchain은 25로 설정되어 있습니다.
- 운영 DB 스키마는 `src/main/resources/db/migration`의 Flyway 마이그레이션으로 관리합니다.
- Persistence 변경 시 `src/test/resources/schema-test.sql`과 관련 Mapper 테스트를 함께 갱신합니다.
- QR을 출력한 뒤에는 `app.qr-base-url` 값을 변경하지 않는 것을 원칙으로 합니다. 서버 주소가 바뀌면 기존 QR 기준 URL에서 새 주소로 리다이렉트하도록 구성합니다.
- 이미지 저장의 운영 기본 구현은 `MinioImageStorageService`입니다. local/test 기본값은 `LocalImageStorageService`입니다.
