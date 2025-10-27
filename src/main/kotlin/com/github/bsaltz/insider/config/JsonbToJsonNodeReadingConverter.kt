package com.github.bsaltz.insider.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.postgresql.util.PGobject
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter

@ReadingConverter
class JsonbToJsonNodeReadingConverter(
    private val objectMapper: ObjectMapper,
) : Converter<PGobject, JsonNode> {
    override fun convert(source: PGobject): JsonNode = objectMapper.readTree(source.value)
}
