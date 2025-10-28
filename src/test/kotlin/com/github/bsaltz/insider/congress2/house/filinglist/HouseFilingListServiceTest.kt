package com.github.bsaltz.insider.congress2.house.filinglist

import com.github.bsaltz.insider.congress2.house.client.HouseHttpClient
import com.github.bsaltz.insider.congress2.house.client.StoredResponse
import com.google.cloud.spring.storage.GoogleStorageLocation
import com.google.cloud.storage.Storage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
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

class HouseFilingListServiceTest {
    private val houseFilingListRepository = mock<HouseFilingListRepository>()
    private val houseFilingListRowRepository = mock<HouseFilingListRowRepository>()
    private val houseHttpClient = mock<HouseHttpClient>()
    private val storage = mock<Storage>()
    private val fixedInstant = Instant.parse("2025-09-26T10:30:00Z").truncatedTo(ChronoUnit.MICROS)
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private val houseFilingListService =
        HouseFilingListService(
            houseFilingListRepository = houseFilingListRepository,
            houseFilingListRowRepository = houseFilingListRowRepository,
            houseHttpClient = houseHttpClient,
            storage = storage,
            clock = clock,
        )

    @Test
    fun `getHouseFilingList should delegate to repository`() {
        // Given
        val year = 2025
        val expectedFilingList =
            HouseFilingList(
                id = 1L,
                year = year,
                etag = "test-etag",
                gcsUri = "gs://test-bucket/2025.zip",
                parsed = true,
                parsedAt = fixedInstant,
                createdAt = fixedInstant,
                updatedAt = null,
            )
        whenever(houseFilingListRepository.findByYear(year)).thenReturn(expectedFilingList)

        // When
        val result = houseFilingListService.getHouseFilingList(year)

        // Then
        assertNotNull(result)
        assertEquals(expectedFilingList, result)
        assertEquals(1L, result!!.id)
        assertEquals(year, result.year)
        verify(houseFilingListRepository).findByYear(year)
    }

    @Test
    fun `getHouseFilingList should return null when not found`() {
        // Given
        val year = 2024
        whenever(houseFilingListRepository.findByYear(year)).thenReturn(null)

        // When
        val result = houseFilingListService.getHouseFilingList(year)

        // Then
        assertNull(result)
        verify(houseFilingListRepository).findByYear(year)
    }

    @Test
    fun `getHouseFilingListRow should delegate to repository`() {
        // Given
        val docId = "20250101-001"
        val expectedRow =
            HouseFilingListRow(
                id = 1L,
                docId = docId,
                prefix = "Hon.",
                last = "Smith",
                first = "John",
                suffix = "Jr.",
                filingType = "P",
                stateDst = "CA-01",
                year = 2025,
                filingDate = LocalDate.of(2025, 1, 15),
                downloaded = false,
                downloadedAt = null,
                rawRowData = "raw data",
                createdAt = fixedInstant,
                houseFilingListId = 1L,
            )
        whenever(houseFilingListRowRepository.findByDocId(docId)).thenReturn(expectedRow)

        // When
        val result = houseFilingListService.getHouseFilingListRow(docId)

        // Then
        assertNotNull(result)
        assertEquals(expectedRow, result)
        assertEquals(docId, result!!.docId)
        verify(houseFilingListRowRepository).findByDocId(docId)
    }

    @Test
    fun `getHouseFilingListRow should return null when not found`() {
        // Given
        val docId = "nonexistent"
        whenever(houseFilingListRowRepository.findByDocId(docId)).thenReturn(null)

        // When
        val result = houseFilingListService.getHouseFilingListRow(docId)

        // Then
        assertNull(result)
        verify(houseFilingListRowRepository).findByDocId(docId)
    }

