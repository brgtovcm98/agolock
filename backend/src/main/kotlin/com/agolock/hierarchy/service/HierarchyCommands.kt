package com.agolock.hierarchy.service

data class CreateNodeCommand(
    val name: String,
    val description: String?,
)

data class UpdateNodeCommand(
    val name: String?,
    val description: String?,
    val version: Long,
)

data class MoveShelfCommand(
    val roomId: java.util.UUID,
    val version: Long,
)

data class MoveBoxCommand(
    val shelfId: java.util.UUID,
    val version: Long,
)
