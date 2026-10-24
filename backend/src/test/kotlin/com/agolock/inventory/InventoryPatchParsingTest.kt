package com.agolock.inventory

import com.agolock.inventory.api.CategoryListResponse
import com.agolock.inventory.api.CategoryResponse
import com.agolock.inventory.api.UpdateCategoryRequest
import com.agolock.inventory.api.UpdateInventoryRequest
import com.agolock.inventory.api.UpdateItemRequest
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class InventoryPatchParsingTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun `카테고리 색상은 생략과 명시 null을 구분한다`() {
        val keep = mapper.readValue("{}", UpdateCategoryRequest::class.java)
        val clear = mapper.readValue("{\"color\":null}", UpdateCategoryRequest::class.java)
        assertThat(keep.colorPresent).isFalse()
        assertThat(clear.colorPresent).isTrue()
        assertThat(clear.color).isNull()
    }

    @Test
    fun `Item 이름 null과 객체 아닌 PATCH는 거부한다`() {
        assertThatThrownBy { mapper.readValue("{\"name\":null,\"version\":0}", UpdateItemRequest::class.java) }
            .isInstanceOf(JsonMappingException::class.java)
        assertThatThrownBy { mapper.readValue("[]", UpdateInventoryRequest::class.java) }
            .isInstanceOf(JsonMappingException::class.java)
    }

    @Test
    fun `상속 필드와 마지막 페이지의 null은 응답에 명시한다`() {
        val json = jacksonObjectMapper().setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
        val category =
            CategoryResponse(UUID.randomUUID(), "도구", null, 0)
        val categoryNode = json.readTree(json.writeValueAsString(category))
        val page =
            CategoryListResponse(emptyList())
        val pageNode = json.readTree(json.writeValueAsString(page))
        assertThat(categoryNode.has("color")).isTrue()
        assertThat(categoryNode["color"].isNull).isTrue()
        assertThat(pageNode.has("nextCursor")).isTrue()
        assertThat(pageNode["nextCursor"].isNull).isTrue()
    }
}
