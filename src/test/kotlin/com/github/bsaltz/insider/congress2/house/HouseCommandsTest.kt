package com.github.bsaltz.insider.congress2.house

import com.github.bsaltz.insider.congress2.house.filinglist.HouseFilingList
import com.github.bsaltz.insider.congress2.house.filinglist.HouseFilingListRow
import com.github.bsaltz.insider.congress2.house.filinglist.HouseFilingListService
import com.github.bsaltz.insider.congress2.house.ptr.HousePtrDownload
import com.github.bsaltz.insider.congress2.house.ptr.HousePtrFiling
import com.github.bsaltz.insider.congress2.house.ptr.HousePtrService
import com.github.bsaltz.insider.congress2.house.ptr.HousePtrStats
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
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
}
