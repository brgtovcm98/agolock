package com.agolock.common.json

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode

/**
 * PATCH 역직렬화에서 JSON 타입을 암묵적으로 변환하지 않도록 하는 공용 reader입니다.
 */
object JsonPatchReaders {
    fun optionalText(
        parser: JsonParser,
        node: JsonNode,
        field: String,
    ): String? = if (node.isNull) null else text(parser, node, field)

    fun text(
        parser: JsonParser,
        node: JsonNode,
        field: String,
    ): String {
        if (!node.isTextual) {
            throw JsonMappingException.from(parser, "$field 값은 문자열이어야 합니다.")
        }
        return node.textValue()
    }

    fun boolean(
        parser: JsonParser,
        node: JsonNode,
        field: String,
    ): Boolean {
        if (!node.isBoolean) {
            throw JsonMappingException.from(parser, "$field 값은 boolean이어야 합니다.")
        }
        return node.booleanValue()
    }
}
