package com.github.bsaltz.insider.congress.house

import com.github.bsaltz.insider.congress2.house.client.StoredResponse
import com.google.cloud.spring.storage.GoogleStorageLocation
import com.google.cloud.spring.storage.GoogleStorageResource
import com.google.cloud.storage.Storage
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.nio.ByteBuffer

@Component
class CongressHouseHttpClient(
    private val storage: Storage,
) {
    private val restClient: RestClient =
        RestClient
            .builder()
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
            .build()

    fun fetchDisclosureList(year: Int): StoredResponse {
        val entity = disclosureListGetEntity(year)
        val location = storeResponse(getListGcsUri(year), entity)
        return StoredResponse(
            googleStorageLocation = location,
            etag = entity.headers[HttpHeaders.ETAG]?.firstOrNull() ?: "",
        )
    }

    fun getDisclosureListEtag(year: Int): String? = disclosureListHeadHeaders(year)[HttpHeaders.ETAG]?.firstOrNull()

    fun fetchDisclosureDoc(
        docId: String,
        year: Int,
    ): StoredResponse {
        val entity = disclosureDocGetEntity(docId, year)
        val location = storeResponse(getDocGcsUri(docId, year), entity)
        val etag = (
            entity.headers[HttpHeaders.ETAG]?.firstOrNull()
                ?: error("No etag found for disclosure doc $year/$docId")
        )
        return StoredResponse(location, etag)
    }

    fun getDisclosureDocEtag(
        docId: String,
        year: Int,
    ): String? = disclosureDocHeadHeaders(docId, year)[HttpHeaders.ETAG]?.firstOrNull()

    private fun getListUrl(year: Int): String = "https://disclosures-clerk.house.gov/public_disc/financial-pdfs/${year}FD.zip"

    private fun getListGcsUri(year: Int): String = "gs://insider-trading-analyzer/congress/house/disclosure-list/$year.zip"

    private fun disclosureListGetEntity(year: Int): ResponseEntity<Resource> =
        restClient
            .get()
            .uri(getListUrl(year))
            .retrieve()
            .toEntity(Resource::class.java)

    private fun disclosureListHeadHeaders(year: Int): HttpHeaders =
        restClient
            .head()
            .uri(getListUrl(year))
            .retrieve()
            .toBodilessEntity()
            .headers

    private fun getDocUrl(
        docId: String,
        year: Int,
    ): String = "https://disclosures-clerk.house.gov/public_disc/ptr-pdfs/$year/$docId.pdf"

    private fun getDocGcsUri(
        docId: String,
        year: Int,
    ): String = "gs://insider-trading-analyzer/congress/house/$year/$docId.pdf"

    private fun disclosureDocGetEntity(
        docId: String,
        year: Int,
    ): ResponseEntity<Resource> =
        restClient
            .get()
            .uri(getDocUrl(docId, year))
            .retrieve()
            .toEntity(Resource::class.java)

    private fun disclosureDocHeadHeaders(
        docId: String,
        year: Int,
    ): HttpHeaders =
        restClient
            .head()
            .uri(getDocUrl(docId, year))
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
