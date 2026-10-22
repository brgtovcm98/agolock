@file:Suppress("MagicNumber")

package com.agolock.hierarchy.domain

/**
 * 공유 Grant로 부여할 수 있는 권한 단계입니다.
 *
 * DB `permission` enum과 이름이 1:1로 대응하며 `OWNER`는 저장하지 않습니다.
 * Space 소유자는 Grant가 아니라 [EffectivePermission.Owner]로 계산합니다.
 */
enum class GrantLevel(
    val rank: Int,
) {
    VIEW(1),
    EDIT(2),
    MANAGE(3),
    ;

    fun atLeast(min: GrantLevel): Boolean = rank >= min.rank

    companion object {
        fun max(
            a: GrantLevel?,
            b: GrantLevel?,
        ): GrantLevel? =
            when {
                a == null -> b
                b == null -> a
                a.rank >= b.rank -> a
                else -> b
            }
    }
}
