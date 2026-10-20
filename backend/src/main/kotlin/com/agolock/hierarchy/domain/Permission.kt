package com.agolock.hierarchy.domain

enum class Permission {
    NONE,
    VIEW,
    EDIT,
    MANAGE,
    OWNER,
    ;

    fun atLeast(required: Permission): Boolean = ordinal >= required.ordinal
}
