package com.github.bsaltz.insider.vision

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.temporal.ChronoUnit

@DataJdbcTest
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase
class OcrParseResultRepositoryTest(
    @param:Autowired
    private val ocrParseResultRepository: OcrParseResultRepository,
) {
    private val now = Instant.now().truncatedTo(ChronoUnit.MICROS)

    @Test
    fun `should save and retrieve OcrParseResult`() {
        // Given
        val ocrResult =
            OcrParseResult(
                response =
                    """
                    {
                        "responses": [
                            {
                                "fullTextAnnotation": {
                                    "text": "Filing ID #20032062\nFILER INFORMATION\nName: Hon. Robert B. Aderholt\nStatus: Member\nState: AL District: 04"
                                }
                            }
                        ]
                    }
                    """.trimIndent(),
                createdTime = now,
            )

        // When
        val savedResult = ocrParseResultRepository.save(ocrResult)

        // Then
        assertNotNull(savedResult.id)
        assertTrue(savedResult.response.contains("Filing ID #20032062"))
        assertTrue(savedResult.response.contains("Hon. Robert B. Aderholt"))
        assertTrue(savedResult.createdTime.isAfter(now.minusSeconds(1)))

        // When retrieving by ID
        val retrievedResult = ocrParseResultRepository.findById(savedResult.id!!)

        // Then
        assertTrue(retrievedResult.isPresent)
        val result = retrievedResult.get()
        assertEquals(savedResult.id, result.id)
        assertEquals(savedResult.response, result.response)
        assertEquals(savedResult.createdTime, result.createdTime)
    }

    @Test
    fun `should handle large JSON response text`() {
        // Given - simulate a large OCR response
        val largeResponse =
            """
            {
                "responses": [
                    {
                        "fullTextAnnotation": {
                            "text": "${"Large OCR response text ".repeat(1000)}"
                        }
                    }
                ]
            }
            """.trimIndent()

        val ocrResult =
            OcrParseResult(
                response = largeResponse,
                createdTime = Instant.now(),
            )

        // When
        val savedResult = ocrParseResultRepository.save(ocrResult)

        // Then
        assertNotNull(savedResult.id)
        assertEquals(largeResponse, savedResult.response)
        assertTrue(savedResult.response.length > 10000) // Verify it's actually large
    }

    @Test
    fun `should preserve timestamps correctly`() {
        // Given - create multiple results with different timestamps
        val time1 = Instant.parse("2025-01-01T10:00:00Z")
        val time2 = Instant.parse("2025-01-02T15:30:00Z")

        val result1 = OcrParseResult(response = "Response 1", createdTime = time1)
        val result2 = OcrParseResult(response = "Response 2", createdTime = time2)

        // When
        val saved1 = ocrParseResultRepository.save(result1)
        val saved2 = ocrParseResultRepository.save(result2)

        // Then
        assertEquals(time1, saved1.createdTime)
        assertEquals(time2, saved2.createdTime)
        assertNotEquals(saved1.id, saved2.id)

        // Verify we can retrieve both with correct timestamps
        val allResults = ocrParseResultRepository.findAll().toList()
        assertTrue(allResults.size >= 2)

        val timestamps = allResults.map { it.createdTime }
        assertTrue(timestamps.contains(time1))
        assertTrue(timestamps.contains(time2))
    }
}