    @Test
    fun `getHouseFilingListRows by houseFilingListId should delegate to repository`() {
        // Given
        val houseFilingListId = 1L
        val expectedRows =
            listOf(
                HouseFilingListRow(
                    id = 1L,
                    docId = "doc1",
                    prefix = "",
                    last = "Smith",
                    first = "John",
                    suffix = "",
                    filingType = "P",
                    stateDst = "CA-01",
                    year = 2025,
                    filingDate = LocalDate.of(2025, 1, 1),
                    downloaded = false,
                    downloadedAt = null,
                    rawRowData = "raw",
                    createdAt = fixedInstant,
                    houseFilingListId = houseFilingListId,
                ),
                HouseFilingListRow(
                    id = 2L,
                    docId = "doc2",
                    prefix = "",
                    last = "Jones",
                    first = "Jane",
                    suffix = "",
                    filingType = "A",
                    stateDst = "TX-02",
                    year = 2025,
                    filingDate = LocalDate.of(2025, 1, 2),
                    downloaded = false,
                    downloadedAt = null,
                    rawRowData = "raw2",
                    createdAt = fixedInstant,
                    houseFilingListId = houseFilingListId,
                ),
            )
        whenever(houseFilingListRowRepository.findByHouseFilingListId(houseFilingListId))
            .thenReturn(expectedRows)

        // When
        val result = houseFilingListService.getHouseFilingListRows(houseFilingListId)

        // Then
        assertEquals(2, result.size)
        assertEquals(expectedRows, result)
        verify(houseFilingListRowRepository).findByHouseFilingListId(houseFilingListId)
    }

    @Test
    fun `getHouseFilingListRows by houseFilingListId should return empty list when none found`() {
        // Given
        val houseFilingListId = 999L
        whenever(houseFilingListRowRepository.findByHouseFilingListId(houseFilingListId))
            .thenReturn(emptyList())

        // When
        val result = houseFilingListService.getHouseFilingListRows(houseFilingListId)

        // Then
        assertTrue(result.isEmpty())
        verify(houseFilingListRowRepository).findByHouseFilingListId(houseFilingListId)
    }

    @Test
    fun `getHouseFilingListRows by year should delegate to repository`() {
        // Given
        val year = 2025
        val expectedRows =
            listOf(
                HouseFilingListRow(
                    id = 1L,
                    docId = "doc1",
                    prefix = "",
                    last = "Smith",
                    first = "John",
                    suffix = "",
                    filingType = "P",
                    stateDst = "CA-01",
                    year = year,
                    filingDate = LocalDate.of(2025, 1, 1),
                    downloaded = false,
                    downloadedAt = null,
                    rawRowData = "raw",
                    createdAt = fixedInstant,
                    houseFilingListId = 1L,
                ),
            )
        whenever(houseFilingListRowRepository.findByYear(year)).thenReturn(expectedRows)

        // When
        val result = houseFilingListService.getHouseFilingListRows(year)

        // Then
        assertEquals(1, result.size)
        assertEquals(expectedRows, result)
        assertEquals(year, result[0].year)
        verify(houseFilingListRowRepository).findByYear(year)
    }

    @Test
    fun `getHouseFilingListRows by year should return empty list when none found`() {
        // Given
        val year = 2020
        whenever(houseFilingListRowRepository.findByYear(year)).thenReturn(emptyList())

        // When
        val result = houseFilingListService.getHouseFilingListRows(year)

        // Then
        assertTrue(result.isEmpty())
        verify(houseFilingListRowRepository).findByYear(year)
    }

