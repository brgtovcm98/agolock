package com.agolock.hierarchy.domain

/**
 * ACL 판정 결과입니다.
 *
 * 소유권([Owner])과 공유 Grant([Granted])를 계산 결과에서 분리해,
 * `VIEW < EDIT < MANAGE` 비교와 응답 문자열 변환을 한 곳에서 수행합니다.
 */
sealed interface EffectivePermission {
    data object None : EffectivePermission

    data object Owner : EffectivePermission

    data class Granted(
        val level: GrantLevel,
    ) : EffectivePermission

    fun atLeast(min: GrantLevel): Boolean =
        when (this) {
            Owner -> true
            is Granted -> level.atLeast(min)
            None -> false
        }

    fun max(other: EffectivePermission): EffectivePermission =
        when (this) {
            Owner -> Owner
            None -> other
            is Granted ->
                when (other) {
                    Owner -> Owner
                    None -> this
                    is Granted -> Granted(GrantLevel.max(level, other.level) ?: level)
                }
        }

    /**
     * API 응답에 직렬화할 문자열입니다.
     * `None`은 응답에 노출하지 않는다는 불변식에 따라 호출 시 실패합니다.
     */
    fun wire(): String =
        when (this) {
            None -> error("NONE은 API 응답에 노출하지 않습니다.")
            Owner -> "OWNER"
            is Granted -> level.name
        }
}
