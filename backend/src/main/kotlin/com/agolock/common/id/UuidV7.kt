package com.agolock.common.id

import java.security.SecureRandom
import java.time.Clock
import java.util.UUID

/**
 * RFC 9562 UUIDv7 생성기입니다.
 *
 * 상위 48비트에 Unix epoch 밀리초를 넣어 시간 정렬이 가능하게 하고,
 * 나머지는 무작위 값으로 채웁니다. 같은 밀리초 안에서의 단조 증가는 보장하지 않습니다.
 */
object UuidV7 {
    private const val VERSION_7 = 0x7000L
    private const val VARIANT_RFC = Long.MIN_VALUE
    private const val TIMESTAMP_MASK = 0xFFFF_FFFF_FFFFL
    private const val RANDOM_A_MASK = 0x0FL
    private const val BYTE_MASK = 0xFFL
    private const val RANDOM_BITS_MASK = 0x3FFF_FFFF_FFFF_FFFFL
    private const val TIMESTAMP_SHIFT = 16
    private const val RANDOM_A_SHIFT = 8
    private const val RANDOM_BYTE_COUNT = 10
    private const val RANDOM_A_START = 0
    private const val RANDOM_B_START = 2
    private val random = SecureRandom()

    fun generate(clock: Clock = Clock.systemUTC()): UUID {
        val timestamp = clock.millis() and TIMESTAMP_MASK
        val randomBytes = ByteArray(RANDOM_BYTE_COUNT).also(random::nextBytes)

        val randomA =
            ((randomBytes[RANDOM_A_START].toLong() and RANDOM_A_MASK) shl RANDOM_A_SHIFT) or
                (randomBytes[RANDOM_A_START + 1].toLong() and BYTE_MASK)
        val mostSignificantBits = (timestamp shl TIMESTAMP_SHIFT) or VERSION_7 or randomA

        var leastSignificantBits = 0L
        for (index in RANDOM_B_START until randomBytes.size) {
            leastSignificantBits = (leastSignificantBits shl RANDOM_A_SHIFT) or
                (randomBytes[index].toLong() and BYTE_MASK)
        }
        leastSignificantBits = (leastSignificantBits and RANDOM_BITS_MASK) or VARIANT_RFC

        return UUID(mostSignificantBits, leastSignificantBits)
    }

    fun timestamp(uuid: UUID): Long = uuid.mostSignificantBits ushr TIMESTAMP_SHIFT
}
