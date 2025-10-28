package com.github.bsaltz.insider.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter

@Configuration
class JdbcConfiguration : AbstractJdbcConfiguration() {
    private val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()

    @Bean
    override fun jdbcCustomConversions(): JdbcCustomConversions =
        JdbcCustomConversions(
            listOf(
                JsonNodeToJsonbWritingConverter(objectMapper),
                JsonbToJsonNodeReadingConverter(objectMapper),
            ),
        )
}
