package com.github.bsaltz.insider.congress2.house

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.github.bsaltz.insider.congress2.house.filinglist.HouseFilingList
import com.github.bsaltz.insider.congress2.house.filinglist.HouseFilingListRow
import com.github.bsaltz.insider.congress2.house.filinglist.HouseFilingListService
import com.github.bsaltz.insider.congress2.house.ptr.HousePtrDownload
import com.github.bsaltz.insider.congress2.house.ptr.HousePtrFiling
import com.github.bsaltz.insider.congress2.house.ptr.HousePtrService
import com.github.bsaltz.insider.congress2.house.ptr.HousePtrStats
import com.github.bsaltz.insider.congress2.house.ptr.HousePtrTransaction
import com.github.bsaltz.insider.congress2.house.ptr.HousePtrTransactionWithRepresentative
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class HouseCommandsTest {
    private val houseFilingListService = mock<HouseFilingListService>()
    private val housePtrService = mock<HousePtrService>()
    private val fixedInstant = Instant.parse("2025-09-26T10:30:00Z").truncatedTo(ChronoUnit.MICROS)
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private val houseCommands =
        HouseCommands(
            houseFilingListService = houseFilingListService,
            housePtrService = housePtrService,
            clock = clock,
        )

    @Test
    fun `processAll should process current year by default`() {
        // Given
        val currentYear = clock.instant().atZone(clock.zone).year
        val filingList = createFilingList(currentYear, 1L)
        val filingListRows = listOf(createFilingListRow("doc1", currentYear, "P"))

        whenever(houseFilingListService.processYear(currentYear)).thenReturn(filingList)
        whenever(houseFilingListService.getHouseFilingListRows(1L)).thenReturn(filingListRows)
        whenever(housePtrService.processFilingListRow(any<HouseFilingListRow>(), eq(false))).thenReturn(createFiling("doc1", 1L))

        // When
        houseCommands.processAll(null)

        // Then
        verify(houseFilingListService).processYear(currentYear)
        verify(housePtrService).processFilingListRow(any<HouseFilingListRow>(), eq(false))
    }

    @Test
    fun `processAll should process specified years`() {
        // Given
        val years = listOf(2023, 2024)
        val filingList2023 = createFilingList(2023, 1L)
        val filingList2024 = createFilingList(2024, 2L)
        val filingListRows2023 = listOf(createFilingListRow("doc1", 2023, "P"))
        val filingListRows2024 = listOf(createFilingListRow("doc2", 2024, "P"))

        whenever(houseFilingListService.processYear(2023)).thenReturn(filingList2023)
        whenever(houseFilingListService.processYear(2024)).thenReturn(filingList2024)
        whenever(houseFilingListService.getHouseFilingListRows(1L)).thenReturn(filingListRows2023)
        whenever(houseFilingListService.getHouseFilingListRows(2L)).thenReturn(filingListRows2024)
        whenever(housePtrService.processFilingListRow(any<HouseFilingListRow>(), eq(false))).thenReturn(createFiling("doc1", 1L))

        // When
        houseCommands.processAll(years)

        // Then
        verify(houseFilingListService).processYear(2023)
        verify(houseFilingListService).processYear(2024)
    }

    @Test
    fun `processAll should reject years before 2008`() {
        // Given
        val years = listOf(2007)

        // When & Then
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                houseCommands.processAll(years)
            }
        assertEquals("Years must be 2008 or later", exception.message)
    }

    @Test
    fun `processAll should reject future years`() {
        // Given
        val currentYear = clock.instant().atZone(clock.zone).year
        val years = listOf(currentYear + 1)

        // When & Then
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                houseCommands.processAll(years)
            }
        assertEquals("Years cannot be in the future", exception.message)
    }

    @Test
    fun `processYear should process all type P filings for the year`() {
        // Given
        val year = 2025
        val filingList = createFilingList(year, 1L)
        val filingListRows =
            listOf(
                createFilingListRow("doc1", year, "P"),
                createFilingListRow("doc2", year, "P"),
                createFilingListRow("doc3", year, "A"), // Should be filtered out
            )

        whenever(houseFilingListService.processYear(year)).thenReturn(filingList)
        whenever(houseFilingListService.getHouseFilingListRows(1L)).thenReturn(filingListRows)
        whenever(housePtrService.processFilingListRow(any<HouseFilingListRow>(), eq(false)))
            .thenReturn(createFiling("doc1", 1L), createFiling("doc2", 1L))

        // When
        houseCommands.processYear(year)

        // Then
        verify(houseFilingListService).processYear(year)
        verify(houseFilingListService).getHouseFilingListRows(1L)
        verify(housePtrService).processFilingListRow(filingListRows[0])
        verify(housePtrService).processFilingListRow(filingListRows[1])
    }

    @Test
    fun `processYear should handle failures gracefully`() {
        // Given
        val year = 2025
        val filingList = createFilingList(year, 1L)
        val filingListRows =
            listOf(
                createFilingListRow("doc1", year, "P"),
                createFilingListRow("doc2", year, "P"),
            )

        whenever(houseFilingListService.processYear(year)).thenReturn(filingList)
        whenever(houseFilingListService.getHouseFilingListRows(1L)).thenReturn(filingListRows)
        whenever(housePtrService.processFilingListRow(filingListRows[0])).thenReturn(createFiling("doc1", 1L))
        whenever(housePtrService.processFilingListRow(filingListRows[1])).thenReturn(null) // Failure

        // When
        houseCommands.processYear(year)

        // Then - Should continue processing despite failure
        verify(housePtrService).processFilingListRow(filingListRows[0])
        verify(housePtrService).processFilingListRow(filingListRows[1])
    }

    @Test
    fun `processYear should process filings in descending order by filing date`() {
        // Given
        val year = 2025
        val filingList = createFilingList(year, 1L)
        val oldFiling = createFilingListRow("doc1", year, "P", LocalDate.of(year, 1, 15))
        val recentFiling = createFilingListRow("doc2", year, "P", LocalDate.of(year, 3, 10))
        val midFiling = createFilingListRow("doc3", year, "P", LocalDate.of(year, 2, 20))
        val filingListRows = listOf(oldFiling, recentFiling, midFiling) // Unsorted order

        whenever(houseFilingListService.processYear(year)).thenReturn(filingList)
        whenever(houseFilingListService.getHouseFilingListRows(1L)).thenReturn(filingListRows)
        whenever(housePtrService.processFilingListRow(any<HouseFilingListRow>(), eq(false)))
            .thenReturn(createFiling("doc", 1L))

        // Capture the order of processing
        val processingOrder = mutableListOf<String>()
        whenever(housePtrService.processFilingListRow(any<HouseFilingListRow>(), eq(false))).thenAnswer { invocation ->
            val row = invocation.getArgument<HouseFilingListRow>(0)
            processingOrder.add(row.docId)
            createFiling(row.docId, 1L)
        }

        // When
        houseCommands.processYear(year)

        // Then - Should process in descending order by filing date (most recent first)
        assertEquals(listOf("doc2", "doc3", "doc1"), processingOrder)
    }

    @Test
    fun `processYear should reject years before 2008`() {
        // When & Then
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                houseCommands.processYear(2007)
            }
        assertEquals("Year must be 2008 or later", exception.message)
    }

    @Test
    fun `processYear should reject future years`() {
        // Given
        val currentYear = clock.instant().atZone(clock.zone).year

        // When & Then
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                houseCommands.processYear(currentYear + 1)
            }
        assertEquals("Year cannot be in the future", exception.message)
    }

    @Test
    fun `processFiling should process single filing with force true`() {
        // Given
        val docId = "20250101-001"
        val filing = createFiling(docId, 1L)

        whenever(housePtrService.processFilingListRow(docId, true)).thenReturn(filing)

        // When
        houseCommands.processFiling(docId)

        // Then
        verify(housePtrService).processFilingListRow(docId, true)
    }

    @Test
    fun `processFiling should handle null result`() {
        // Given
        val docId = "20250101-001"

        whenever(housePtrService.processFilingListRow(docId, true)).thenReturn(null)

        // When
        houseCommands.processFiling(docId)

        // Then
        verify(housePtrService).processFilingListRow(docId, true)
    }

    @Test
    fun `processFiling should reject blank docId`() {
        // When & Then
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                houseCommands.processFiling("")
            }
        assertEquals("Document ID cannot be blank", exception.message)
    }

    @Test
    fun `downloadTsv should delegate to service`() {
        // Given
        val year = 2025
        val filingList = createFilingList(year, 1L)

        whenever(houseFilingListService.processYear(year)).thenReturn(filingList)

        // When
        houseCommands.downloadTsv(year)

        // Then
        verify(houseFilingListService).processYear(year)
    }

    @Test
    fun `downloadTsv should reject years before 2008`() {
        // When & Then
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                houseCommands.downloadTsv(2007)
            }
        assertEquals("Year must be 2008 or later", exception.message)
    }

    @Test
    fun `downloadTsv should reject future years`() {
        // Given
        val currentYear = clock.instant().atZone(clock.zone).year

        // When & Then
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                houseCommands.downloadTsv(currentYear + 1)
            }
        assertEquals("Year cannot be in the future", exception.message)
    }

    @Test
    fun `downloadPdf should delegate to service with force true`() {
        // Given
        val docId = "20250101-001"
        val download =
            HousePtrDownload(
                id = 1L,
                docId = docId,
                gcsUri = "gs://test/doc.pdf",
                etag = "etag",
                parsed = false,
                parsedAt = null,
                createdAt = fixedInstant,
                updatedAt = null,
            )

        whenever(housePtrService.downloadPdf(docId, true)).thenReturn(download)

        // When
        houseCommands.downloadPdf(docId)

        // Then
        verify(housePtrService).downloadPdf(docId, true)
    }

    @Test
    fun `downloadPdf should handle null result`() {
        // Given
        val docId = "20250101-001"

        whenever(housePtrService.downloadPdf(docId, true)).thenReturn(null)

        // When
        houseCommands.downloadPdf(docId)

        // Then
        verify(housePtrService).downloadPdf(docId, true)
    }

    @Test
    fun `downloadPdf should reject blank docId`() {
        // When & Then
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                houseCommands.downloadPdf("")
            }
        assertEquals("Document ID cannot be blank", exception.message)
    }

    @Test
    fun `ocr should delegate to service with force true`() {
        // Given
        val docId = "20250101-001"

        // When
        houseCommands.ocr(docId)

        // Then
        verify(housePtrService).runOcr(docId, true)
    }

    @Test
    fun `ocr should reject blank docId`() {
        // When & Then
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                houseCommands.ocr("")
            }
        assertEquals("Document ID cannot be blank", exception.message)
    }

    @Test
    fun `parse should delegate to service with force true`() {
        // Given
        val docId = "20250101-001"

        // When
        houseCommands.parse(docId)

        // Then
        verify(housePtrService).parseFiling(docId, true)
    }

    @Test
    fun `parse should reject blank docId`() {
        // When & Then
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                houseCommands.parse("")
            }
        assertEquals("Document ID cannot be blank", exception.message)
    }

    @Test
    fun `stats should delegate to service with current year by default`() {
        // Given
        val currentYear = clock.instant().atZone(clock.zone).year
        val stats =
            HousePtrStats(
                year = currentYear,
                totalFilingListRows = 100,
                typePFilingListRows = 80,
                downloadsCompleted = 70,
                ocrResultsCompleted = 60,
                filingsCompleted = 50,
                transactionsExtracted = 200,
            )

        whenever(housePtrService.getStats(currentYear)).thenReturn(stats)

        // When
        houseCommands.stats(null)

        // Then
        verify(housePtrService).getStats(currentYear)
    }

    @Test
    fun `stats should delegate to service with specified year`() {
        // Given
        val year = 2024
        val stats =
            HousePtrStats(
                year = year,
                totalFilingListRows = 100,
                typePFilingListRows = 80,
                downloadsCompleted = 70,
                ocrResultsCompleted = 60,
                filingsCompleted = 50,
                transactionsExtracted = 200,
            )

        whenever(housePtrService.getStats(year)).thenReturn(stats)

        // When
        houseCommands.stats(year)

        // Then
        verify(housePtrService).getStats(year)
    }

    @Test
    fun `stats should reject years before 2008`() {
        // When & Then
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                houseCommands.stats(2007)
            }
        assertEquals("Year must be 2008 or later", exception.message)
    }

    @Test
    fun `stats should reject future years`() {
        // Given
        val currentYear = clock.instant().atZone(clock.zone).year

        // When & Then
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                houseCommands.stats(currentYear + 1)
            }
        assertEquals("Year cannot be in the future", exception.message)
    }

    @Test
    fun `export should write transactions to CSV with current year by default`(
        @TempDir tempDir: Path,
    ) {
        // Given
        val currentYear = clock.instant().atZone(clock.zone).year
        val outputFile = tempDir.resolve("transactions.csv").toFile()
        val transactionsWithReps =
            listOf(
                createTransactionWithRep(
                    "doc1",
                    1L,
                    "SP",
                    "Apple Inc. (AAPL)",
                    "P",
                    "01/15/2025",
                    "01/20/2025",
                    "\$1,001 - \$15,000",
                    90,
                    "Hon.",
                    "Nancy",
                    "Pelosi",
                    "",
                    "CA-11",
                ),
                createTransactionWithRep(
                    "doc2",
                    2L,
                    "DC",
                    "Microsoft Corp (MSFT)",
                    "S",
                    "02/10/2025",
                    "02/15/2025",
                    "\$15,001 - \$50,000",
                    85,
                    "Rep.",
                    "Kevin",
                    "McCarthy",
                    "",
                    "CA-20",
                ),
            )

        whenever(housePtrService.getTransactionsWithRepresentativeForYear(currentYear)).thenReturn(transactionsWithReps)

        // When
        houseCommands.export(outputFile.absolutePath, null)

        // Then
        verify(housePtrService).getTransactionsWithRepresentativeForYear(currentYear)
        assertTrue(outputFile.exists())

        val lines = outputFile.readLines()
        assertEquals(3, lines.size) // Header + 2 transactions
        assertEquals(
            "doc_id,filing_date,representative_name,state_district,owner,asset,transaction_type,transaction_date,notification_date,amount,certainty",
            lines[0],
        )
        assertEquals("doc1,2024-01-15,Hon. Nancy Pelosi,CA-11,SP,Apple Inc. (AAPL),P,01/15/2025,01/20/2025,\$1,001 - \$15,000,90", lines[1])
        assertEquals(
            "doc2,2024-01-15,Rep. Kevin McCarthy,CA-20,DC,Microsoft Corp (MSFT),S,02/10/2025,02/15/2025,\$15,001 - \$50,000,85",
            lines[2],
        )
    }

    @Test
    fun `export should write transactions to CSV with specified year`(
        @TempDir tempDir: Path,
    ) {
        // Given
        val year = 2024
        val outputFile = tempDir.resolve("transactions.csv").toFile()
        val transactionsWithReps =
            listOf(
                createTransactionWithRep("doc1", 1L, "SP", "Test Asset", "P", "01/15/2024", "01/20/2024", "\$1,001 - \$15,000", 90),
            )

        whenever(housePtrService.getTransactionsWithRepresentativeForYear(year)).thenReturn(transactionsWithReps)

        // When
        houseCommands.export(outputFile.absolutePath, year)

        // Then
        verify(housePtrService).getTransactionsWithRepresentativeForYear(year)
        assertTrue(outputFile.exists())
    }

    @Test
    fun `export should handle empty transaction list`(
        @TempDir tempDir: Path,
    ) {
        // Given
        val year = 2024
        val outputFile = tempDir.resolve("transactions.csv").toFile()

        whenever(housePtrService.getTransactionsWithRepresentativeForYear(year)).thenReturn(emptyList())

        // When
        houseCommands.export(outputFile.absolutePath, year)

        // Then
        verify(housePtrService).getTransactionsWithRepresentativeForYear(year)
        // File should not be created when no transactions
        assertTrue(!outputFile.exists())
    }

    @Test
    fun `export should escape CSV fields with commas`(
        @TempDir tempDir: Path,
    ) {
        // Given
        val year = 2024
        val outputFile = tempDir.resolve("transactions.csv").toFile()
        val transactionsWithReps =
            listOf(
                createTransactionWithRep("doc1", 1L, "SP", "Company, Inc.", "P", "01/15/2024", "01/20/2024", "\$1,001 - \$15,000", 90),
            )

        whenever(housePtrService.getTransactionsWithRepresentativeForYear(year)).thenReturn(transactionsWithReps)

        // When
        houseCommands.export(outputFile.absolutePath, year)

        // Then
        val lines = outputFile.readLines()
        assertEquals("doc1,2024-01-15,Hon. John Doe,CA-01,SP,\"Company, Inc.\",P,01/15/2024,01/20/2024,\$1,001 - \$15,000,90", lines[1])
    }

    @Test
    fun `export should escape CSV fields with quotes`(
        @TempDir tempDir: Path,
    ) {
        // Given
        val year = 2024
        val outputFile = tempDir.resolve("transactions.csv").toFile()
        val transactionsWithReps =
            listOf(
                createTransactionWithRep(
                    "doc1",
                    1L,
                    "SP",
                    "Company \"Best\" Inc.",
                    "P",
                    "01/15/2024",
                    "01/20/2024",
                    "\$1,001 - \$15,000",
                    90,
                ),
            )

        whenever(housePtrService.getTransactionsWithRepresentativeForYear(year)).thenReturn(transactionsWithReps)

        // When
        houseCommands.export(outputFile.absolutePath, year)

        // Then
        val lines = outputFile.readLines()
        assertEquals(
            "doc1,2024-01-15,Hon. John Doe,CA-01,SP,\"Company \"\"Best\"\" Inc.\",P,01/15/2024,01/20/2024,\$1,001 - \$15,000,90",
            lines[1],
        )
    }

    @Test
    fun `export should reject years before 2008`(
        @TempDir tempDir: Path,
    ) {
        // Given
        val outputFile = tempDir.resolve("transactions.csv").toFile()

        // When & Then
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                houseCommands.export(outputFile.absolutePath, 2007)
            }
        assertEquals("Year must be 2008 or later", exception.message)
    }

    @Test
    fun `export should reject future years`(
        @TempDir tempDir: Path,
    ) {
        // Given
        val currentYear = clock.instant().atZone(clock.zone).year
        val outputFile = tempDir.resolve("transactions.csv").toFile()

        // When & Then
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                houseCommands.export(outputFile.absolutePath, currentYear + 1)
            }
        assertEquals("Year cannot be in the future", exception.message)
    }

    // Helper methods
    private fun createFilingList(
        year: Int,
        id: Long,
    ) = HouseFilingList(
        id = id,
        year = year,
        etag = "etag",
        gcsUri = "gs://test/$year.zip",
        parsed = true,
        parsedAt = fixedInstant,
        createdAt = fixedInstant,
        updatedAt = null,
    )

    private fun createFilingListRow(
        docId: String,
        year: Int,
        filingType: String,
        filingDate: LocalDate = LocalDate.of(year, 1, 1),
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
        filingDate = filingDate,
        downloaded = false,
        downloadedAt = null,
        rawRowData = "raw",
        createdAt = fixedInstant,
        houseFilingListId = 1L,
    )

    private fun createFiling(
        docId: String,
        id: Long,
    ) = HousePtrFiling(
        id = id,
        housePtrOcrResultId = 1L,
        docId = docId,
        rawLlmResponse = "{}",
        createdAt = fixedInstant,
    )

    private fun createTransaction(
        docId: String,
        filingId: Long,
        owner: String,
        asset: String,
        transactionType: String,
        transactionDate: String,
        notificationDate: String,
        amount: String,
        certainty: Int,
    ) = HousePtrTransaction(
        id = 1L,
        docId = docId,
        housePtrFilingId = filingId,
        owner = owner,
        asset = asset,
        transactionType = transactionType,
        transactionDate = transactionDate,
        notificationDate = notificationDate,
        amount = amount,
        certainty = certainty,
        additionalData = JsonNodeFactory.instance.objectNode(),
    )

    private fun createTransactionWithRep(
        docId: String,
        filingId: Long,
        owner: String,
        asset: String,
        transactionType: String,
        transactionDate: String,
        notificationDate: String,
        amount: String,
        certainty: Int,
        prefix: String = "Hon.",
        firstName: String = "John",
        lastName: String = "Doe",
        suffix: String = "",
        stateDst: String = "CA-01",
        filingDate: LocalDate = LocalDate.parse("2024-01-15"),
    ) = HousePtrTransactionWithRepresentative(
        transaction =
            HousePtrTransaction(
                id = 1L,
                docId = docId,
                housePtrFilingId = filingId,
                owner = owner,
                asset = asset,
                transactionType = transactionType,
                transactionDate = transactionDate,
                notificationDate = notificationDate,
                amount = amount,
                certainty = certainty,
                additionalData = JsonNodeFactory.instance.objectNode(),
            ),
        prefix = prefix,
        firstName = firstName,
        lastName = lastName,
        suffix = suffix,
        stateDst = stateDst,
        filingDate = filingDate,
    )
}
