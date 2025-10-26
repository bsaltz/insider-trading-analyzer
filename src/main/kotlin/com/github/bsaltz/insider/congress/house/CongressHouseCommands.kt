package com.github.bsaltz.insider.congress.house

import org.springframework.shell.command.annotation.Command
import java.time.LocalDate

@Command
class CongressHouseCommands(
    private val congressHouseFilingService: CongressHouseFilingService,
    private val congressHouseReportService: CongressHouseReportService,
) {
    /**
     * Update Congress House filings for a given year.
     *
     * This command will update filings for the specified year. Specifically, it will check for updates to the
     * disclosure list by retrieving the ETag header from the server. If it has changed, it will download the updated
     * disclosure list and parse it to extract the necessary information. Any new filings will be processed and stored
     * for further analysis.
     *
     * Note that this command will not check for updates to individual filing documents, only the disclosure list.
     *
     * @param year The year for which to update filings.
     */
    @Command
    fun congressHouseFilingUpdate(year: Int) {
        println("Congress House Update for year $year...")
        congressHouseFilingService.processFilingList(year)
        println("Congress House Update completed for year $year")
    }

    /**
     * Update Congress House reports for a given date range.
     *
     * This command will update reports for the specified date range. Specifically, it will check for updates to all
     * stored disclosure docs and process any changes by checking the ETag header of each document.
     *
     * @param startDate The start date for the report update, YYYY-MM-DD.
     * @param endDate The end date for the report update, YYYY-MM-DD.
     */
    @Command
    fun congressHouseReportUpdate(
        startDate: String,
        endDate: String,
    ) {
        println("Congress House Report Update for period $startDate to $endDate...")
        val start = LocalDate.parse(startDate)
        val end = LocalDate.parse(endDate)
        congressHouseReportService.processReportFiles(start, end)
        println("Congress House Report Update completed for period $start")
    }

    @Command
    fun congressHouseReportUpdateOne(documentId: String) {
        println("Congress House Report Update for document $documentId...")
        congressHouseReportService.processReportFile(documentId)
        println("Congress House Report Update completed for document $documentId")
    }
}
