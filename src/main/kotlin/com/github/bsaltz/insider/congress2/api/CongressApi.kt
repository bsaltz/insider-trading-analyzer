package com.github.bsaltz.insider.congress2.api

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class CongressApi(
    @Value("\${com.github.bsaltz.insider.congress2.apiKey}")
    apiKey: String,
) {
    private val restClient: RestClient =
        RestClient
            .builder()
            .baseUrl("https://api.congress.gov/v3")
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
            .defaultHeader(HttpHeaders.ACCEPT, "application/json")
            .defaultHeader(HttpHeaders.CONNECTION, "keep-alive")
            .defaultHeader("X-Api-Key", apiKey)
            .build()

    fun <T> getEntity(
        path: String,
        responseType: Class<T>,
        headers: Map<String, String> = emptyMap(),
        parameters: Map<String, String?> = emptyMap(),
    ): ResponseEntity<T?> =
        restClient
            .get()
            .uri(path)
            .headers { headers.forEach { (key, value) -> it.set(key, value) } }
            .attributes { params -> parameters.forEach { (key, value) -> params[key] = value } }
            .retrieve()
            .toEntity(responseType)

    companion object {
        private const val USER_AGENT = "TradingAnalyzer/1.0"

        inline fun <reified T> CongressApi.get(
            path: String,
            headers: Map<String, String> = emptyMap(),
            parameters: Map<String, String?> = emptyMap(),
        ): T? = this@get.getEntity(path, T::class.java, headers, parameters).body
    }
}
