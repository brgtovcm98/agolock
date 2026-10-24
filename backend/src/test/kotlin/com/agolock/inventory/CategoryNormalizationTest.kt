package com.agolock.inventory

import com.agolock.inventory.domain.CategoryNamePolicy
import com.agolock.inventory.domain.CategoryNormalizationRow
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class CategoryNormalizationTest {
    @Test
    fun `같은 Space 정규화 충돌은 ID를 보고하고 자동 병합하지 않는다`() {
        val space = UUID.randomUUID()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        assertThatThrownBy {
            CategoryNamePolicy.plan(
                listOf(
                    CategoryNormalizationRow(first, space, "ＡＢＣ"),
                    CategoryNormalizationRow(second, space, "abc"),
                ),
            )
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining(first.toString())
            .hasMessageContaining(second.toString())
    }

    @Test
    fun `다른 Space의 같은 이름은 허용한다`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val plan =
            CategoryNamePolicy.plan(
                listOf(
                    CategoryNormalizationRow(first, UUID.randomUUID(), "ＡＢＣ"),
                    CategoryNormalizationRow(second, UUID.randomUUID(), "abc"),
                ),
            )
        assertThat(plan).containsEntry(first, "abc").containsEntry(second, "abc")
    }
}
