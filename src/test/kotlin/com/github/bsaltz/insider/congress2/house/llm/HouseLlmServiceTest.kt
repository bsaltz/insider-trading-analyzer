package com.github.bsaltz.insider.congress2.house.llm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt

class HouseLlmServiceTest {
    private val chatModel = mock<ChatModel>()
    private val houseLlmService = HouseLlmService(chatModel)

    @Test
    fun `process should successfully parse OCR result and return LLM output`() {
        // Given
        val ocrParseResult =
            """
            Filing ID #20029060
            FILER INFORMATION
            Name: Hon. David J. Taylor
            Status: Member
            State: OH District: 02

            TRANSACTIONS
            ID | Owner | Asset | Type | Date | Notification Date | Amount
            1 | | Amazon.com, Inc. - Common Stock (AMZN) [ST] | P | 03/27/2025 | 03/31/2025 | ${'$'}1,001 - ${'$'}15,000
            """.trimIndent()

        val llmJsonResponse =
            """
            {
              "filingId": "20029060",
              "filer": {
                "name": "Hon. David J. Taylor",
                "status": "Member",
                "stateDistrict": "OH02"
              },
              "transactions": [
                {
                  "id": "",
                  "owner": "",
                  "asset": "Amazon.com, Inc. - Common Stock (AMZN) [ST]",
                  "transactionType": "P",
                  "date": "03/27/2025",
                  "notificationDate": "03/31/2025",
                  "amount": "${'$'}1,001 - ${'$'}15,000",
                  "filingStatus": "New",
                  "certainty": 85
                }
              ]
            }
            """.trimIndent()

        val chatResponse = createMockChatResponse(llmJsonResponse)
        whenever(chatModel.call(any<Prompt>())).thenReturn(chatResponse)

        // When
        val result = houseLlmService.process(ocrParseResult)

        // Then
        assertNotNull(result)
        val (output, rawResponse) = result
        assertEquals(llmJsonResponse, rawResponse)
        assertEquals("20029060", output.filingId)
        assertEquals("Hon. David J. Taylor", output.filer.name)
        assertEquals("Member", output.filer.status)
        assertEquals("OH02", output.filer.stateDistrict)
        assertEquals(1, output.transactions.size)

        val transaction = output.transactions[0]
        assertEquals("", transaction.id)
        assertEquals("", transaction.owner)
        assertEquals("Amazon.com, Inc. - Common Stock (AMZN) [ST]", transaction.asset)
        assertEquals("P", transaction.transactionType)
        assertEquals("03/27/2025", transaction.date)
        assertEquals("03/31/2025", transaction.notificationDate)
        assertEquals("$1,001 - $15,000", transaction.amount)
        assertEquals("New", transaction.filingStatus)
        assertEquals(85, transaction.certainty)

        // Verify ChatModel was called
        verify(chatModel).call(any<Prompt>())
    }

