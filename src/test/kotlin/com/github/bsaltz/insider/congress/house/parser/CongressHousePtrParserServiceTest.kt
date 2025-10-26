package com.github.bsaltz.insider.congress.house.parser

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.bsaltz.insider.congress.house.model.AmountRange
import com.github.bsaltz.insider.congress.house.model.FilerStatus
import com.github.bsaltz.insider.congress.house.model.FilingStatus
import com.github.bsaltz.insider.congress.house.model.Ownership
import com.github.bsaltz.insider.congress.house.model.TradeType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import java.time.LocalDate

class CongressHousePtrParserServiceTest {
    private val objectMapper = ObjectMapper()
    private val parserService = CongressHousePtrParserService(objectMapper, java.time.Clock.systemDefaultZone())

    @Test
    fun `should parse sample OCR output 20032062 correctly`() {
        // Load the sample JSON file
        val jsonResource = ClassPathResource("fixtures/congress/house/samples_congress_house_20032062_output-1-to-1.json")
        val jsonContent = jsonResource.inputStream.bufferedReader().use { it.readText() }
        val sourceUrl = "https://test-source-url.com/sample.pdf"

        // Parse the JSON
        val result = parserService.parseFromJson(jsonContent, sourceUrl)

        // Verify the parsed report
        assertTrue(result.isSuccess)
        val data = result.getDataOrNull()!!
        assertEquals("20032062", data.docId)
        assertEquals("Hon. Robert B. Aderholt", data.filerFullName)
        assertEquals(FilerStatus.MEMBER, data.filerStatus)
        assertEquals("AL", data.state)
        assertEquals(4, data.district)
        assertEquals(sourceUrl, data.fileSourceUrl)

        // Verify transactions
        assertEquals(1, data.transactions.size)

        val transaction = data.transactions[0]
        assertEquals(null, transaction.owner)
        assertEquals("GSK plc American Depositary Shares (GSK)", transaction.assetName)
        assertEquals("ST", transaction.assetType)
        assertEquals(FilingStatus.NEW, transaction.filingStatus)
        assertEquals(TradeType.SALE, transaction.tradeType)
        assertEquals(AmountRange.A_1001_15000, transaction.amountRange)
        assertEquals(LocalDate.of(2025, 7, 28), transaction.tradeDate)
        assertEquals(sourceUrl, transaction.fileSourceUrl)
        assertEquals(LocalDate.now(), transaction.parsedDate)
    }

    @Test
    fun `should return error when document ID cannot be extracted`() {
        val invalidJson =
            """
            {
                "responses": [
                    {
                        "fullTextAnnotation": {
                            "text": "Invalid document without Filing ID"
                        }
                    }
                ]
            }
            """.trimIndent()

        val result = parserService.parseFromJson(invalidJson, "test-url")
        assertTrue(result.isError)
        assertTrue(result.errors().isNotEmpty())
    }

    @Test
    fun `should return error when FILER INFORMATION block cannot be extracted`() {
        val invalidJson =
            """
            {
                "responses": [
                    {
                        "fullTextAnnotation": {
                            "text": "Filing ID #12345\nSome document without proper FILER INFORMATION block"
                        }
                    }
                ]
            }
            """.trimIndent()

        val result = parserService.parseFromJson(invalidJson, "test-url")
        assertTrue(result.isError)
        assertTrue(result.errors().isNotEmpty())
    }

    @Test
    fun `should return error when FILER INFORMATION block has invalid format`() {
        val invalidJson =
            """
            {
                "responses": [
                    {
                        "fullTextAnnotation": {
                            "text": "Filing ID #12345\nFILER INFORMATION\nName: John Doe\nStatus: Member\nInvalid state format"
                        }
                    }
                ]
            }
            """.trimIndent()

        val result = parserService.parseFromJson(invalidJson, "test-url")
        assertTrue(result.isError)
        assertTrue(result.errors().isNotEmpty())
    }

