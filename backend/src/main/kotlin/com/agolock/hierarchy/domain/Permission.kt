package com.agolock.hierarchy.domain

/**
 * API/레거시 호환용 권한 enum입니다.
 *
 * 내부 ACL 판정은 [GrantLevel]과 [EffectivePermission]을 사용하고,
 * 이 enum은 기존 API 계약과 DTO 매핑에만 사용합니다.
 */
enum class Permission {
    NONE,
    VIEW,
    EDIT,
    MANAGE,
    OWNER,
    ;

    fun atLeast(required: Permission): Boolean =
        when (this) {
            OWNER -> true
            MANAGE -> required != OWNER
            EDIT -> required == NONE || required == VIEW || required == EDIT
            VIEW -> required == NONE || required == VIEW
            NONE -> required == NONE
        }
}

fun EffectivePermission.toPermission(): Permission =
    when (this) {
        EffectivePermission.None -> Permission.NONE
        EffectivePermission.Owner -> Permission.OWNER
        is EffectivePermission.Granted -> level.toPermission()
    }

fun GrantLevel.toPermission(): Permission =
    when (this) {
        GrantLevel.VIEW -> Permission.VIEW
        GrantLevel.EDIT -> Permission.EDIT
        GrantLevel.MANAGE -> Permission.MANAGE
    }

/**
 * API DTO의 [Permission]을 저장 가능한 Grant 단계로 변환합니다.
 * `OWNER`와 `NONE`은 Grant로 저장할 수 없으므로 null을 반환합니다.
 */
fun Permission.toGrantLevelOrNull(): GrantLevel? =
    when (this) {
        Permission.VIEW -> GrantLevel.VIEW
        Permission.EDIT -> GrantLevel.EDIT
        Permission.MANAGE -> GrantLevel.MANAGE
        Permission.NONE, Permission.OWNER -> null
    }