    @Test
    fun `process should handle multiple transactions`() {
        // Given
        val ocrParseResult = "Filing ID #20030000\nMultiple transactions..."

        val llmJsonResponse =
            """
            {
              "filingId": "20030000",
              "filer": {
                "name": "Hon. Jane Smith",
                "status": "Member",
                "stateDistrict": "CA01"
              },
              "transactions": [
                {
                  "id": "",
                  "owner": "SP",
                  "asset": "Apple Inc. (AAPL)",
                  "transactionType": "P",
                  "date": "01/15/2025",
                  "notificationDate": "01/20/2025",
                  "amount": "${'$'}15,001 - ${'$'}50,000",
                  "filingStatus": "New",
                  "certainty": 90
                },
                {
                  "id": "",
                  "owner": "DC",
                  "asset": "Microsoft Corporation (MSFT)",
                  "transactionType": "S",
                  "date": "01/16/2025",
                  "notificationDate": "01/21/2025",
                  "amount": "${'$'}1,001 - ${'$'}15,000",
                  "filingStatus": "New",
                  "certainty": 88
                },
                {
                  "id": "",
                  "owner": "JT",
                  "asset": "Tesla Inc. (TSLA)",
                  "transactionType": "P",
                  "date": "01/17/2025",
                  "notificationDate": "01/22/2025",
                  "amount": "${'$'}50,001 - ${'$'}100,000",
                  "filingStatus": "Amended",
                  "certainty": 75
                }
              ]
            }
            """.trimIndent()

        val chatResponse = createMockChatResponse(llmJsonResponse)
        whenever(chatModel.call(any<Prompt>())).thenReturn(chatResponse)

        // When
        val result = houseLlmService.process(ocrParseResult)

        // Then
        val (output, _) = result
        assertEquals(3, output.transactions.size)

        assertEquals("SP", output.transactions[0].owner)
        assertEquals("P", output.transactions[0].transactionType)
        assertEquals(90, output.transactions[0].certainty)

        assertEquals("DC", output.transactions[1].owner)
        assertEquals("S", output.transactions[1].transactionType)
        assertEquals(88, output.transactions[1].certainty)

        assertEquals("JT", output.transactions[2].owner)
        assertEquals("P", output.transactions[2].transactionType)
        assertEquals("Amended", output.transactions[2].filingStatus)
        assertEquals(75, output.transactions[2].certainty)
    }

    @Test
    fun `process should handle empty transactions list`() {
        // Given
        val ocrParseResult = "Filing with no transactions"

        val llmJsonResponse =
            """
            {
              "filingId": "20040000",
              "filer": {
                "name": "Hon. Empty Filing",
                "status": "Member",
                "stateDistrict": "TX05"
              },
              "transactions": []
            }
            """.trimIndent()

        val chatResponse = createMockChatResponse(llmJsonResponse)
        whenever(chatModel.call(any<Prompt>())).thenReturn(chatResponse)

        // When
        val result = houseLlmService.process(ocrParseResult)

        // Then
        val (output, _) = result
        assertEquals("20040000", output.filingId)
        assertEquals(0, output.transactions.size)
        assertTrue(output.transactions.isEmpty())
    }

    @Test
    fun `process should verify prompt contains OCR parse result`() {
        // Given
        val ocrParseResult = "Specific OCR content to verify"

        val llmJsonResponse =
            """
            {
              "filingId": "12345",
              "filer": {
                "name": "Test",
                "status": "Member",
                "stateDistrict": "AA00"
              },
              "transactions": []
            }
            """.trimIndent()

        val chatResponse = createMockChatResponse(llmJsonResponse)
        whenever(chatModel.call(any<Prompt>())).thenReturn(chatResponse)

        // When
        houseLlmService.process(ocrParseResult)

        // Then
        argumentCaptor<Prompt>().apply {
            verify(chatModel).call(capture())
            val prompt = firstValue
            val messages = prompt.instructions

            // Verify we have 2 messages (initial + reflection)
            assertEquals(2, messages.size)

            // Verify the first message contains the OCR result
            val firstMessage = messages[0].text
            assertTrue(firstMessage.contains("Specific OCR content to verify"))
            assertTrue(firstMessage.contains("Periodic Transaction Report"))
        }
    }

    @Test
    fun `process should include reflection prompt`() {
        // Given
        val ocrParseResult = "Test OCR"

        val llmJsonResponse =
            """
            {
              "filingId": "99999",
              "filer": {
                "name": "Test",
                "status": "Member",
                "stateDistrict": "AA00"
              },
              "transactions": []
            }
            """.trimIndent()

        val chatResponse = createMockChatResponse(llmJsonResponse)
        whenever(chatModel.call(any<Prompt>())).thenReturn(chatResponse)

        // When
        houseLlmService.process(ocrParseResult)

        // Then
        argumentCaptor<Prompt>().apply {
            verify(chatModel).call(capture())
            val prompt = firstValue
            val messages = prompt.instructions

            assertEquals(2, messages.size)

            // Verify the second message is the reflection prompt
            val reflectionMessage = messages[1].text
            assertTrue(reflectionMessage.contains("Reflect"))
            assertTrue(reflectionMessage.contains("certainty"))
        }
    }

