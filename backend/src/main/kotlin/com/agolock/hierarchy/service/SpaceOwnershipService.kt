package com.agolock.hierarchy.service

import com.agolock.identity.persistence.SpaceMapper
import org.springframework.stereotype.Service
import java.util.UUID

interface SpaceOwnership {
    fun ownerOf(spaceId: UUID): UUID?
}

@Service
class SpaceOwnershipService(
    private val spaceMapper: SpaceMapper,
) : SpaceOwnership {
    override fun ownerOf(spaceId: UUID): UUID? = spaceMapper.findById(spaceId)?.ownerUserId
}