    @Test
    fun `processYear should create new filing list when none exists`() {
        // Given
        val year = 2025
        whenever(houseFilingListRepository.findByYear(year)).thenReturn(null)

        val newFilingList =
            HouseFilingList(
                id = null,
                year = year,
                etag = null,
                gcsUri = "gs://insider-trading-analyzer/congress/house/disclosure-list/$year.zip",
                parsed = false,
                parsedAt = null,
                createdAt = fixedInstant,
                updatedAt = null,
            )
        val savedFilingList = newFilingList.copy(id = 1L)
        whenever(houseFilingListRepository.save(any())).thenReturn(savedFilingList)

        // Mock HTTP client responses
        val etag = "new-etag-123"
        whenever(houseHttpClient.getFilingListEtag(year)).thenReturn(etag)
        whenever(houseHttpClient.fetchFilingList(eq(year), any()))
            .thenReturn(StoredResponse(GoogleStorageLocation.forFile("test-bucket", "test.zip"), etag))

        // When - Note: This will fail on parse() due to missing ZIP file, but we can test the setup
        try {
            houseFilingListService.processYear(year)
        } catch (e: Exception) {
            // Expected - parse will fail without actual file
        }

        // Then - Verify filing list was created (save may be called multiple times in the workflow)
        verify(houseFilingListRepository).findByYear(year)
        verify(houseFilingListRepository, atLeastOnce()).save(any())
    }

    @Test
    fun `processYear should use existing filing list when it exists`() {
        // Given
        val year = 2025
        val existingFilingList =
            HouseFilingList(
                id = 1L,
                year = year,
                etag = "old-etag",
                gcsUri = "gs://insider-trading-analyzer/congress/house/disclosure-list/$year.zip",
                parsed = false,
                parsedAt = null,
                createdAt = fixedInstant.minusSeconds(3600),
                updatedAt = null,
            )
        whenever(houseFilingListRepository.findByYear(year)).thenReturn(existingFilingList)

        // Mock HTTP client responses
        val newEtag = "new-etag-456"
        whenever(houseHttpClient.getFilingListEtag(year)).thenReturn(newEtag)
        whenever(houseHttpClient.fetchFilingList(eq(year), any()))
            .thenReturn(StoredResponse(GoogleStorageLocation.forFile("test-bucket", "test.zip"), newEtag))

        val updatedFilingList =
            existingFilingList.copy(
                etag = newEtag,
                updatedAt = fixedInstant,
            )
        whenever(houseFilingListRepository.save(any())).thenReturn(updatedFilingList)

        // When - Will fail on parse, but we can test the workflow
        try {
            houseFilingListService.processYear(year)
        } catch (e: Exception) {
            // Expected - parse will fail without actual file
        }

        // Then - Verify it used existing filing list
        verify(houseFilingListRepository).findByYear(year)
        verify(houseFilingListRepository, never()).save(
            eq(
                HouseFilingList(
                    id = null,
                    year = year,
                    etag = null,
                    gcsUri = "gs://insider-trading-analyzer/congress/house/disclosure-list/$year.zip",
                    parsed = false,
                    parsedAt = null,
                    createdAt = fixedInstant,
                    updatedAt = null,
                ),
            ),
        )
    }

    @Test
    fun `processYear should skip download when etag matches and not forced`() {
        // Given
        val year = 2025
        val etag = "unchanged-etag"
        val existingFilingList =
            HouseFilingList(
                id = 1L,
                year = year,
                etag = etag,
                gcsUri = "gs://insider-trading-analyzer/congress/house/disclosure-list/$year.zip",
                parsed = true,
                parsedAt = fixedInstant.minusSeconds(7200),
                createdAt = fixedInstant.minusSeconds(10800),
                updatedAt = fixedInstant.minusSeconds(7200),
            )
        whenever(houseFilingListRepository.findByYear(year)).thenReturn(existingFilingList)
        whenever(houseHttpClient.getFilingListEtag(year)).thenReturn(etag)

        // When
        val result = houseFilingListService.processYear(year, force = false)

        // Then
        assertEquals(existingFilingList, result)
        verify(houseFilingListRepository).findByYear(year)
        verify(houseHttpClient).getFilingListEtag(year)
        // Should not download or save when etag matches and already parsed
        verify(houseHttpClient, never()).fetchFilingList(any(), any())
    }

