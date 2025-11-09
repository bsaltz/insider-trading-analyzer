package com.github.bsaltz.insider.congress2.house.ptr

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.github.bsaltz.insider.congress2.house.HouseConfig
import com.github.bsaltz.insider.congress2.house.client.HouseHttpClient
import com.github.bsaltz.insider.congress2.house.filinglist.HouseFilingListRow
import com.github.bsaltz.insider.congress2.house.filinglist.HouseFilingListService
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
    private val housePtrLlmService: HousePtrLlmService,
    private val storage: Storage,
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
        // Check if already fully processed (unless force=true)
        if (!force) {
            val existingFilings = housePtrFilingRepository.findByDocId(houseFilingListRow.docId)
            if (existingFilings.isNotEmpty()) {
                println("  ✓ Filing ${houseFilingListRow.docId} already processed, skipping")
                return existingFilings.first()
            }
        }

        println("  → Step 1/3: Downloading PDF for ${houseFilingListRow.docId}...")
        val download = downloadPdf(houseFilingListRow.docId, houseFilingListRow.year, force)
        if (download == null) {
            println("  ✗ Download failed for ${houseFilingListRow.docId}")
            return null
        }
        println("  ✓ PDF downloaded for ${houseFilingListRow.docId}")

        println("  → Step 2/3: Running OCR on ${houseFilingListRow.docId}...")
        val ocrResult = runOcr(houseFilingListRow.year, download, force)
        println("  ✓ OCR completed for ${houseFilingListRow.docId}")

        println("  → Step 3/3: Parsing filing ${houseFilingListRow.docId}...")
        val result = parseFiling(ocrResult, force)
        println("  ✓ Parsing completed for ${houseFilingListRow.docId}")

        return result
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
        if (existingDownload != null && !force && existingDownload.etag == etag) {
            println("    → Using cached PDF for $docId (etag matches)")
            return existingDownload
        }
        println("    → Fetching PDF from remote for $docId...")
        val gcsUri = getDocGcsUri(docId, year)
        val response = houseHttpClient.fetchPtr(docId, year, gcsUri)
        if (response == null) {
            println("    ✗ Failed to fetch PTR document $docId for year $year - possibly an FDR file at different URL")
            return null
        }
        println("    ✓ Successfully fetched PDF for $docId")
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
    ): String = HouseConfig.ptrPdfGcsUri(year, docId)

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
        if (existingOcrResult != null && !force) {
            println("    → Using cached OCR result for ${housePtrDownload.docId}")
            return existingOcrResult
        }
        println("    → Running OCR on PDF for ${housePtrDownload.docId}...")
        val result = ocrProcessorService.parsePdf(GoogleStorageLocation(housePtrDownload.gcsUri)).response
        println("    ✓ OCR completed, saving result to GCS...")
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
    ): String = HouseConfig.ptrOcrGcsUri(year, docId)

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
        println("    → Loading OCR text from GCS for ${ocrResult.docId}...")
        val ocrResultText =
            storage.getResource(ocrResult.gcsUri).inputStream.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        println("    → Running LLM parsing on ${ocrResult.docId}...")
        val (output, rawLlmResponse) = housePtrLlmService.process(ocrResultText)
        println("    ✓ LLM parsing completed for ${ocrResult.docId}, saving ${output.transactions.size} transactions...")
        return output.saveHouseLlmOutput(ocrResult, rawLlmResponse)
    }

    private fun HouseLlmOutput.saveHouseLlmOutput(
        ocrResult: HousePtrOcrResult,
        response: String,
    ): HousePtrFiling {
        // Delete old records if they exist to avoid duplicates
        val existingFilings = housePtrFilingRepository.findByDocId(ocrResult.docId)
        existingFilings.forEach { existingFiling ->
            // Delete old transactions first (cascade should handle this, but being explicit)
            housePtrTransactionRepository.findByHousePtrFilingId(existingFiling.id!!).forEach {
                housePtrTransactionRepository.delete(it)
            }
            housePtrFilingRepository.delete(existingFiling)
        }

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

        // Early return if no filings
        if (typePFilingListRows.isEmpty()) {
            return HousePtrStats(
                year = year,
                totalFilingListRows = allFilingListRows.size,
                typePFilingListRows = 0,
                downloadsCompleted = 0,
                ocrResultsCompleted = 0,
                filingsCompleted = 0,
                transactionsExtracted = 0,
            )
        }

        // Extract all docIds for batch queries
        val docIds = typePFilingListRows.map { it.docId }

        // Batch query: Count downloads for this year's filings
        val downloadsCompleted = housePtrDownloadRepository.findByDocIdIn(docIds).size

        // Batch query: Count OCR results for this year's filings
        val ocrResultsCompleted = housePtrOcrResultRepository.findByDocIdIn(docIds).size

        // Batch query: Get all filings and count unique docIds that have filings
        val allFilings = docIds.flatMap { housePtrFilingRepository.findByDocId(it) }
        val filingsCompleted = allFilings.map { it.docId }.toSet().size

        // Batch query: Count total transactions extracted
        val filingIds = allFilings.mapNotNull { it.id }
        val transactionsExtracted =
            if (filingIds.isNotEmpty()) {
                housePtrTransactionRepository.findByHousePtrFilingIdIn(filingIds).size
            } else {
                0
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

    /**
     * Get all PTR transactions for a specific year.
     *
     * @param year The year to get transactions for
     * @return List of all transactions for that year
     */
    fun getTransactionsForYear(year: Int): List<HousePtrTransaction> {
        val filingListRows = houseFilingListService.getHouseFilingListRows(year)
        val typePFilingListRows = filingListRows.filter { it.filingType == "P" }

        return typePFilingListRows.flatMap { row ->
            val filings = housePtrFilingRepository.findByDocId(row.docId)
            filings.flatMap { filing ->
                housePtrTransactionRepository.findByHousePtrFilingId(filing.id!!)
            }
        }
    }

    /**
     * Get all PTR transactions for a specific year with representative information.
     *
     * This method enriches transaction data with the representative's name and district
     * by joining with the filing list rows.
     *
     * @param year The year to get transactions for
     * @return List of all transactions with representative info for that year
     */
    fun getTransactionsWithRepresentativeForYear(year: Int): List<HousePtrTransactionWithRepresentative> {
        val filingListRows = houseFilingListService.getHouseFilingListRows(year)
        val typePFilingListRows = filingListRows.filter { it.filingType == "P" }

        return typePFilingListRows.flatMap { row ->
            val filings = housePtrFilingRepository.findByDocId(row.docId)
            filings.flatMap { filing ->
                val transactions = housePtrTransactionRepository.findByHousePtrFilingId(filing.id!!)
                transactions.map { transaction ->
                    HousePtrTransactionWithRepresentative(
                        transaction = transaction,
                        prefix = row.prefix,
                        firstName = row.first,
                        lastName = row.last,
                        suffix = row.suffix,
                        stateDst = row.stateDst,
                        filingDate = row.filingDate,
                    )
                }
            }
        }
    }
}