    @Test
    fun `should parse sample OCR output 20026650 with spouse ownership correctly`() {
        // Load the sample JSON file
        val jsonResource = ClassPathResource("fixtures/congress/house/samples_congress_house_20026650_output-1-to-1.json")
        val jsonContent = jsonResource.inputStream.bufferedReader().use { it.readText() }
        val sourceUrl = "https://test-source-url.com/20026650.pdf"

        // Parse the JSON
        val result = parserService.parseFromJson(jsonContent, sourceUrl)

        // Verify the parsed report
        assertTrue(result.isSuccess)
        val data = result.getDataOrNull()!!
        assertEquals("20026650", data.docId)
        assertEquals("Hon. Robert E. Latta", data.filerFullName)
        assertEquals(FilerStatus.MEMBER, data.filerStatus)
        assertEquals("OH", data.state)
        assertEquals(5, data.district)
        assertEquals(sourceUrl, data.fileSourceUrl)

        // Verify transactions - should now find FMAO transaction
        assertEquals(1, data.transactions.size)

        val transaction = data.transactions[0]
        assertEquals(Ownership.SP, transaction.owner)
        assertEquals("Farmers & Merchants Bancorp, Inc. (FMAO)", transaction.assetName)
        assertEquals("ST", transaction.assetType)
        assertEquals(FilingStatus.NEW, transaction.filingStatus)
        assertEquals(TradeType.PURCHASE, transaction.tradeType)
        assertEquals(AmountRange.A_1001_15000, transaction.amountRange)
        assertEquals(LocalDate.of(2025, 1, 20), transaction.tradeDate)
        assertEquals(sourceUrl, transaction.fileSourceUrl)
    }

    @Test
    fun `should parse sample OCR output 20029060 with multiple transactions correctly`() {
        // Load the sample JSON file
        val jsonResource = ClassPathResource("fixtures/congress/house/samples_congress_house_20029060_output-1-to-2.json")
        val jsonContent = jsonResource.inputStream.bufferedReader().use { it.readText() }
        val sourceUrl = "https://test-source-url.com/20029060.pdf"

        // Parse the JSON - this will likely fail due to hardcoded parsing
        val result = parserService.parseFromJson(jsonContent, sourceUrl)

        // Verify the parsed report
        assertTrue(result.isSuccess)
        val data = result.getDataOrNull()!!
        assertEquals("20029060", data.docId)
        assertEquals("Hon. David J. Taylor", data.filerFullName)
        assertEquals(FilerStatus.MEMBER, data.filerStatus)
        assertEquals("OH", data.state)
        assertEquals(2, data.district)
        assertEquals(sourceUrl, data.fileSourceUrl)

        // Verify transactions - should now find multiple transactions (6 based on OCR)
        assertTrue(data.transactions.size >= 6, "Should find at least 6 transactions")

        // Check first transaction as example
        val firstTransaction = data.transactions[0]
        assertEquals("Amazon.com, Inc. - Common Stock (AMZN)", firstTransaction.assetName)
        assertEquals("ST", firstTransaction.assetType)
        assertEquals(FilingStatus.NEW, firstTransaction.filingStatus)
        assertEquals(TradeType.PURCHASE, firstTransaction.tradeType)
        assertEquals(AmountRange.A_1001_15000, firstTransaction.amountRange)
        assertEquals(LocalDate.of(2025, 3, 27), firstTransaction.tradeDate)
        assertEquals(sourceUrl, firstTransaction.fileSourceUrl)
    }

    @Test
    fun `should parse FILER INFORMATION but find no transactions for complex document`() {
        // This test shows FILER INFORMATION parsing works but transaction parsing is still limited
        val jsonResource = ClassPathResource("fixtures/congress/house/samples_congress_house_20029060_output-1-to-2.json")
        val jsonContent = jsonResource.inputStream.bufferedReader().use { it.readText() }
        val sourceUrl = "https://test-source-url.com/20029060.pdf"

        val result = parserService.parseFromJson(jsonContent, sourceUrl)

        // FILER INFORMATION parsing should work now
        assertTrue(result.isSuccess)
        val data = result.getDataOrNull()!!
        assertEquals("20029060", data.docId)
        assertEquals("Hon. David J. Taylor", data.filerFullName)
        assertEquals(FilerStatus.MEMBER, data.filerStatus)
        assertEquals("OH", data.state)
        assertEquals(2, data.district)

        // Should now find multiple transactions with the new parser
        assertTrue(data.transactions.size >= 6, "Should find multiple transactions")
    }
}