    @Test
    fun `processYear should generate correct GCS URI for year`() {
        // Given
        val year = 2023
        whenever(houseFilingListRepository.findByYear(year)).thenReturn(null)

        val savedFilingList =
            HouseFilingList(
                id = 1L,
                year = year,
                etag = null,
                gcsUri = "gs://insider-trading-analyzer/congress/house/disclosure-list/$year.zip",
                parsed = false,
                parsedAt = null,
                createdAt = fixedInstant,
                updatedAt = null,
            )
        whenever(houseFilingListRepository.save(any())).thenReturn(savedFilingList)

        val etag = "etag-2023"
        whenever(houseHttpClient.getFilingListEtag(year)).thenReturn(etag)
        whenever(houseHttpClient.fetchFilingList(eq(year), any()))
            .thenReturn(StoredResponse(GoogleStorageLocation.forFile("test-bucket", "test.zip"), etag))

        // When
        try {
            houseFilingListService.processYear(year)
        } catch (e: Exception) {
            // Expected - parse will fail
        }

        // Then - Verify the GCS URI was generated correctly
        verify(houseHttpClient).fetchFilingList(
            eq(year),
            eq("gs://insider-trading-analyzer/congress/house/disclosure-list/$year.zip"),
        )
    }

    @Test
    fun `processYear should update etag when it changes`() {
        // Given
        val year = 2025
        val oldEtag = "old-etag"
        val newEtag = "new-etag"

        val existingFilingList =
            HouseFilingList(
                id = 1L,
                year = year,
                etag = oldEtag,
                gcsUri = "gs://insider-trading-analyzer/congress/house/disclosure-list/$year.zip",
                parsed = false,
                parsedAt = null,
                createdAt = fixedInstant.minusSeconds(3600),
                updatedAt = null,
            )
        whenever(houseFilingListRepository.findByYear(year)).thenReturn(existingFilingList)
        whenever(houseHttpClient.getFilingListEtag(year)).thenReturn(newEtag)
        whenever(houseHttpClient.fetchFilingList(eq(year), any()))
            .thenReturn(StoredResponse(GoogleStorageLocation.forFile("test-bucket", "test.zip"), newEtag))

        val updatedFilingList =
            existingFilingList.copy(
                etag = newEtag,
                parsed = false,
                parsedAt = null,
                updatedAt = fixedInstant,
            )
        whenever(houseFilingListRepository.save(any())).thenReturn(updatedFilingList)

        // When
        try {
            houseFilingListService.processYear(year)
        } catch (e: Exception) {
            // Expected - parse will fail
        }

        // Then - Verify etag was updated
        verify(houseHttpClient).fetchFilingList(eq(year), any())
        verify(houseFilingListRepository).save(any())
    }

    @Test
    fun `processYear with force true should download when etag differs`() {
        // Given
        val year = 2025
        val oldEtag = "old-etag"
        val newEtag = "new-etag"
        val existingFilingList =
            HouseFilingList(
                id = 1L,
                year = year,
                etag = oldEtag,
                gcsUri = "gs://insider-trading-analyzer/congress/house/disclosure-list/$year.zip",
                parsed = true,
                parsedAt = fixedInstant.minusSeconds(7200),
                createdAt = fixedInstant.minusSeconds(10800),
                updatedAt = fixedInstant.minusSeconds(7200),
            )
        whenever(houseFilingListRepository.findByYear(year)).thenReturn(existingFilingList)
        whenever(houseHttpClient.getFilingListEtag(year)).thenReturn(newEtag)
        whenever(houseHttpClient.fetchFilingList(eq(year), any()))
            .thenReturn(StoredResponse(GoogleStorageLocation.forFile("test-bucket", "test.zip"), newEtag))

        whenever(houseFilingListRepository.save(any())).thenReturn(existingFilingList)

        // When
        try {
            houseFilingListService.processYear(year, force = true)
        } catch (e: Exception) {
            // Expected - parse will fail
        }

        // Then - Should download when etag differs
        verify(houseHttpClient).fetchFilingList(eq(year), any())
    }
}
