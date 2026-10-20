package com.agolock.common.id

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class UuidV7Test {
    private val fixedClock =
        Clock.fixed(Instant.ofEpochMilli(1_700_000_000_123L), ZoneOffset.UTC)

    @Test
    fun `UUIDv7 형식과 타임스탬프를 가진다`() {
        val uuid = UuidV7.generate(fixedClock)

        assertThat(uuid.version()).isEqualTo(7)
        assertThat(uuid.variant()).isEqualTo(2)
        assertThat(UuidV7.timestamp(uuid)).isEqualTo(1_700_000_000_123L)
        assertThat(uuid.toString()).matches("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    }

    @Test
    fun `시간이 다르면 타임스탬프 순서가 유지된다`() {
        val earlier = UuidV7.generate(Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC))
        val later = UuidV7.generate(Clock.fixed(Instant.ofEpochMilli(1_700_000_000_001L), ZoneOffset.UTC))

        assertThat(UuidV7.timestamp(earlier)).isLessThan(UuidV7.timestamp(later))
    }

    @Test
    fun `같은 시각에도 충돌하지 않는다`() {
        val values = (1..1_000).map { UuidV7.generate(fixedClock) }

        assertThat(values.toSet()).hasSize(1_000)
    }

    @Test
    fun `타임스탬프는 부호와 무관하게 추출된다`() {
        val uuid = UUID(-1L, Long.MIN_VALUE)

        assertThat(UuidV7.timestamp(uuid)).isEqualTo(0xFFFF_FFFF_FFFFL)
    }
}
