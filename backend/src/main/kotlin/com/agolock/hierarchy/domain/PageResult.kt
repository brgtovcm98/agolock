package com.agolock.hierarchy.domain

data class PageResult<T>(
    val items: List<T>,
    val nextCursor: String?,
)
