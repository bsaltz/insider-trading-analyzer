package com.github.bsaltz.insider.congress2.house.ptr

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.github.bsaltz.insider.congress2.house.client.HouseHttpClient
import com.github.bsaltz.insider.congress2.house.filinglist.HouseFilingListRow
import com.github.bsaltz.insider.congress2.house.filinglist.HouseFilingListService
import com.github.bsaltz.insider.congress2.house.llm.HouseLlmOutput
import com.github.bsaltz.insider.congress2.house.llm.HouseLlmService
import com.github.bsaltz.insider.congress2.house.llm.HouseLlmTransaction
import com.github.bsaltz.insider.utils.StorageUtil.getResource
import com.github.bsaltz.insider.vision.OcrProcessorService
import com.google.cloud.spring.storage.GoogleStorageLocation
import com.google.cloud.storage.Storage
import org.springframework.stereotype.Service
import java.time.Clock

@Service
class HousePtrService(
    private val houseFilingListService: HouseFilingListService,
    private val housePtrDownloadRepository: HousePtrDownloadRepository,
    private val housePtrOcrResultRepository: HousePtrOcrResultRepository,
    private val housePtrFilingRepository: HousePtrFilingRepository,
    private val housePtrTransactionRepository: HousePtrTransactionRepository,
    private val houseHttpClient: HouseHttpClient,
    private val ocrProcessorService: OcrProcessorService,
    private val houseLlmService: HouseLlmService,
    private val storage: Storage,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    fun processFilingListRow(
        docId: String,
        force: Boolean = false,
    ): HousePtrFiling? =
        processFilingListRow(houseFilingListService.getHouseFilingListRow(docId) ?: error("No row for docId: $docId"), force)

    fun processFilingListRow(
        houseFilingListRow: HouseFilingListRow,
        force: Boolean = false,
    ): HousePtrFiling? {
        val download = downloadPdf(houseFilingListRow.docId, houseFilingListRow.year, force) ?: return null
        val ocrResult = runOcr(houseFilingListRow.year, download, force)
        return parseFiling(ocrResult, force)
    }

    fun downloadPdf(
        docId: String,
        force: Boolean = false,
    ): HousePtrDownload? {
        val houseFilingListRow =
            houseFilingListService.getHouseFilingListRow(docId)
                ?: error("No row for docId: $docId")
        return downloadPdf(docId, houseFilingListRow.year, force)
    }

    private fun downloadPdf(
        docId: String,
        year: Int,
        force: Boolean = false,
    ): HousePtrDownload? {
        val existingDownload = housePtrDownloadRepository.findByDocId(docId)
        val etag = houseHttpClient.getPtrEtag(docId, year)
        if (existingDownload != null && !force && existingDownload.etag == etag) return existingDownload
        val gcsUri = getDocGcsUri(docId, year)
        val response = houseHttpClient.fetchPtr(docId, year, gcsUri)
        if (response == null) {
            println("Failed to fetch PTR document $docId for year $year - possibly an FDR file at different URL")
            return null
        }
        val download =
            existingDownload?.copy(etag = response.etag) ?: HousePtrDownload(
                docId = docId,
                gcsUri = gcsUri,
                etag = response.etag,
                parsed = false,
                parsedAt = null,
                createdAt = clock.instant(),
                updatedAt = null,
            )
        return housePtrDownloadRepository.save(download)
    }

    private fun getDocGcsUri(
        docId: String,
        year: Int,
    ): String = "gs://insider-trading-analyzer/congress/house/$year/$docId.pdf"

    fun runOcr(
        docId: String,
        force: Boolean = false,
    ): HousePtrOcrResult {
        val houseFilingListRow =
            houseFilingListService.getHouseFilingListRow(docId)
                ?: error("No row for docId: $docId")
        val housePtrDownload = housePtrDownloadRepository.findByDocId(docId) ?: error("No download for docId: $docId")
        return runOcr(houseFilingListRow.year, housePtrDownload, force)
    }

    private fun runOcr(
        year: Int,
        housePtrDownload: HousePtrDownload,
        force: Boolean = false,
    ): HousePtrOcrResult {
        val housePtrDownloadId = housePtrDownload.id ?: error("No id for housePtrDownload")
        val existingOcrResult = housePtrOcrResultRepository.findByHousePtrDownloadId(housePtrDownloadId)
        if (existingOcrResult != null && !force) return existingOcrResult
        val result = ocrProcessorService.parsePdf(GoogleStorageLocation(housePtrDownload.gcsUri)).response
        val gcsUri = getOcrGcsUri(housePtrDownload.docId, year)
        storage.getResource(gcsUri).outputStream.use { it.write(result.toByteArray()) }
        val housePtrOcrResult =
            existingOcrResult?.copy(gcsUri = gcsUri) ?: HousePtrOcrResult(
                docId = housePtrDownload.docId,
                housePtrDownloadId = housePtrDownloadId,
                gcsUri = gcsUri,
            )
        return housePtrOcrResultRepository.save(housePtrOcrResult)
    }

    private fun getOcrGcsUri(
        docId: String,
        year: Int,
    ): String = "gs://insider-trading-analyzer/congress/house/$year/$docId.txt"

    fun parseFiling(
        docId: String,
        force: Boolean = false,
    ): HousePtrFiling {
        val housePtrOcrResult =
            housePtrOcrResultRepository.findByDocId(docId)
                ?: error("No ocr result for docId: $docId")
        return parseFiling(housePtrOcrResult, force)
    }

    private fun parseFiling(
        ocrResult: HousePtrOcrResult,
        force: Boolean = false,
    ): HousePtrFiling {
        val ocrResultText =
            storage.getResource(ocrResult.gcsUri).inputStream.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        val (output, rawLlmResponse) = houseLlmService.process(ocrResultText)
        return output.saveHouseLlmOutput(ocrResult, rawLlmResponse)
    }

    private fun HouseLlmOutput.saveHouseLlmOutput(
        ocrResult: HousePtrOcrResult,
        response: String,
    ): HousePtrFiling {
        // TODO: Delete (or maybe update?) the old records if they exist
        val housePtrFiling =
            housePtrFilingRepository.save(
                HousePtrFiling(
                    housePtrOcrResultId = ocrResult.id ?: error("No ocr result id for docId: ${ocrResult.docId}"),
                    docId = ocrResult.docId,
                    rawLlmResponse = response,
                    createdAt = clock.instant(),
                ),
            )
        transactions.forEach { transaction ->
            housePtrTransactionRepository.save(transaction.toEntity(housePtrFiling))
        }
        return housePtrFiling
    }

    private fun HouseLlmTransaction.toEntity(housePtrFiling: HousePtrFiling): HousePtrTransaction =
        HousePtrTransaction(
            docId = housePtrFiling.docId,
            housePtrFilingId = housePtrFiling.id ?: error("No housePtrFiling id for docId: ${housePtrFiling.docId}"),
            owner = owner,
            asset = asset,
            transactionType = transactionType,
            transactionDate = date,
            notificationDate = notificationDate,
            amount = amount,
            certainty = certainty,
            // Empty for now
            additionalData = JsonNodeFactory.instance.objectNode(),
        )

    /**
     * Get processing statistics for a specific year.
     *
     * @param year The year to get statistics for
     * @return Statistics about processing progress and results
     */
    fun getStats(year: Int): HousePtrStats {
        // Get all filing list rows for the year
        val allFilingListRows = houseFilingListService.getHouseFilingListRows(year)
        val typePFilingListRows = allFilingListRows.filter { it.filingType == "P" }

        // Count downloads for this year's filings
        val downloadsCompleted =
            typePFilingListRows.count { row ->
                housePtrDownloadRepository.findByDocId(row.docId) != null
            }

        // Count OCR results for this year's filings
        val ocrResultsCompleted =
            typePFilingListRows.count { row ->
                housePtrOcrResultRepository.findByDocId(row.docId) != null
            }

        // Count parsed filings for this year's filings
        val filingsCompleted =
            typePFilingListRows.count { row ->
                housePtrFilingRepository.findByDocId(row.docId) != null
            }

        // Count total transactions extracted
        val transactionsExtracted =
            typePFilingListRows.sumOf { row ->
                val filing = housePtrFilingRepository.findByDocId(row.docId)
                if (filing != null) {
                    housePtrTransactionRepository.findByHousePtrFilingId(filing.id!!).size
                } else {
                    0
                }
            }

        return HousePtrStats(
            year = year,
            totalFilingListRows = allFilingListRows.size,
            typePFilingListRows = typePFilingListRows.size,
            downloadsCompleted = downloadsCompleted,
            ocrResultsCompleted = ocrResultsCompleted,
            filingsCompleted = filingsCompleted,
            transactionsExtracted = transactionsExtracted,
        )
    }
}
