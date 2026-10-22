package com.agolock.inventory.api

import com.agolock.hierarchy.api.UpdateNodeRequest
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PatchDeserializerTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `문자열 필드에 숫자를 보내면 400 매핑 예외가 난다`() {
        assertThrows<JsonMappingException> {
            objectMapper.readValue("""{"nameOverride":123,"version":0}""", UpdateInventoryRequest::class.java)
        }
        assertThrows<JsonMappingException> {
            objectMapper.readValue("""{"name":123,"version":0}""", UpdateItemRequest::class.java)
        }
        assertThrows<JsonMappingException> {
            objectMapper.readValue("""{"name":123,"version":0}""", UpdateNodeRequest::class.java)
        }
    }

    @Test
    fun `confirmDisposal은 boolean만 허용한다`() {
        assertThrows<JsonMappingException> {
            objectMapper.readValue("""{"confirmDisposal":"yes","version":0}""", UpdateInventoryRequest::class.java)
        }
    }

    @Test
    fun `null 필드는 오버라이드 해제로 유지한다`() {
        val request =
            objectMapper.readValue(
                """{"nameOverride":null,"descriptionOverride":null,"version":0}""",
                UpdateInventoryRequest::class.java,
            )

        assertThat(request.nameOverridePresent).isTrue()
        assertThat(request.nameOverride).isNull()
        assertThat(request.descriptionOverridePresent).isTrue()
        assertThat(request.descriptionOverride).isNull()
    }
}