    @Test
    fun `process should throw error when LLM returns empty text`() {
        // Given
        val ocrParseResult = "Test OCR"
        val chatResponse = createMockChatResponse("")
        whenever(chatModel.call(any<Prompt>())).thenReturn(chatResponse)

        // When & Then - Empty string causes JSON parsing to fail
        assertThrows(Exception::class.java) {
            houseLlmService.process(ocrParseResult)
        }
    }

    @Test
    fun `process should throw error when JSON conversion fails`() {
        // Given
        val ocrParseResult = "Test OCR"
        val invalidJsonResponse = "This is not valid JSON"
        val chatResponse = createMockChatResponse(invalidJsonResponse)
        whenever(chatModel.call(any<Prompt>())).thenReturn(chatResponse)

        // When & Then
        assertThrows(Exception::class.java) {
            houseLlmService.process(ocrParseResult)
        }
    }

    @Test
    fun `process should throw error when JSON is missing required fields`() {
        // Given
        val ocrParseResult = "Test OCR"
        val incompleteJsonResponse =
            """
            {
              "filingId": "12345"
            }
            """.trimIndent()
        val chatResponse = createMockChatResponse(incompleteJsonResponse)
        whenever(chatModel.call(any<Prompt>())).thenReturn(chatResponse)

        // When & Then
        assertThrows(Exception::class.java) {
            houseLlmService.process(ocrParseResult)
        }
    }

    @Test
    fun `process should propagate ChatModel exceptions`() {
        // Given
        val ocrParseResult = "Test OCR"
        whenever(chatModel.call(any<Prompt>()))
            .thenThrow(RuntimeException("LLM API error"))

        // When & Then
        assertThrows(RuntimeException::class.java) {
            houseLlmService.process(ocrParseResult)
        }
    }

    @Test
    fun `process should handle special characters in OCR result`() {
        // Given
        val ocrParseResult =
            """
            Filing with special chars: ${'$'}1,000 & "quotes" 'apostrophes'
            Symbols: @ # % ^ * ( ) - + = [ ] { } | \ / < >
            """.trimIndent()

        val llmJsonResponse =
            """
            {
              "filingId": "88888",
              "filer": {
                "name": "Special Char Test",
                "status": "Member",
                "stateDistrict": "NY01"
              },
              "transactions": []
            }
            """.trimIndent()

        val chatResponse = createMockChatResponse(llmJsonResponse)
        whenever(chatModel.call(any<Prompt>())).thenReturn(chatResponse)

        // When
        val result = houseLlmService.process(ocrParseResult)

        // Then
        val (output, _) = result
        assertEquals("88888", output.filingId)

        // Verify the prompt was created with the special characters
        argumentCaptor<Prompt>().apply {
            verify(chatModel).call(capture())
            val prompt = firstValue.instructions[0].text
            assertTrue(prompt.contains("$"))
            assertTrue(prompt.contains("&"))
        }
    }

    @Test
    fun `process should handle very long OCR result`() {
        // Given
        val longOcrResult = "Filing content\n".repeat(1000) + "End of filing"

        val llmJsonResponse =
            """
            {
              "filingId": "77777",
              "filer": {
                "name": "Long Filing Test",
                "status": "Member",
                "stateDistrict": "FL08"
              },
              "transactions": []
            }
            """.trimIndent()

        val chatResponse = createMockChatResponse(llmJsonResponse)
        whenever(chatModel.call(any<Prompt>())).thenReturn(chatResponse)

        // When
        val result = houseLlmService.process(longOcrResult)

        // Then
        val (output, _) = result
        assertEquals("77777", output.filingId)
    }

    private fun createMockChatResponse(text: String?): ChatResponse {
        val assistantMessage = AssistantMessage(text ?: "")
        val generation = Generation(assistantMessage)
        return ChatResponse(listOf(generation))
    }
}
