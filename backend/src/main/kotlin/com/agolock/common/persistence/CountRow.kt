package com.agolock.common.persistence

import java.util.UUID

/**
 * GROUP BY count 조회 결과를 담는 공용 row입니다.
 */
data class CountRow(
    var id: UUID = UUID(0, 0),
    var cnt: Int = 0,
)
