package com.github.bsaltz.insider.congress2.house

import com.github.bsaltz.insider.congress2.house.filinglist.HouseFilingListService
import com.github.bsaltz.insider.congress2.house.ptr.HousePtrService
import org.springframework.shell.command.CommandRegistration
import org.springframework.shell.command.annotation.Command
import org.springframework.shell.command.annotation.Option
import java.time.Clock

/**
 * Commands for processing Congress House filings.
 *
 * Commands supported are:
 *
 * ```text
 * # End-to-end processing
 * congress house process-all [--years <year-list>]
 * congress house process-year <year>
 * congress house process-filing <doc-id>
 *
 * # Individual operations (for debugging, reprocessing, or manual intervention)
 * congress house download-tsv <year>
 * congress house download-pdf <doc-id>
 * congress house ocr <doc-id>
 * congress house parse <doc-id>
 *
 * # Utility/maintenance commands
 * congress house clear-cache <doc-id>  # Clear the cached etag for the given document id
 * congress house refresh-etags [--year <year>]  # Check all etags without downloading
 * congress house stats [--year <year>]  # Show processing stats
 * ```
 *
 * Process commands cover the main workflows:
 *
 * * `process-all` - End-to-end for all years (or specified years)
 * * `process-year` - Batch processing for a specific year
 * * `process-filing` - Single filing from start to finish
 *
 * Individual operation commands run single-op execution paths:
 *
 * - `download-tsv` - Only fetch, cache, and parse the TSV for a year
 * - `download-pdf` - Only fetch and cache a specific PDF
 * - `ocr` - Only run OCR on an already-downloaded PDF
 * - `parse` - Only run LLM parsing on already-OCR'd text
 *
 * Utility commands for operational needs:
 *
 * - `clear-cache` - Invalidate cache data for a specific PDF
 * - `refresh-etags` - Validate cache without heavy processing
 * - `stats` - See what's been processed, success rates, etc.
 *
 * @param clock The clock to use for determining the current year.
 */
@Command(
    command = ["congress", "house"],
    group = "US Congress - House",
)
class HouseCommands(
    private val houseFilingListService: HouseFilingListService,
    private val housePtrService: HousePtrService,
    private val clock: Clock,
) {
    @Command(
        command = ["process-all"],
        description = "End-to-end processing for all years (or specified years). Downloads TSV data, fetches PDFs, runs OCR, and parses transactions.",
    )
    fun processAll(
        @Option(
            longNames = ["--years"],
            shortNames = ['y'],
            description = "The years to process data for (default is the current year)",
            required = false,
            arity = CommandRegistration.OptionArity.ZERO_OR_MORE,
        )
        years: List<Int>?,
    ) {
        val years = years ?: listOf(clock.instant().atZone(clock.zone).year)
        require(years.all { it >= 2008 }) { "Years must be 2008 or later" }
        require(years.all { it <= clock.instant().atZone(clock.zone).year }) { "Years cannot be in the future" }
        println("Processing all data for years: $years")
        years.forEach { processYear(it) }
        println("Processing completed for all years: $years")
    }

    @Command(
        command = ["process-year"],
        description = "Batch processing for a specific year. Downloads TSV data, fetches all PDFs for the year, runs OCR, and parses transactions.",
    )
    fun processYear(year: Int) {
        require(year >= 2008) { "Year must be 2008 or later" }
        require(year <= clock.instant().atZone(clock.zone).year) { "Year cannot be in the future" }
        println("Processing data for year $year")
        val filingList = houseFilingListService.processYear(year)
        val filingListId = filingList.id ?: error("Filing list ID is null")
        val filingListRows = houseFilingListService.getHouseFilingListRows(filingListId)
        filingListRows.forEach { housePtrService.processFilingListRow(it) }
        println("Processing completed for year $year")
    }

    @Command(
        command = ["process-filing"],
        description = "Single filing processing from start to finish. Downloads PDF, runs OCR, and parses transactions for the specified document ID.",
    )
    fun processFiling(docId: String) {
        require(docId.isNotBlank()) { "Document ID cannot be blank" }
        println("Processing filing $docId")
        housePtrService.processFilingListRow(docId, true)
        println("Processing of filing $docId completed")
    }

    @Command(
        command = ["download-tsv"],
        description = "Download, cache, and parse the TSV filing list for a specific year without processing individual filings.",
    )
    fun downloadTsv(year: Int) {
        require(year >= 2008) { "Year must be 2008 or later" }
        require(year <= clock.instant().atZone(clock.zone).year) { "Year cannot be in the future" }
        println("Downloading TSV for year $year")
        val houseFilingList = houseFilingListService.processYear(year)
        println("TSV download and parsing completed for year $year: $houseFilingList")
    }

    @Command(
        command = ["download-pdf"],
        description = "Download and cache a specific PDF filing by document ID without further processing.",
    )
    fun downloadPdf(docId: String) {
        require(docId.isNotBlank()) { "Document ID cannot be blank" }
        println("Downloading PDF for filing $docId")
        housePtrService.downloadPdf(docId, true)
        println("PDF download and parsing completed for filing $docId")
    }

    @Command(
        command = ["run-ocr"],
        description = "Run OCR processing on an already-downloaded PDF filing using Google Cloud Vision API.",
    )
    fun ocr(docId: String) {
        require(docId.isNotBlank()) { "Document ID cannot be blank" }
        println("Running OCR for filing $docId")
        housePtrService.runOcr(docId, true)
        println("OCR completed for filing $docId")
    }

    @Command(
        command = ["parse"],
        description = "Run LLM parsing on already-OCR'd text to extract structured transaction data from a filing.",
    )
    fun parse(docId: String) {
        require(docId.isNotBlank()) { "Document ID cannot be blank" }
        println("Parsing filing $docId")
        housePtrService.parseFiling(docId, true)
        println("Parsing completed for filing $docId")
    }

    @Command(
        command = ["clear-cache"],
        description = "Clear the cached ETag for a specific document ID to force re-download on next processing.",
    )
    fun clearCache(docId: String) {
        require(docId.isNotBlank()) { "Document ID cannot be blank" }
        println("Clearing cache for filing $docId")
        TODO()
    }

    @Command(
        command = ["refresh-etags"],
        description = "Check all ETags without downloading to validate cache status for a specific year.",
    )
    fun refreshEtags(
        @Option(
            longNames = ["--year"],
            shortNames = ['y'],
            description = "The year to refresh etags for (default is the current year)",
            required = false,
        )
        year: Int?,
    ) {
        val year = year ?: clock.instant().atZone(clock.zone).year
        require(year >= 2008) { "Year must be 2008 or later" }
        require(year <= clock.instant().atZone(clock.zone).year) { "Year cannot be in the future" }
        println("Refreshing etags for year $year")
        TODO()
    }

    @Command(
        command = ["stats"],
        description = "Show processing statistics including success rates and what's been processed for a specific year.",
    )
    fun stats(
        @Option(
            longNames = ["--year"],
            shortNames = ['y'],
            description = "The year to refresh etags for (default is the current year)",
            required = false,
        )
        year: Int?,
    ) {
        val year = year ?: clock.instant().atZone(clock.zone).year
        require(year >= 2008) { "Year must be 2008 or later" }
        require(year <= clock.instant().atZone(clock.zone).year) { "Year cannot be in the future" }
        println("Stats for year $year")
        TODO()
    }
}