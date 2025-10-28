package com.github.bsaltz.insider.congress2.house.client

import com.google.cloud.spring.storage.GoogleStorageLocation
import com.google.cloud.spring.storage.GoogleStorageResource
import com.google.cloud.storage.Storage
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.nio.ByteBuffer
import kotlin.collections.firstOrNull

@Service
class HouseHttpClient(
    private val storage: Storage,
) {
    private val restClient: RestClient =
        RestClient
            .builder()
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
            .defaultHeader(HttpHeaders.ACCEPT, "*/*")
            .defaultHeader(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate")
            .defaultHeader(HttpHeaders.CONNECTION, "keep-alive")
            .build()

    fun fetchFilingList(
        year: Int,
        gcsUri: String,
    ): StoredResponse {
        val entity = filingListGetEntity(year) ?: error("Failed to fetch filing list for year $year")
        val location = storeResponse(gcsUri, entity)
        return StoredResponse(
            googleStorageLocation = location,
            etag = entity.headers[HttpHeaders.ETAG]?.firstOrNull() ?: "",
        )
    }

    fun getFilingListEtag(year: Int): String? = filingListHeadHeaders(year)?.get(HttpHeaders.ETAG)?.firstOrNull()

    fun fetchPtr(
        docId: String,
        year: Int,
        gcsUri: String,
    ): StoredResponse? {
        val entity = ptrGetEntity(docId, year) ?: return null
        val location = storeResponse(gcsUri, entity)
        val etag =
            entity.headers[HttpHeaders.ETAG]?.firstOrNull()
                ?: error("No etag found for disclosure doc $year/$docId")
        return StoredResponse(location, etag)
    }

    fun getPtrEtag(
        docId: String,
        year: Int,
    ): String? = ptrHeadHeaders(docId, year)?.get(HttpHeaders.ETAG)?.firstOrNull()

    private fun filingListUrl(year: Int): String = "https://disclosures-clerk.house.gov/public_disc/financial-pdfs/${year}FD.zip"

    private fun filingListGetEntity(year: Int): ResponseEntity<Resource>? =
        runCatching {
            restClient
                .get()
                .uri(filingListUrl(year))
                .retrieve()
                .toEntity(Resource::class.java)
        }.onFailure { exception ->
            when (exception) {
                is org.springframework.web.client.RestClientResponseException -> {
                    println("HTTP error fetching filing list for year $year: ${exception.statusCode} ${exception.statusText}")
                    println("Response body: ${exception.responseBodyAsString}")
                }
                else -> println("Error fetching filing list for year $year: ${exception.message}")
            }
        }.getOrNull()

    private fun filingListHeadHeaders(year: Int): HttpHeaders? =
        runCatching {
            restClient
                .head()
                .uri(filingListUrl(year))
                .retrieve()
                .toBodilessEntity()
                .headers
        }.onFailure { exception ->
            when (exception) {
                is org.springframework.web.client.RestClientResponseException -> {
                    println("HTTP error HEAD filing list for year $year: ${exception.statusCode} ${exception.statusText}")
                    println("Response body: ${exception.responseBodyAsString}")
                }
                else -> println("Error HEAD filing list for year $year: ${exception.message}")
            }
        }.getOrNull()

    private fun ptrDocUrl(
        docId: String,
        year: Int,
    ): String = "https://disclosures-clerk.house.gov/public_disc/ptr-pdfs/$year/$docId.pdf"

    private fun ptrGetEntity(
        docId: String,
        year: Int,
    ): ResponseEntity<Resource>? =
        runCatching {
            restClient
                .get()
                .uri(ptrDocUrl(docId, year))
                .retrieve()
                .toEntity(Resource::class.java)
        }.onFailure { exception ->
            when (exception) {
                is org.springframework.web.client.RestClientResponseException -> {
                    println("HTTP error fetching PTR $docId for year $year: ${exception.statusCode} ${exception.statusText}")
                    println("Response body: ${exception.responseBodyAsString}")
                }
                else -> println("Error fetching PTR $docId for year $year: ${exception.message}")
            }
        }.getOrNull()

    private fun ptrHeadHeaders(
        docId: String,
        year: Int,
    ): HttpHeaders? =
        runCatching {
            restClient
                .head()
                .uri(ptrDocUrl(docId, year))
                .retrieve()
                .toBodilessEntity()
                .headers
        }.onFailure { exception ->
            when (exception) {
                is org.springframework.web.client.RestClientResponseException -> {
                    println("HTTP error HEAD PTR $docId for year $year: ${exception.statusCode} ${exception.statusText}")
                    println("Response body: ${exception.responseBodyAsString}")
                }
                else -> println("Error HEAD PTR $docId for year $year: ${exception.message}")
            }
        }.getOrNull()

    private fun storeResponse(
        gcsUri: String,
        responseEntity: ResponseEntity<Resource>,
    ): GoogleStorageLocation {
        val resource = GoogleStorageResource(storage, gcsUri)
        responseEntity.body?.inputStream?.use { response ->
            resource.writableChannel().use { channel ->
                val buffer = ByteArray(10240)
                while (true) {
                    val bytesRead = response.read(buffer)
                    if (bytesRead == -1) break
                    channel.write(ByteBuffer.wrap(buffer, 0, bytesRead))
                }
            }
        }
        return resource.googleStorageLocation
    }

    companion object {
        private const val USER_AGENT = "TradingAnalyzer/1.0"
    }
}
