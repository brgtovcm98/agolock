package com.agolock.inventory

import com.agolock.common.error.ApiException
import com.agolock.inventory.domain.CategoryNamePolicy
import com.agolock.inventory.domain.ItemFieldPolicy
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ItemFieldPolicyTest {
    @Test
    fun `빈 설명은 명시적 값이고 null만 상속이다`() {
        assertThat(ItemFieldPolicy.description("  ")).isEqualTo("")
        assertThat(ItemFieldPolicy.description(null)).isNull()
        assertThat(ItemFieldPolicy.description(" 설명 ")).isEqualTo("설명")
    }

    @Test
    fun `가격 0과 정수 최대값은 보존하고 음수는 거부한다`() {
        assertThat(ItemFieldPolicy.price(0)).isZero()
        assertThat(ItemFieldPolicy.price(Int.MAX_VALUE)).isEqualTo(Int.MAX_VALUE)
        assertThatThrownBy { ItemFieldPolicy.price(-1) }.isInstanceOf(ApiException::class.java)
    }

    @Test
    fun `빈 이름과 긴 설명과 음수 버전은 거부한다`() {
        assertThat(ItemFieldPolicy.name(" 물건 ")).isEqualTo("물건")
        assertThatThrownBy { ItemFieldPolicy.name("  ") }.isInstanceOf(ApiException::class.java)
        assertThatThrownBy { ItemFieldPolicy.description("x".repeat(2001)) }.isInstanceOf(ApiException::class.java)
        assertThatThrownBy { ItemFieldPolicy.version(-1) }.isInstanceOf(ApiException::class.java)
        ItemFieldPolicy.version(0)
    }

    @Test
    fun `카테고리는 NFKC와 대소문자 및 공백을 정규화한다`() {
        assertThat(CategoryNamePolicy.normalize("  ＡＢＣ　 도구  ")).isEqualTo("abc 도구")
        assertThat(CategoryNamePolicy.normalize("ABC\t 도구")).isEqualTo("abc 도구")
    }
}
