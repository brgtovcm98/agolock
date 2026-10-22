package com.agolock.hierarchy

import com.agolock.common.error.ApiException
import com.agolock.hierarchy.domain.AccessTarget
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.domain.EffectivePermission
import com.agolock.hierarchy.domain.GrantLevel
import com.agolock.hierarchy.domain.Permission
import com.agolock.hierarchy.domain.toPermission
import com.agolock.hierarchy.service.AccessService
import com.agolock.hierarchy.service.CursorCodec
import com.agolock.hierarchy.service.GrantLookup
import com.agolock.hierarchy.service.NoGrantLookup
import com.agolock.hierarchy.service.SpaceOwnership
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class HierarchyUnitTest {
    private val userId = UUID.randomUUID()
    private val spaceId = UUID.randomUUID()
    private val actor = Actor(userId, spaceId)

    @Test
    fun `GrantLevel은 누적 순서와 최대값을 가진다`() {
        assertThat(GrantLevel.VIEW.atLeast(GrantLevel.VIEW)).isTrue()
        assertThat(GrantLevel.EDIT.atLeast(GrantLevel.VIEW)).isTrue()
        assertThat(GrantLevel.MANAGE.atLeast(GrantLevel.EDIT)).isTrue()
        assertThat(GrantLevel.VIEW.atLeast(GrantLevel.EDIT)).isFalse()

        assertThat(GrantLevel.max(GrantLevel.VIEW, GrantLevel.EDIT)).isEqualTo(GrantLevel.EDIT)
        assertThat(GrantLevel.max(GrantLevel.MANAGE, GrantLevel.VIEW)).isEqualTo(GrantLevel.MANAGE)
        assertThat(GrantLevel.max(null, GrantLevel.EDIT)).isEqualTo(GrantLevel.EDIT)
        assertThat(GrantLevel.max(GrantLevel.VIEW, null)).isEqualTo(GrantLevel.VIEW)
        assertThat(GrantLevel.max(null, null)).isNull()
    }

    @Test
    fun `EffectivePermission은 OWNER와 Granted를 구분하고 응답 문자열을 만든다`() {
        assertThat(EffectivePermission.Owner.atLeast(GrantLevel.MANAGE)).isTrue()
        assertThat(EffectivePermission.Granted(GrantLevel.VIEW).atLeast(GrantLevel.EDIT)).isFalse()
        assertThat(EffectivePermission.None.atLeast(GrantLevel.VIEW)).isFalse()

        assertThat(EffectivePermission.Owner.wire()).isEqualTo("OWNER")
        assertThat(EffectivePermission.Granted(GrantLevel.EDIT).wire()).isEqualTo("EDIT")
        assertThat(EffectivePermission.Granted(GrantLevel.MANAGE).toPermission()).isEqualTo(Permission.MANAGE)
        assertThat(EffectivePermission.None.toPermission()).isEqualTo(Permission.NONE)
        assertThatThrownBy { EffectivePermission.None.wire() }
            .isInstanceOf(IllegalStateException::class.java)

        assertThat(EffectivePermission.None.max(EffectivePermission.Granted(GrantLevel.VIEW)))
            .isEqualTo(EffectivePermission.Granted(GrantLevel.VIEW))
        assertThat(
            EffectivePermission.Granted(GrantLevel.VIEW).max(EffectivePermission.Granted(GrantLevel.MANAGE)),
        ).isEqualTo(EffectivePermission.Granted(GrantLevel.MANAGE))
        assertThat(EffectivePermission.Granted(GrantLevel.MANAGE).max(EffectivePermission.Owner))
            .isEqualTo(EffectivePermission.Owner)
    }

    @Test
    fun `Permission 레거시 API는 OWNER를 포함한 누적 순서를 유지한다`() {
        assertThat(Permission.VIEW.atLeast(Permission.VIEW)).isTrue()
        assertThat(Permission.EDIT.atLeast(Permission.VIEW)).isTrue()
        assertThat(Permission.MANAGE.atLeast(Permission.EDIT)).isTrue()
        assertThat(Permission.OWNER.atLeast(Permission.MANAGE)).isTrue()
        assertThat(Permission.VIEW.atLeast(Permission.EDIT)).isFalse()
        assertThat(Permission.NONE.atLeast(Permission.VIEW)).isFalse()
    }

    @Test
    fun `target은 위치 ID 조합에 맞는 sealed subtype을 만든다`() {
        val ownedSpaceId = spaceId
        val ownership =
            object : SpaceOwnership {
                override fun ownerOf(spaceId: UUID): UUID? = if (spaceId == ownedSpaceId) userId else null
            }
        val accessService = AccessService(ownership, NoGrantLookup())
        val roomId = UUID.randomUUID()
        val shelfId = UUID.randomUUID()
        val boxId = UUID.randomUUID()

        assertThat(accessService.target(spaceId)).isEqualTo(AccessTarget.Space(spaceId, userId))
        assertThat(accessService.target(spaceId, roomId = roomId))
            .isEqualTo(AccessTarget.Room(spaceId, userId, roomId))
        assertThat(accessService.target(spaceId, roomId, shelfId))
            .isEqualTo(AccessTarget.Shelf(spaceId, userId, roomId, shelfId))
        assertThat(accessService.target(spaceId, roomId, shelfId, boxId))
            .isEqualTo(AccessTarget.Box(spaceId, userId, roomId, shelfId, boxId))

        assertThatThrownBy {
            accessService.target(spaceId, roomId = null, shelfId = null, boxId = boxId)
        }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { accessService.target(UUID.randomUUID()) }
            .isInstanceOf(ApiException::class.java)
            .extracting("code")
            .isEqualTo("RESOURCE_NOT_FOUND")
    }

    @Test
    fun `소유자는 OWNER이고 다른 사용자는 Grant가 없으면 NONE이다`() {
        val ownership =
            object : SpaceOwnership {
                override fun ownerOf(spaceId: UUID): UUID? = userId
            }
        val accessService = AccessService(ownership, NoGrantLookup())

        assertThat(accessService.effectivePermission(actor, spaceId)).isEqualTo(EffectivePermission.Owner)
        assertThat(accessService.effectivePermission(Actor(UUID.randomUUID(), spaceId), spaceId))
            .isEqualTo(EffectivePermission.None)
        assertThat(accessService.isShared(actor, AccessTarget.Space(spaceId, userId))).isFalse()
    }

    @Test
    fun `GrantLookup이 있으면 소유자가 아닌 사용자도 권한을 받는다`() {
        val ownership =
            object : SpaceOwnership {
                override fun ownerOf(spaceId: UUID): UUID? = UUID.randomUUID()
            }
        val grant =
            object : GrantLookup {
                override fun permissionFor(
                    actor: Actor,
                    target: AccessTarget,
                ): GrantLevel = GrantLevel.VIEW
            }
        val accessService = AccessService(ownership, grant)

        assertThat(accessService.effectivePermission(actor, AccessTarget.Space(spaceId, UUID.randomUUID())))
            .isEqualTo(EffectivePermission.Granted(GrantLevel.VIEW))
        assertThat(accessService.isShared(actor, AccessTarget.Space(spaceId, UUID.randomUUID()))).isTrue()
    }

    @Test
    fun `target 권한은 404와 403을 구분한다`() {
        val owner = UUID.randomUUID()
        val ownership =
            object : SpaceOwnership {
                override fun ownerOf(spaceId: UUID): UUID? = owner
            }
        val grant =
            object : GrantLookup {
                override fun permissionFor(
                    actor: Actor,
                    target: AccessTarget,
                ): GrantLevel? =
                    when (target) {
                        is AccessTarget.Shelf -> GrantLevel.MANAGE
                        else -> GrantLevel.VIEW
                    }
            }
        val accessService = AccessService(ownership, grant)
        val roomId = UUID.randomUUID()
        val shelfId = UUID.randomUUID()
        val roomTarget = accessService.target(spaceId, roomId = roomId)
        val shelfTarget = accessService.target(spaceId, roomId, shelfId)

        assertThat(accessService.require(actor, roomTarget, GrantLevel.VIEW))
            .isEqualTo(EffectivePermission.Granted(GrantLevel.VIEW))
        assertThat(accessService.require(actor, shelfTarget, GrantLevel.MANAGE))
            .isEqualTo(EffectivePermission.Granted(GrantLevel.MANAGE))
        assertThatThrownBy { accessService.require(actor, roomTarget, GrantLevel.MANAGE) }
            .isInstanceOf(ApiException::class.java)
            .extracting("code")
            .isEqualTo("PERMISSION_DENIED")

        val noGrantAccess = AccessService(ownership, NoGrantLookup())
        assertThatThrownBy { noGrantAccess.require(actor, roomTarget, GrantLevel.VIEW) }
            .isInstanceOf(ApiException::class.java)
            .extracting("code")
            .isEqualTo("RESOURCE_NOT_FOUND")
    }

    @Test
    fun `requireOwner는 소유자만 허용하고 Space가 없으면 404를 반환한다`() {
        val ownedSpaceId = spaceId
        val ownership =
            object : SpaceOwnership {
                override fun ownerOf(spaceId: UUID): UUID? = if (spaceId == ownedSpaceId) userId else null
            }
        val grant =
            object : GrantLookup {
                override fun permissionFor(
                    actor: Actor,
                    target: AccessTarget,
                ): GrantLevel = GrantLevel.MANAGE
            }
        val accessService = AccessService(ownership, grant)

        assertThat(accessService.requireOwner(actor, spaceId)).isEqualTo(EffectivePermission.Owner)
        assertThatThrownBy { accessService.requireOwner(Actor(UUID.randomUUID(), spaceId), spaceId) }
            .isInstanceOf(ApiException::class.java)
            .extracting("code")
            .isEqualTo("PERMISSION_DENIED")
        assertThatThrownBy { accessService.requireOwner(actor, UUID.randomUUID()) }
            .isInstanceOf(ApiException::class.java)
            .extracting("code")
            .isEqualTo("RESOURCE_NOT_FOUND")
    }

    @Test
    fun `커서는 이름과 ID를 왕복하고 잘못된 값은 400 코드로 거부한다`() {
        val id = UUID.randomUUID()
        val encoded = CursorCodec.encode("거실", id)

        assertThat(CursorCodec.decode(encoded)?.name).isEqualTo("거실")
        assertThat(CursorCodec.decode(encoded)?.id).isEqualTo(id)
        assertThatThrownBy { CursorCodec.decode("not-a-cursor") }
            .isInstanceOf(ApiException::class.java)
            .extracting("code")
            .isEqualTo("INVALID_CURSOR")
    }
}
