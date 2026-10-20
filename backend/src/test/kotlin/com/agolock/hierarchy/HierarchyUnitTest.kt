package com.agolock.hierarchy

import com.agolock.common.error.ApiException
import com.agolock.hierarchy.domain.AccessTarget
import com.agolock.hierarchy.domain.Actor
import com.agolock.hierarchy.domain.Permission
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
    fun `Permission은 누적 순서를 가진다`() {
        assertThat(Permission.VIEW.atLeast(Permission.VIEW)).isTrue()
        assertThat(Permission.EDIT.atLeast(Permission.VIEW)).isTrue()
        assertThat(Permission.MANAGE.atLeast(Permission.EDIT)).isTrue()
        assertThat(Permission.OWNER.atLeast(Permission.MANAGE)).isTrue()
        assertThat(Permission.VIEW.atLeast(Permission.EDIT)).isFalse()
        assertThat(Permission.NONE.atLeast(Permission.VIEW)).isFalse()
    }

    @Test
    fun `소유자는 OWNER이고 다른 사용자는 Grant가 없으면 NONE이다`() {
        val ownership =
            object : SpaceOwnership {
                override fun ownerOf(spaceId: UUID): UUID? = userId
            }
        val accessService = AccessService(ownership, NoGrantLookup())

        assertThat(accessService.effectivePermission(actor, spaceId)).isEqualTo(Permission.OWNER)
        assertThat(accessService.effectivePermission(Actor(UUID.randomUUID(), spaceId), spaceId))
            .isEqualTo(Permission.NONE)
        assertThat(accessService.isShared(actor, AccessTarget(spaceId, userId))).isFalse()
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
                ): Permission = Permission.VIEW
            }
        val accessService = AccessService(ownership, grant)

        assertThat(accessService.effectivePermission(actor, AccessTarget(spaceId, UUID.randomUUID())))
            .isEqualTo(Permission.VIEW)
        assertThat(accessService.isShared(actor, AccessTarget(spaceId, UUID.randomUUID()))).isTrue()
    }

    @Test
    fun `target 권한은 404와 403을 구분하고 resolveAll로 일괄 계산한다`() {
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
                ): Permission = if (target.shelfId != null) Permission.MANAGE else Permission.VIEW
            }
        val accessService = AccessService(ownership, grant)
        val roomId = UUID.randomUUID()
        val shelfId = UUID.randomUUID()
        val roomTarget = AccessTarget(spaceId, owner, roomId = roomId)
        val shelfTarget = AccessTarget(spaceId, owner, roomId = roomId, shelfId = shelfId)

        assertThat(accessService.require(actor, roomTarget, Permission.VIEW)).isEqualTo(Permission.VIEW)
        assertThat(accessService.require(actor, shelfTarget, Permission.MANAGE))
            .isEqualTo(Permission.MANAGE)
        assertThatThrownBy { accessService.require(actor, roomTarget, Permission.MANAGE) }
            .isInstanceOf(ApiException::class.java)
            .extracting("code")
            .isEqualTo("PERMISSION_DENIED")
        assertThat(accessService.resolveAll(actor, listOf(roomTarget, shelfTarget)))
            .containsEntry(roomTarget.key, Permission.VIEW)
            .containsEntry(shelfTarget.key, Permission.MANAGE)
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
