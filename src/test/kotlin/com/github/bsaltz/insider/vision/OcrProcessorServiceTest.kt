package com.github.bsaltz.insider.vision

import com.google.cloud.spring.storage.GoogleStorageLocation
import com.google.cloud.spring.storage.GoogleStorageResource
import com.google.cloud.spring.vision.CloudVisionTemplate
import com.google.cloud.storage.Storage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class OcrProcessorServiceTest {
    private val storage = mock<Storage>()
    private val cloudVisionTemplate = mock<CloudVisionTemplate>()
    private val ocrParseResultRepository = mock<OcrParseResultRepository>()
    private val fixedInstant = Instant.parse("2025-09-26T10:30:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private val ocrProcessorService =
        OcrProcessorService(
            storage = storage,
            cloudVisionTemplate = cloudVisionTemplate,
            ocrParseResultRepository = ocrParseResultRepository,
            clock = clock,
        )

    @Test
    fun `parsePdf should extract text and save OCR result`() {
        // Given
        val pdfLocation = GoogleStorageLocation.forFile("test-bucket", "test-file.pdf")
        val extractedTextLines =
            listOf(
                "Filing ID #20032062",
                "FILER INFORMATION",
                "Name: Hon. Robert B. Aderholt",
                "Status: Member",
                "State: AL District: 04",
            )
        val expectedJoinedText = extractedTextLines.joinToString("\n")

        val expectedOcrResult =
            OcrParseResult(
                id = 123L,
                response = expectedJoinedText,
                createdTime = fixedInstant,
            )

        // Mock the CloudVisionTemplate to return extracted text lines
        whenever(cloudVisionTemplate.extractTextFromPdf(any<GoogleStorageResource>()))
            .thenReturn(extractedTextLines)

        // Mock the repository save operation
        whenever(ocrParseResultRepository.save(any<OcrParseResult>()))
            .thenReturn(expectedOcrResult)

        // When
        val result = ocrProcessorService.parsePdf(pdfLocation)

        // Then
        assertEquals(expectedOcrResult, result)
        assertEquals(123L, result.id)
        assertEquals(expectedJoinedText, result.response)
        assertEquals(fixedInstant, result.createdTime)

        // Verify CloudVisionTemplate was called
        verify(cloudVisionTemplate).extractTextFromPdf(any<GoogleStorageResource>())

        // Verify repository save was called
        verify(ocrParseResultRepository).save(any<OcrParseResult>())
    }

    @Test
    fun `parsePdf should handle single line text extraction`() {
        // Given
        val pdfLocation = GoogleStorageLocation.forFile("test-bucket", "single-line.pdf")
        val extractedTextLines = listOf("Single line of extracted text")
        val expectedText = "Single line of extracted text"

        val expectedOcrResult =
            OcrParseResult(
                id = 456L,
                response = expectedText,
                createdTime = fixedInstant,
            )

        whenever(cloudVisionTemplate.extractTextFromPdf(any<GoogleStorageResource>()))
            .thenReturn(extractedTextLines)
        whenever(ocrParseResultRepository.save(any<OcrParseResult>()))
            .thenReturn(expectedOcrResult)

        // When
        val result = ocrProcessorService.parsePdf(pdfLocation)

        // Then
        assertEquals(expectedText, result.response)
        assertEquals(456L, result.id)
    }

    @Test
    fun `parsePdf should handle empty text extraction`() {
        // Given
        val pdfLocation = GoogleStorageLocation.forFile("test-bucket", "empty.pdf")
        val extractedTextLines = emptyList<String>()
        val expectedText = ""

        val expectedOcrResult =
            OcrParseResult(
                id = 789L,
                response = expectedText,
                createdTime = fixedInstant,
            )

        whenever(cloudVisionTemplate.extractTextFromPdf(any<GoogleStorageResource>()))
            .thenReturn(extractedTextLines)
        whenever(ocrParseResultRepository.save(any<OcrParseResult>()))
            .thenReturn(expectedOcrResult)

        // When
        val result = ocrProcessorService.parsePdf(pdfLocation)

        // Then
        assertEquals("", result.response)
        assertEquals(789L, result.id)
    }

    @Test
    fun `parsePdf should handle multiline text with proper joining`() {
        // Given
        val pdfLocation = GoogleStorageLocation.forFile("complex-bucket", "multiline.pdf")
        val extractedTextLines =
            listOf(
                "Line 1",
                "Line 2",
                "", // Empty line
                "Line 4",
                "Line 5",
            )
        val expectedText = "Line 1\nLine 2\n\nLine 4\nLine 5"

        val expectedOcrResult =
            OcrParseResult(
                id = 999L,
                response = expectedText,
                createdTime = fixedInstant,
            )

        whenever(cloudVisionTemplate.extractTextFromPdf(any<GoogleStorageResource>()))
            .thenReturn(extractedTextLines)
        whenever(ocrParseResultRepository.save(any<OcrParseResult>()))
            .thenReturn(expectedOcrResult)

        // When
        val result = ocrProcessorService.parsePdf(pdfLocation)

        // Then
        assertEquals(expectedText, result.response)
        assertTrue(result.response.contains("\n\n")) // Verify empty lines are preserved
    }

    @Test
    fun `parsePdf should use correct clock for timestamp`() {
        // Given
        val pdfLocation = GoogleStorageLocation.forFile("time-test", "test.pdf")
        val extractedTextLines = listOf("Test content")

        val expectedOcrResult =
            OcrParseResult(
                id = 111L,
                response = "Test content",
                createdTime = fixedInstant,
            )

        whenever(cloudVisionTemplate.extractTextFromPdf(any<GoogleStorageResource>()))
            .thenReturn(extractedTextLines)
        whenever(ocrParseResultRepository.save(any<OcrParseResult>()))
            .thenReturn(expectedOcrResult)

        // When
        val result = ocrProcessorService.parsePdf(pdfLocation)

        // Then
        assertEquals(fixedInstant, result.createdTime)

        // Verify the saved entity had the correct timestamp
        argumentCaptor<OcrParseResult>().apply {
            verify(ocrParseResultRepository).save(capture())
            assertEquals(fixedInstant, firstValue.createdTime)
        }
    }

    @Test
    fun `parsePdf should propagate CloudVisionTemplate exceptions`() {
        // Given
        val pdfLocation = GoogleStorageLocation.forFile("error-test", "error.pdf")

        whenever(cloudVisionTemplate.extractTextFromPdf(any<GoogleStorageResource>()))
            .thenThrow(RuntimeException("Vision API error"))

        // When & Then
        assertThrows(RuntimeException::class.java) {
            ocrProcessorService.parsePdf(pdfLocation)
        }

        // Verify repository was never called
        verify(ocrParseResultRepository, never()).save(any<OcrParseResult>())
    }

    @Test
    fun `parsePdf should propagate repository save exceptions`() {
        // Given
        val pdfLocation = GoogleStorageLocation.forFile("save-error-test", "test.pdf")
        val extractedTextLines = listOf("Test content")

        whenever(cloudVisionTemplate.extractTextFromPdf(any<GoogleStorageResource>()))
            .thenReturn(extractedTextLines)
        whenever(ocrParseResultRepository.save(any<OcrParseResult>()))
            .thenThrow(RuntimeException("Database error"))

        // When & Then
        assertThrows(RuntimeException::class.java) {
            ocrProcessorService.parsePdf(pdfLocation)
        }

        // Verify CloudVisionTemplate was still called
        verify(cloudVisionTemplate).extractTextFromPdf(any<GoogleStorageResource>())
    }
}
