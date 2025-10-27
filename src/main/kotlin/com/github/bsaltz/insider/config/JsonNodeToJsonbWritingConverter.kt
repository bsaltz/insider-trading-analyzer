package com.github.bsaltz.insider.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.postgresql.util.PGobject
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.WritingConverter

@WritingConverter
class JsonNodeToJsonbWritingConverter(
    private val objectMapper: ObjectMapper,
) : Converter<JsonNode, PGobject> {
    override fun convert(source: JsonNode): PGobject {
        val pgObject = PGobject()
        pgObject.type = "jsonb"
        pgObject.value = objectMapper.writeValueAsString(source)
        return pgObject
    }
}
