package com.agolock.inventory

import com.agolock.common.error.ApiException
import com.agolock.inventory.service.InventoryQueryText
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class InventoryQueryTextTest {
    @Test
    fun `검색어 길이 초과는 잘라내지 않고 거부한다`() {
        assertThat(InventoryQueryText.normalize("가".repeat(200))).hasSize(200)
        assertThatThrownBy { InventoryQueryText.normalize("가".repeat(201)) }.isInstanceOf(ApiException::class.java)
    }

    @Test
    fun `검색어 양끝 공백을 제거하고 빈 검색어를 정규화한다`() {
        assertThat(InventoryQueryText.normalize("  선반 > 상자  ")).isEqualTo("선반 > 상자")
        assertThat(InventoryQueryText.normalize("   ")).isNull()
        assertThat(InventoryQueryText.normalize(null)).isNull()
    }
}
