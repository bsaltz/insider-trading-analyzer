package com.github.bsaltz.insider.congress2.house.ptr

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.github.bsaltz.insider.congress2.house.client.HouseHttpClient
import com.github.bsaltz.insider.congress2.house.client.StoredResponse
import com.github.bsaltz.insider.congress2.house.filinglist.HouseFilingListRow
import com.github.bsaltz.insider.congress2.house.filinglist.HouseFilingListService
import com.github.bsaltz.insider.congress2.house.llm.HouseLlmService
import com.github.bsaltz.insider.vision.OcrProcessorService
import com.google.cloud.spring.storage.GoogleStorageLocation
import com.google.cloud.storage.Storage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class HousePtrServiceTest {
    private val houseFilingListService = mock<HouseFilingListService>()
    private val housePtrDownloadRepository = mock<HousePtrDownloadRepository>()
    private val housePtrOcrResultRepository = mock<HousePtrOcrResultRepository>()
    private val housePtrFilingRepository = mock<HousePtrFilingRepository>()
    private val housePtrTransactionRepository = mock<HousePtrTransactionRepository>()
    private val houseHttpClient = mock<HouseHttpClient>()
    private val ocrProcessorService = mock<OcrProcessorService>()
    private val houseLlmService = mock<HouseLlmService>()
    private val storage = mock<Storage>()
    private val fixedInstant = Instant.parse("2025-09-26T10:30:00Z").truncatedTo(ChronoUnit.MICROS)
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private val housePtrService =
        HousePtrService(
            houseFilingListService = houseFilingListService,
            housePtrDownloadRepository = housePtrDownloadRepository,
            housePtrOcrResultRepository = housePtrOcrResultRepository,
            housePtrFilingRepository = housePtrFilingRepository,
            housePtrTransactionRepository = housePtrTransactionRepository,
            houseHttpClient = houseHttpClient,
            ocrProcessorService = ocrProcessorService,
            houseLlmService = houseLlmService,
            storage = storage,
            clock = clock,
        )

    @Test
    fun `downloadPdf should create new download when none exists`() {
        // Given
        val docId = "20250101-001"
        val year = 2025
        val etag = "test-etag"
        val gcsUri = "gs://insider-trading-analyzer/congress/house/$year/$docId.pdf"

        val filingListRow = createFilingListRow(docId, year)
        whenever(houseFilingListService.getHouseFilingListRow(docId)).thenReturn(filingListRow)
        whenever(housePtrDownloadRepository.findByDocId(docId)).thenReturn(null)
        whenever(houseHttpClient.getPtrEtag(docId, year)).thenReturn(etag)
        whenever(houseHttpClient.fetchPtr(docId, year, gcsUri))
            .thenReturn(StoredResponse(GoogleStorageLocation.forFile("test-bucket", "$docId.pdf"), etag))

        val savedDownload =
            HousePtrDownload(
                id = 1L,
                docId = docId,
                gcsUri = gcsUri,
                etag = etag,
                parsed = false,
                parsedAt = null,
                createdAt = fixedInstant,
                updatedAt = null,
            )
        whenever(housePtrDownloadRepository.save(any())).thenReturn(savedDownload)

        // When
        val result = housePtrService.downloadPdf(docId, force = false)

        // Then
        assertNotNull(result)
        assertEquals(docId, result!!.docId)
        assertEquals(etag, result.etag)
        verify(housePtrDownloadRepository).save(any())
        verify(houseHttpClient).fetchPtr(docId, year, gcsUri)
    }

    @Test
    fun `downloadPdf should return existing download when etag matches and not forced`() {
        // Given
        val docId = "20250101-001"
        val year = 2025
        val etag = "test-etag"

        val filingListRow = createFilingListRow(docId, year)
        val existingDownload =
            HousePtrDownload(
                id = 1L,
                docId = docId,
                gcsUri = "gs://insider-trading-analyzer/congress/house/$year/$docId.pdf",
                etag = etag,
                parsed = false,
                parsedAt = null,
                createdAt = fixedInstant,
                updatedAt = null,
            )

        whenever(houseFilingListService.getHouseFilingListRow(docId)).thenReturn(filingListRow)
        whenever(housePtrDownloadRepository.findByDocId(docId)).thenReturn(existingDownload)
        whenever(houseHttpClient.getPtrEtag(docId, year)).thenReturn(etag)

        // When
        val result = housePtrService.downloadPdf(docId, force = false)

        // Then
        assertNotNull(result)
        assertEquals(existingDownload, result)
        verify(houseHttpClient, never()).fetchPtr(any(), any(), any())
        verify(housePtrDownloadRepository, never()).save(any())
    }

    @Test
    fun `downloadPdf should update download when etag changes`() {
        // Given
        val docId = "20250101-001"
        val year = 2025
        val oldEtag = "old-etag"
        val newEtag = "new-etag"
        val gcsUri = "gs://insider-trading-analyzer/congress/house/$year/$docId.pdf"

        val filingListRow = createFilingListRow(docId, year)
        val existingDownload =
            HousePtrDownload(
                id = 1L,
                docId = docId,
                gcsUri = gcsUri,
                etag = oldEtag,
                parsed = false,
                parsedAt = null,
                createdAt = fixedInstant,
                updatedAt = null,
            )

        whenever(houseFilingListService.getHouseFilingListRow(docId)).thenReturn(filingListRow)
        whenever(housePtrDownloadRepository.findByDocId(docId)).thenReturn(existingDownload)
        whenever(houseHttpClient.getPtrEtag(docId, year)).thenReturn(newEtag)
        whenever(houseHttpClient.fetchPtr(docId, year, gcsUri))
            .thenReturn(StoredResponse(GoogleStorageLocation.forFile("test-bucket", "$docId.pdf"), newEtag))

        val updatedDownload = existingDownload.copy(etag = newEtag)
        whenever(housePtrDownloadRepository.save(any())).thenReturn(updatedDownload)

        // When
        val result = housePtrService.downloadPdf(docId, force = false)

        // Then
        assertNotNull(result)
        assertEquals(newEtag, result!!.etag)
        verify(houseHttpClient).fetchPtr(docId, year, gcsUri)
        verify(housePtrDownloadRepository).save(any())
    }

    @Test
    fun `downloadPdf should force download when force is true`() {
        // Given
        val docId = "20250101-001"
        val year = 2025
        val etag = "test-etag"
        val gcsUri = "gs://insider-trading-analyzer/congress/house/$year/$docId.pdf"

        val filingListRow = createFilingListRow(docId, year)
        val existingDownload =
            HousePtrDownload(
                id = 1L,
                docId = docId,
                gcsUri = gcsUri,
                etag = etag,
                parsed = false,
                parsedAt = null,
                createdAt = fixedInstant,
                updatedAt = null,
            )

        whenever(houseFilingListService.getHouseFilingListRow(docId)).thenReturn(filingListRow)
        whenever(housePtrDownloadRepository.findByDocId(docId)).thenReturn(existingDownload)
        whenever(houseHttpClient.getPtrEtag(docId, year)).thenReturn(etag)
        whenever(houseHttpClient.fetchPtr(docId, year, gcsUri))
            .thenReturn(StoredResponse(GoogleStorageLocation.forFile("test-bucket", "$docId.pdf"), etag))
        whenever(housePtrDownloadRepository.save(any())).thenReturn(existingDownload)

        // When
        val result = housePtrService.downloadPdf(docId, force = true)

        // Then
        assertNotNull(result)
        verify(houseHttpClient).fetchPtr(docId, year, gcsUri)
    }

    @Test
    fun `downloadPdf should return null when fetch fails`() {
        // Given
        val docId = "20250101-001"
        val year = 2025
        val etag = "test-etag"
        val gcsUri = "gs://insider-trading-analyzer/congress/house/$year/$docId.pdf"

        val filingListRow = createFilingListRow(docId, year)
        whenever(houseFilingListService.getHouseFilingListRow(docId)).thenReturn(filingListRow)
        whenever(housePtrDownloadRepository.findByDocId(docId)).thenReturn(null)
        whenever(houseHttpClient.getPtrEtag(docId, year)).thenReturn(etag)
        whenever(houseHttpClient.fetchPtr(docId, year, gcsUri)).thenReturn(null)

        // When
        val result = housePtrService.downloadPdf(docId, force = false)

        // Then
        assertNull(result)
        verify(housePtrDownloadRepository, never()).save(any())
    }

    @Test
    fun `downloadPdf should throw error when filing list row not found`() {
        // Given
        val docId = "nonexistent"
        whenever(houseFilingListService.getHouseFilingListRow(docId)).thenReturn(null)

        // When & Then
        assertThrows(IllegalStateException::class.java) {
            housePtrService.downloadPdf(docId, force = false)
        }
    }

    // Note: runOcr tests with Storage interactions are complex to mock due to extension functions
    // These would be better tested as integration tests

    @Test
    fun `runOcr should return existing result when not forced`() {
        // Given
        val docId = "20250101-001"
        val year = 2025
        val downloadId = 1L

        val filingListRow = createFilingListRow(docId, year)
        val download =
            HousePtrDownload(
                id = downloadId,
                docId = docId,
                gcsUri = "gs://insider-trading-analyzer/congress/house/$year/$docId.pdf",
                etag = "etag",
                parsed = false,
                parsedAt = null,
                createdAt = fixedInstant,
                updatedAt = null,
            )
        val existingOcrResult =
            HousePtrOcrResult(
                id = 1L,
                docId = docId,
                housePtrDownloadId = downloadId,
                gcsUri = "gs://insider-trading-analyzer/congress/house/$year/$docId.txt",
            )

        whenever(houseFilingListService.getHouseFilingListRow(docId)).thenReturn(filingListRow)
        whenever(housePtrDownloadRepository.findByDocId(docId)).thenReturn(download)
        whenever(housePtrOcrResultRepository.findByHousePtrDownloadId(downloadId)).thenReturn(existingOcrResult)

        // When
        val result = housePtrService.runOcr(docId, force = false)

        // Then
        assertNotNull(result)
        assertEquals(existingOcrResult, result)
        verify(ocrProcessorService, never()).parsePdf(any())
        verify(housePtrOcrResultRepository, never()).save(any())
    }

    // Note: parseFiling tests with Storage interactions are complex to mock due to extension functions
    // These would be better tested as integration tests

    @Test
    fun `getStats should return statistics for year`() {
        // Given
        val year = 2025
        val docId1 = "20250101-001"
        val docId2 = "20250102-001"
        val docId3 = "20250103-001"

        val filingListRows =
            listOf(
                createFilingListRow(docId1, year, filingType = "P"),
                createFilingListRow(docId2, year, filingType = "P"),
                createFilingListRow(docId3, year, filingType = "A"),
            )
        whenever(houseFilingListService.getHouseFilingListRows(year)).thenReturn(filingListRows)

        // Batch query mocks for type P filings only (docId1, docId2)
        val typePDocIds = listOf(docId1, docId2)

        // Doc 1: fully processed with 2 transactions
        val download1 = createDownload(docId1, year, 1L)
        val download2 = createDownload(docId2, year, 2L)
        whenever(housePtrDownloadRepository.findByDocIdIn(typePDocIds)).thenReturn(listOf(download1, download2))

        val ocrResult1 = createOcrResult(docId1, year, 1L, 1L)
        whenever(housePtrOcrResultRepository.findByDocIdIn(typePDocIds)).thenReturn(listOf(ocrResult1))

        val filing1 = createFiling(docId1, 1L, 1L)
        whenever(housePtrFilingRepository.findByDocId(docId1)).thenReturn(listOf(filing1))
        whenever(housePtrFilingRepository.findByDocId(docId2)).thenReturn(emptyList())

        val transactions1 = listOf(createTransaction(docId1, 1L, 1L), createTransaction(docId1, 1L, 2L))
        whenever(housePtrTransactionRepository.findByHousePtrFilingIdIn(listOf(1L))).thenReturn(transactions1)

        // When
        val result = housePtrService.getStats(year)

        // Then
        assertEquals(year, result.year)
        assertEquals(3, result.totalFilingListRows)
        assertEquals(2, result.typePFilingListRows)
        assertEquals(2, result.downloadsCompleted)
        assertEquals(1, result.ocrResultsCompleted)
        assertEquals(1, result.filingsCompleted)
        assertEquals(2, result.transactionsExtracted)
    }

    // Note: processFilingListRow full workflow tests with Storage interactions are complex to mock
    // due to extension functions. These would be better tested as integration tests

    @Test
    fun `processFilingListRow should skip processing when filing already exists and force is false`() {
        // Given
        val docId = "20250101-001"
        val year = 2025
        val filingListRow = createFilingListRow(docId, year)
        val existingFiling = createFiling(docId, 1L, 1L)

        whenever(housePtrFilingRepository.findByDocId(docId)).thenReturn(listOf(existingFiling))

        // When
        val result = housePtrService.processFilingListRow(filingListRow, force = false)

        // Then
        assertNotNull(result)
        assertEquals(existingFiling, result)
        verify(houseHttpClient, never()).getPtrEtag(any(), any())
        verify(houseHttpClient, never()).fetchPtr(any(), any(), any())
        verify(ocrProcessorService, never()).parsePdf(any())
        verify(houseLlmService, never()).process(any())
    }

    @Test
    fun `processFilingListRow should return null when download fails`() {
        // Given
        val docId = "20250101-001"
        val year = 2025
        val filingListRow = createFilingListRow(docId, year)

        whenever(housePtrFilingRepository.findByDocId(docId)).thenReturn(emptyList())
        whenever(houseFilingListService.getHouseFilingListRow(docId)).thenReturn(filingListRow)
        whenever(housePtrDownloadRepository.findByDocId(docId)).thenReturn(null)
        whenever(houseHttpClient.getPtrEtag(docId, year)).thenReturn("etag")
        whenever(houseHttpClient.fetchPtr(eq(docId), eq(year), any())).thenReturn(null)

        // When
        val result = housePtrService.processFilingListRow(filingListRow, force = false)

        // Then
        assertNull(result)
        verify(ocrProcessorService, never()).parsePdf(any())
        verify(houseLlmService, never()).process(any())
    }

    @Test
    fun `getTransactionsForYear should return all transactions for type P filings`() {
        // Given
        val year = 2025
        val docId1 = "20250101-001"
        val docId2 = "20250102-002"
        val docId3 = "20250103-003" // Type A filing, should be excluded

        val filingListRow1 = createFilingListRow(docId1, year, "P")
        val filingListRow2 = createFilingListRow(docId2, year, "P")
        val filingListRow3 = createFilingListRow(docId3, year, "A")

        whenever(houseFilingListService.getHouseFilingListRows(year)).thenReturn(
            listOf(filingListRow1, filingListRow2, filingListRow3),
        )

        val filing1 = createFiling(docId1, 1L, 1L)
        val filing2 = createFiling(docId2, 2L, 2L)

        whenever(housePtrFilingRepository.findByDocId(docId1)).thenReturn(listOf(filing1))
        whenever(housePtrFilingRepository.findByDocId(docId2)).thenReturn(listOf(filing2))
        whenever(housePtrFilingRepository.findByDocId(docId3)).thenReturn(emptyList())

        val transactions1 = listOf(createTransaction(docId1, 1L, 1L), createTransaction(docId1, 1L, 2L))
        val transactions2 = listOf(createTransaction(docId2, 2L, 3L))

        whenever(housePtrTransactionRepository.findByHousePtrFilingId(1L)).thenReturn(transactions1)
        whenever(housePtrTransactionRepository.findByHousePtrFilingId(2L)).thenReturn(transactions2)

        // When
        val result = housePtrService.getTransactionsForYear(year)

        // Then
        assertEquals(3, result.size)
        assertTrue(result.containsAll(transactions1 + transactions2))
        verify(housePtrFilingRepository, never()).findByDocId(docId3) // Type A should be skipped
    }

    @Test
    fun `getTransactionsForYear should return empty list when no filings exist`() {
        // Given
        val year = 2025
        whenever(houseFilingListService.getHouseFilingListRows(year)).thenReturn(emptyList())

        // When
        val result = housePtrService.getTransactionsForYear(year)

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getTransactionsForYear should handle multiple filings per docId`() {
        // Given
        val year = 2025
        val docId = "20250101-001"

        val filingListRow = createFilingListRow(docId, year, "P")
        whenever(houseFilingListService.getHouseFilingListRows(year)).thenReturn(listOf(filingListRow))

        // Two filings for the same docId (e.g., reprocessed)
        val filing1 = createFiling(docId, 1L, 1L)
        val filing2 = createFiling(docId, 2L, 1L)
        whenever(housePtrFilingRepository.findByDocId(docId)).thenReturn(listOf(filing1, filing2))

        val transactions1 = listOf(createTransaction(docId, 1L, 1L))
        val transactions2 = listOf(createTransaction(docId, 2L, 2L))

        whenever(housePtrTransactionRepository.findByHousePtrFilingId(1L)).thenReturn(transactions1)
        whenever(housePtrTransactionRepository.findByHousePtrFilingId(2L)).thenReturn(transactions2)

        // When
        val result = housePtrService.getTransactionsForYear(year)

        // Then
        assertEquals(2, result.size)
        assertTrue(result.containsAll(transactions1 + transactions2))
    }

    // Helper methods
    private fun createFilingListRow(
        docId: String,
        year: Int,
        filingType: String = "P",
    ) = HouseFilingListRow(
        id = 1L,
        docId = docId,
        prefix = "Hon.",
        last = "Doe",
        first = "John",
        suffix = "",
        filingType = filingType,
        stateDst = "CA-01",
        year = year,
        filingDate = LocalDate.of(year, 1, 1),
        downloaded = false,
        downloadedAt = null,
        rawRowData = "raw",
        createdAt = fixedInstant,
        houseFilingListId = 1L,
    )

    private fun createDownload(
        docId: String,
        year: Int,
        id: Long,
    ) = HousePtrDownload(
        id = id,
        docId = docId,
        gcsUri = "gs://insider-trading-analyzer/congress/house/$year/$docId.pdf",
        etag = "etag",
        parsed = false,
        parsedAt = null,
        createdAt = fixedInstant,
        updatedAt = null,
    )

    private fun createOcrResult(
        docId: String,
        year: Int,
        id: Long,
        downloadId: Long,
    ) = HousePtrOcrResult(
        id = id,
        docId = docId,
        housePtrDownloadId = downloadId,
        gcsUri = "gs://insider-trading-analyzer/congress/house/$year/$docId.txt",
    )

    private fun createFiling(
        docId: String,
        id: Long,
        ocrResultId: Long,
    ) = HousePtrFiling(
        id = id,
        housePtrOcrResultId = ocrResultId,
        docId = docId,
        rawLlmResponse = "{}",
        createdAt = fixedInstant,
    )

    private fun createTransaction(
        docId: String,
        filingId: Long,
        id: Long,
    ) = HousePtrTransaction(
        id = id,
        docId = docId,
        housePtrFilingId = filingId,
        owner = "SP",
        asset = "Test Asset",
        transactionType = "P",
        transactionDate = "01/15/2025",
        notificationDate = "01/20/2025",
        amount = "$1,001 - $15,000",
        certainty = 90,
        additionalData = JsonNodeFactory.instance.objectNode(),
    )
}
