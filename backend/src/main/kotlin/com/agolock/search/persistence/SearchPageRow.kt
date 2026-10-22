@file:Suppress("MaxLineLength")

package com.agolock.search.persistence

import java.time.OffsetDateTime
import java.util.UUID

/**
 * `UPDATED_DESC` keyset 페이지의 Item 그룹 한 줄입니다.
 *
 * MyBatis가 파라미터 없는 생성자와 setter로 매핑하므로 모든 프로퍼티는 `var`와 기본값을 가집니다.
 * `updatedAt`은 그룹에 매칭된 Inventory 중 가장 최근 수정 시각이고, `inventoryCount`는 ACL·필터를
 * 통과한 Inventory 수입니다.
 */
data class SearchPageRow(
    var itemId: UUID = UUID(0L, 0L),
    var updatedAt: OffsetDateTime = OffsetDateTime.MIN,
    var inventoryCount: Int = 0,
)
