package com.github.bsaltz.insider.congress.house

import com.github.bsaltz.insider.congress.house.model.CongressHouseFiling
import com.github.bsaltz.insider.congress.house.model.CongressHouseFilingRepository
import com.github.bsaltz.insider.congress.house.model.ParseIssueRepository
import com.github.bsaltz.insider.congress.house.model.PeriodicTransactionReportRepository
import com.github.bsaltz.insider.congress.house.model.PeriodicTransactionReportTransactionRepository
import com.github.bsaltz.insider.congress.house.parser.CongressHousePtrParserService
import com.github.bsaltz.insider.vision.OcrProcessorService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class CongressHouseReportService(
    private val congressHouseFilingRepository: CongressHouseFilingRepository,
    private val congressHouseHttpClient: CongressHouseHttpClient,
    private val ocrProcessorService: OcrProcessorService,
    private val congressHousePtrParserService: CongressHousePtrParserService,
    private val periodicTransactionReportRepository: PeriodicTransactionReportRepository,
    private val periodicTransactionReportTransactionRepository: PeriodicTransactionReportTransactionRepository,
    private val parseIssueRepository: ParseIssueRepository,
) {
    @Transactional
    fun processReportFiles(
        startDate: LocalDate,
        endDate: LocalDate,
    ) {
        val filings = congressHouseFilingRepository.findByFilingDateBetween(startDate, endDate)
        println("Found ${filings.size} filings in date range")
        processReportFiles(filings)
    }

    @Transactional
    fun processReportFile(documentId: String) {
        val filing = congressHouseFilingRepository.findOneByDocId(documentId)
        if (filing != null) {
            processReportFiles(listOf(filing))
        } else {
            println("Could not find filing with document ID $documentId")
        }
    }

    @Transactional
    fun processReportFiles(filings: List<CongressHouseFiling>) {
        var processedCount = 0
        var updatedCount = 0

        filings.forEach { filing ->
            try {
                processedCount++
                println("Processing filing $processedCount/${filings.size}: ${filing.docId} (${filing.filingDate})")

                // Check if the document's ETag has changed
                val currentEtag = congressHouseHttpClient.getDisclosureDocEtag(filing.docId, filing.year)

                if (currentEtag != null && currentEtag != filing.etag) {
                    println("  ETag changed for ${filing.docId}: ${filing.etag} -> $currentEtag")
                    updatedCount++

                    // Fetch the updated document
                    val storedResponse = congressHouseHttpClient.fetchDisclosureDoc(filing.docId, filing.year)

                    // Process with OCR
                    val ocrResult = ocrProcessorService.parsePdf(storedResponse.googleStorageLocation)

                    // Parse the OCR result
                    val parseResult =
                        congressHousePtrParserService.parseFromJson(
                            ocrResult.response,
                            storedResponse.googleStorageLocation.toString(),
                        )

                    // Handle the parse result
                    when {
                        parseResult.isSuccess -> {
                            val ptrData = parseResult.getDataOrNull()!!
                            // Check if PTR already exists
                            val existingPtr = periodicTransactionReportRepository.findByDocId(ptrData.docId)

                            if (existingPtr == null) {
                                // Save new PTR
                                val savedPtr = periodicTransactionReportRepository.save(ptrData.toPtrEntity())
                                println("  Saved new PTR for ${filing.docId}")

                                // Save transactions
                                ptrData.transactions.forEach { transaction ->
                                    periodicTransactionReportTransactionRepository.save(
                                        transaction.toTransactionEntity(savedPtr.id!!),
                                    )
                                }
                                println("  Saved ${ptrData.transactions.size} transactions for ${filing.docId}")
                            } else {
                                println("  PTR already exists for ${filing.docId}, skipping save")
                            }

                            if (parseResult.hasWarnings) {
                                println("  Parse completed with ${parseResult.warnings().size} warnings")
                            }
                        }
                        parseResult.isError -> {
                            println("  Parse failed with ${parseResult.errors().size} errors")
                            parseResult.errors().forEach { error ->
                                println("    ${error.severity}: ${error.message}")
                            }
                        }
                    }

                    // Save all parse issues (both warnings and errors) to database
                    // Now references congress_house_filing which always exists
                    val allIssues = parseResult.getAllIssues()
                    if (allIssues.isNotEmpty()) {
                        allIssues.forEach { issue ->
                            parseIssueRepository.save(issue.copy(docId = filing.docId))
                        }
                        println("  Saved ${allIssues.size} parse issues to database")
                    }

                    // Update the filing's ETag
                    congressHouseFilingRepository.save(filing.copy(etag = currentEtag))
                } else if (currentEtag == filing.etag) {
                    println("  No changes for ${filing.docId}")
                } else {
                    println("  Could not retrieve ETag for ${filing.docId}")
                }
            } catch (e: Exception) {
                println("ERROR: Failed to process filing ${filing.docId}: ${e.message}")
                println("  Exception type: ${e.javaClass.simpleName}")
                e.printStackTrace()
                println("  Continuing with next filing...")
            }
        }

        println("Report processing completed: $processedCount processed, $updatedCount updated")
    }
}
