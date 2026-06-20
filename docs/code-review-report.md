# SeuStock Code Review Report

## 개요
전체 코드베이스(주로 `controller`와 `service` 계층)를 스캔한 결과, 프로젝트는 전반적으로 일관된 구조(HTMX 응답 분리, DTO/Entity 변환, 소유권 검증)를 잘 따르고 있습니다. 그러나 몇 가지 반복되는 보일러플레이트 코드와 클래스 비대화(Large Class) 문제가 발견되어 향후 유지보수성과 가독성을 높이기 위한 리팩토링 포인트를 제안합니다.

## 1. 컨트롤러(Controller) 계층의 중복 코드

### 1.1 유효성 검사(Validation) 보일러플레이트
모든 `@PostMapping`, `@PutMapping` 등에서 폼 검증 실패 시 동일한 패턴이 반복되고 있습니다.

**발견 위치:** `StockController`, `SpaceController`, `ShelfController`, `ItemController` 등
**문제 패턴:**
```java
if (result.hasErrors()) {
    log.warn("request validation failed ...");
    model.addAttribute(...);
    return "fragments/modal :: modal";
}
```
**개선 제안:**
- 컨트롤러마다 뷰 반환 경로가 다르기 때문에 완전한 추상화는 어렵지만, `model.addAttribute` 등 공통으로 들어가는 컨텍스트 세팅 로직을 프라이빗 헬퍼나 인터페이스 디폴트 메서드 등으로 간소화할 수 있습니다.

### 1.2 사용자 식별(Principal) 패턴
대부분의 요청에서 `Principal`을 주입받아 `principal.getName()`으로 `username`을 구하고 있습니다.
**개선 제안:**
- Spring Security의 `@AuthenticationPrincipal`을 활용하여 커스텀 어노테이션(예: `@CurrentUser`)과 `HandlerMethodArgumentResolver`를 구현하면, 컨트롤러 파라미터에서 바로 검증된 사용자 식별자(String 혹은 User DTO)를 주입받을 수 있어 코드가 깔끔해집니다.

### 1.3 `StockController`의 패널 조회 페이징 중복 로직
**발견 위치:** `StockController.java` (`panelBySpaceAll`, `panelBySpace`, `panelByShelf`, `panelByBox`)
**문제 패턴:**
거의 동일한 형태의 페이징 조회와 모델 바인딩(`stocks`, `page`, `breadcrumb`, `isAllView`, `append` 체크 후 템플릿 반환 등)이 4번 반복됩니다.
**개선 제안:**
- 공통 파라미터를 받는 범용 메서드로 로직을 통합하거나, 기존에 있는 `buildPanelResponse`를 더 확장하여 각 엔드포인트 핸들러의 코드 라인 수를 줄일 수 있습니다.

## 2. 서비스(Service) 계층 및 설계 분석

### 2.1 `StockService`의 거대화 (God Object / Large Class)
**발견 위치:** `StockService.java` (약 770라인)
**문제점:**
- `StockService` 클래스 하나가 재고의 입/출고, 상태 변경, 위치 이동을 처리할 뿐만 아니라, **빠른 등록 시 품목(Item) 동시 생성**, **이미지 업로드 연동**, **시리얼/로트 번호 생성 규칙 적용**, **위치 계층 검증(`resolveVerifiedLocation`)**, **검색 및 페이징 조회** 등 너무 많은 책임을 쥐고 있습니다. (단일 책임 원칙(SRP) 위배)
**개선 제안:**
1. **검증 로직 분리:** `resolveVerifiedLocation` 과 같은 계층간 위치 유효성 검사 로직은 별도의 `LocationValidator` 컴포넌트로 분리.
2. **도메인 서비스 분할:** 읽기 전용 검색 서비스(`StockQueryService`)와 트랜잭션을 수반하는 커맨드 서비스(`StockCommandService`)로 분리. CQRS 패턴을 가볍게 적용해 볼 수 있습니다.
3. **복합 로직 파사드(Facade)화:** `createWithNewItem` 처럼 여러 도메인(Item, Image, Stock)이 얽힌 기능은 서비스 단의 Facade 계층을 신설하여 트랜잭션을 묶는 것이 바람직합니다.

### 2.2 페이징 응답(PageResult) 생성 로직의 반복
**발견 위치:** `StockService.java` 및 여타 Service들
**문제 패턴:**
```java
int totalCount = mapper.countQuery(...);
PageRequest pageRequest = PageRequest.of(page, totalCount);
List<DTO> list = mapper.fetchQuery(..., pageRequest.size(), pageRequest.offset());
return new PageResult<>(list, pageRequest.page(), pageRequest.size(), totalCount);
```
**개선 제안:**
- `PaginationHelper` 유틸리티 클래스나 `PageResultFactory`를 도입하여 `countQuery`와 `fetchQuery`를 `Supplier`로 받아 처리하도록 추상화하면, 저 4줄의 반복 코드를 1줄로 줄이고 비즈니스 로직에 더 집중하게 만들 수 있습니다.

## 3. 요약 및 권장 리팩토링 순서
1. **Low Hanging Fruit:** Service 계층의 페이징 처리 보일러플레이트 제거 및 Controller 인증 정보 주입 로직(ArgumentResolver) 간소화.
2. **책임 분산:** 너무 비대해진 `StockService`의 책임을 `LocationValidator`나 `StockQueryService` 로 나누어 가독성 확보.
3. **Facade 계층 도입:** 품목 생성과 재고 입고가 동시에 일어나는 복잡한 서비스 횡단 트랜잭션을 전담하는 컴포넌트 추가.
