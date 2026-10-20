# ADR-005: RabbitMQ와 Transactional Outbox

- 상태: 승인됨
- 날짜: 2026-10-19

## 맥락

이미지 후처리, AI 인식, 임베딩은 외부 API와 긴 처리 시간에 의존합니다. DB 트랜잭션과 메시지 발행을 단순히 나누면 이중 쓰기 문제가 발생합니다. 비동기 처리와 재시도, DLQ, 멱등성이 필요합니다.

## 결정

- RabbitMQ를 비동기 작업 큐로 사용합니다.
- `outbox_event`를 DB 변경과 같은 트랜잭션에 저장합니다.
- Outbox 릴레이가 이벤트를 RabbitMQ로 발행합니다.
- 큐는 `image.process`, `ai.recognition`, `ai.embedding`, `notification`으로 나눕니다.
- 각 큐에 DLQ를 두고 지수 백오프로 재시도합니다.
- 소비자는 `eventId` 또는 `jobId`로 멱등하게 작성합니다.
- AI job 상태는 `ai_job` 테이블에서 관리합니다.

## 결과

- DB 커밋과 메시지 발행의 이중 쓰기 문제를 줄입니다.
- 장애 시 재시도와 수동 재처리가 가능합니다.
- 큐 적체, DLQ, 소비자 상태를 관측해야 합니다.
- at-least-once 전달을 전제로 멱등성을 보장해야 합니다.

## 대안

- Spring `@Async`: 유실·재시도·확장에 약합니다.
- Redis Streams: 컴포넌트는 줄지만 DLQ·재시도 구현이 직접 필요합니다.
- Kafka: 현재 규모에 과도합니다.
- DB 폴링만 사용: 구현은 단순하지만 처리 지연과 DB 부하가 커질 수 있습니다.

## 링크

- [미디어·AI](../media-ai.md)
- [아키텍처](../architecture.md)
- [운영](../operations.md)
