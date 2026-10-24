package com.agolock.inventory.api

data class ItemUpdateImpactPreviewResponse(
    val version: Long,
    val changedFields: List<String>,
    val totalInventoryCount: Int,
    val affectedInventoryCount: Int,
    val fullyProtectedInventoryCount: Int,
    val fieldImpacts: List<ItemFieldImpactResponse>,
)

data class ItemFieldImpactResponse(
    val field: String,
    val affectedInventoryCount: Int,
    val protectedInventoryCount: Int,
)
