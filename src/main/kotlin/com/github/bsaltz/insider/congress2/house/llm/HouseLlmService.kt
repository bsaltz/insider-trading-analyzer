package com.github.bsaltz.insider.congress2.house.llm

import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.converter.BeanOutputConverter
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.shell.command.annotation.Command
import org.springframework.stereotype.Service

@Service
@Command
class HouseLlmService(
    private val chatModel: ChatModel,
) {
    private val initialPromptTemplate = """
        I pulled a Periodic Transaction Report (PTR) for a US House Representative and ran the PDF through GCP's Vision
        AI/OCR parser, and this was the result:

        ```text
        {{ocr_parse_result}}
        ```

        The document header contains the filing ID, filer name, filer status ("Member" or "Candidate"), and the
        state/district (AA00, where AA is the state and 00 is the district number).

        The document's main content is a table of security transactions with the columns "ID", "Owner", "Asset",
        "Transaction Type", "Date", "Notification Date", "Amount", and "Cap. Gains > $200?". Also, "Asset" can contain a
        number of subfields, including "Filing Status", "Description", and "Comments". Try to extract these values from
        the parse result. Here's a description of the fields you should extract and include in the output.

        * ID: Almost always blank, never set to null
        * Owner: Blank (owner is the specified member), SP (spouse), DC (dependent child), JT (joint)
        * Asset: Might be multiple lines, might also have the transaction type code accidentally included in it
        * Transaction Type: P (purchase), S (sale) - sometimes the P comes across as the Cyrillic P because of some OCR
          weirdness
        * Date: m/d/y
        * Notification Date: m/d/y
        * Amount: A range of dollars
        * Cap. Gains > $200?: Usually not picked up by OCR, so you can ignore it
        * Filing Status: New or Amended
        * Certainty: A score from 1 to 100 estimating confidence in the output.

        Convert any Cyrillic characters to the look-alike Latin character, e.g. the Cyrillic R looks like a P, so return
        the Latin P, not the Latin R. Do not wrap the output in Markdown syntax, just return the JSON.

        Example values in output. Note that none of these values are nullable, so make them empty instead of null.

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
              "amount": "$1,001 - $15,000",
              "filingStatus": "New",
              "certainty": 85
            }
          ]
        }
    """.trimIndent()
    private val reflectionPrompt = """
        Reflect on the input from OCR and your output and make corrections, including updating the certainty score if
        your confidence increases. Output the corrected JSON only.
    """.trimIndent()

    private val outputConverter = BeanOutputConverter(HouseLlmOutput::class.java)
    private val ollamaOptions: OllamaOptions = OllamaOptions.builder()
        .format(outputConverter.jsonSchemaMap)
        .build()

    fun process(ocrParseResult: String): HouseLlmOutput =
        outputConverter.convert(getChatResponseFromOcr(ocrParseResult))
            ?: error("Failed to convert LLM response to JSON")

    private fun getChatResponseFromOcr(ocrParseResult: String): String =
        chatModel.call(createPrompt(ocrParseResult))
            .result.output.text
            ?: error("No response from LLM")

    private fun createPrompt(ocrParseResult: String): Prompt =
        Prompt.builder()
            .messages(
                listOf(
                    UserMessage(initialPrompt(ocrParseResult)),
                    UserMessage(reflectionPrompt),
                )
            )
            .chatOptions(ollamaOptions)
            .build()

    private fun initialPrompt(ocrParseResult: String): String =
        initialPromptTemplate.replace("{{ocr_parse_result}}", ocrParseResult)
}

data class HouseLlmOutput(
    val filingId: String,
    val filer: HouseLlmFiler,
    val transactions: List<HouseLlmTransaction>,
)

data class HouseLlmFiler(
    val name: String,
    val status: String,
    val stateDistrict: String,
)

data class HouseLlmTransaction(
    val id: String,
    val owner: String,
    val asset: String,
    val transactionType: String,
    val date: String,
    val notificationDate: String,
    val amount: String,
    val filingStatus: String,
    val certainty: Int,
)
