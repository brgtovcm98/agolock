package com.agolock.messaging.outbox

/**
 * Outbox 이벤트 타입별 발행 처리기입니다.
 *
 * 새 이벤트 타입은 이 인터페이스 구현을 추가하는 것으로 확장하고,
 * relay의 if/else 분기를 늘리지 않습니다.
 */
interface OutboxEventHandler {
    fun supports(eventType: String): Boolean

    fun handle(event: ClaimedOutboxEvent)
}
