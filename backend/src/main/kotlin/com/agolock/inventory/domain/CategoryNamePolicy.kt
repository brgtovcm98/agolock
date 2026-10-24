package com.agolock.inventory.domain

import java.text.Normalizer
import java.util.Locale
import java.util.UUID

/** 서비스와 기존 데이터 마이그레이션이 공유하는 정규화입니다. */
data class CategoryNormalizationRow(
    val id: UUID,
    val spaceId: UUID,
    val name: String,
)

object CategoryNamePolicy {
    /** 모든 충돌을 검사한 뒤 갱신 계획을 반환하며 원본을 변경하지 않습니다. */
    fun plan(rows: List<CategoryNormalizationRow>): Map<UUID, String> {
        val normalized = rows.associate { it.id to normalize(it.name) }
        val collisions = rows.groupBy { it.spaceId to normalized.getValue(it.id) }.values.filter { it.size > 1 }
        check(collisions.isEmpty()) {
            "카테고리 정규화 충돌: 명시적으로 병합한 뒤 재실행하세요. IDs=" +
                collisions.flatten().joinToString(",") { it.id.toString() }
        }
        return normalized
    }

    fun normalize(value: String): String =
        Normalizer
            .normalize(value, Normalizer.Form.NFKC)
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
}
