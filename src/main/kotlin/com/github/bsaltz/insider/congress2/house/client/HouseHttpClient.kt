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
class HouseHttpClient(private val storage: Storage) {
    private val restClient: RestClient =
        RestClient
            .builder()
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
            .build()

    fun fetchFilingList(year: Int, gcsUri: String): StoredResponse {
        val entity = filingListGetEntity(year)
        val location = storeResponse(gcsUri, entity)
        return StoredResponse(
            googleStorageLocation = location,
            etag = entity.headers[HttpHeaders.ETAG]?.firstOrNull() ?: "",
        )
    }

    fun getFilingListEtag(year: Int): String? = filingListHeadHeaders(year)[HttpHeaders.ETAG]?.firstOrNull()

    fun fetchPtr(
        docId: String,
        year: Int,
        gcsUri: String,
    ): StoredResponse {
        val entity = ptrGetEntity(docId, year)
        val location = storeResponse(gcsUri, entity)
        val etag = entity.headers[HttpHeaders.ETAG]?.firstOrNull()
            ?: error("No etag found for disclosure doc $year/$docId")
        return StoredResponse(location, etag)
    }

    fun getPtrEtag(
        docId: String,
        year: Int,
    ): String? = ptrHeadHeaders(docId, year)[HttpHeaders.ETAG]?.firstOrNull()

    private fun filingListUrl(year: Int): String =
        "https://disclosures-clerk.house.gov/public_disc/financial-pdfs/${year}FD.zip"

    private fun filingListGetEntity(year: Int): ResponseEntity<Resource> =
        restClient
            .get()
            .uri(filingListUrl(year))
            .retrieve()
            .toEntity(Resource::class.java)

    private fun filingListHeadHeaders(year: Int): HttpHeaders =
        restClient
            .head()
            .uri(filingListUrl(year))
            .retrieve()
            .toBodilessEntity()
            .headers

    private fun ptrDocUrl(docId: String, year: Int): String =
        "https://disclosures-clerk.house.gov/public_disc/ptr-pdfs/$year/$docId.pdf"

    private fun ptrGetEntity(
        docId: String,
        year: Int,
    ): ResponseEntity<Resource> =
        restClient
            .get()
            .uri(ptrDocUrl(docId, year))
            .retrieve()
            .toEntity(Resource::class.java)

    private fun ptrHeadHeaders(
        docId: String,
        year: Int,
    ): HttpHeaders =
        restClient
            .head()
            .uri(ptrDocUrl(docId, year))
            .retrieve()
            .toBodilessEntity()
            .headers

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
