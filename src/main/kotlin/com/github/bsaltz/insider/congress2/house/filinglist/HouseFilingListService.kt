package com.github.bsaltz.insider.congress2.house.filinglist

import com.github.bsaltz.insider.congress2.house.client.HouseHttpClient
import com.github.bsaltz.insider.utils.ListUtil.component6
import com.github.bsaltz.insider.utils.ListUtil.component7
import com.github.bsaltz.insider.utils.ListUtil.component8
import com.github.bsaltz.insider.utils.ListUtil.component9
import com.github.bsaltz.insider.utils.StorageUtil.getResource
import com.google.cloud.storage.Storage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class HouseFilingListService(
    private val houseFilingListRepository: HouseFilingListRepository,
    private val houseFilingListRowRepository: HouseFilingListRowRepository,
    private val houseHttpClient: HouseHttpClient,
    private val storage: Storage,
    private val clock: Clock,
) {
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d/yyyy")
    private val expectedHeader = "Prefix\tLast\tFirst\tSuffix\tFilingType\tStateDst\tYear\tFilingDate\tDocID"

    fun getHouseFilingList(year: Int): HouseFilingList? =
        houseFilingListRepository.findByYear(year)
    fun getHouseFilingListRow(docId: String): HouseFilingListRow? =
        houseFilingListRowRepository.findByDocId(docId)
    fun getHouseFilingListRows(houseFilingListId: Long): List<HouseFilingListRow> =
        houseFilingListRowRepository.findByHouseFilingListId(houseFilingListId)

    /**
     * Process a given year and return the resulting HouseFilingList.
     *
     * If the year is in the database, the existing HouseFilingList will be reused. Otherwise, a new HouseFilingList
     * will be created and saved to the database. Once the HouseFilingList is created or fetched, the TSV will be
     * downloaded if the etag is null or different from the existing etag. Finally, the TSV will be parsed and its rows
     * will be saved to the database.
     */
    @Transactional
    fun processYear(year: Int, force: Boolean = false): HouseFilingList {
        val existingFilingList = houseFilingListRepository.findByYear(year)
            ?: houseFilingListRepository.save(
                HouseFilingList(
                    year = year,
                    etag = null,
                    gcsUri = getListGcsUri(year),
                    parsed = false,
                    parsedAt = null,
                    createdAt = clock.instant(),
                    updatedAt = null,
                )
            )
        return parse(downloadTsv(existingFilingList, force), force)
    }

    private fun getListGcsUri(year: Int): String = "gs://insider-trading-analyzer/congress/house/disclosure-list/$year.zip"

    private fun downloadTsv(houseFilingList: HouseFilingList, force: Boolean): HouseFilingList {
        val execute = force || houseFilingList.etag == null || houseFilingList.etag != getEtag(houseFilingList.year)
        if (!execute) return houseFilingList
        val etag = houseHttpClient.getFilingListEtag(houseFilingList.year)
        if (houseFilingList.etag == etag) return houseFilingList
        val response = houseHttpClient.fetchFilingList(houseFilingList.year, houseFilingList.gcsUri)
        return houseFilingListRepository.save(
            houseFilingList.copy(
                etag = response.etag,
                parsed = false,
                parsedAt = null,
                updatedAt = clock.instant(),
            ),
        )
    }

    private fun getEtag(year: Int): String? = houseHttpClient.getFilingListEtag(year)

    /**
     * Parses TSV files published by the House Office of the Clerk and saves the rows to the database. Here's a sample
     * of the TSV:
     *
     * ```text
     * Prefix	Last	First	Suffix	FilingType	StateDst	Year	FilingDate	DocID
     * 	Aaron	Richard		D	MI04	2025	3/24/2025	40003749
     * 	Abrevaya	David		W	IL09	2025	5/19/2025	8005
     * 	Abughazaleh	Katherine M.		C	IL09	2025	5/14/2025	10065677
     * 	Adams	Jared		W	MN07	2025	6/3/2025	8011
     * 	Adeimy	Deborah		X	FL22	2025	5/22/2025	30023997
     * Hon.	Aderholt	Robert B.		P	AL04	2025	9/10/2025	20032062
     * 	Ager	Jamie		X	NC11	2025	9/3/2025	30025402
     * 	Aisen	Joshua		W	VA11	2025	6/24/2025	8013
     * 	Al-Aqidi	Dalia		X	MN05	2025	5/22/2025	30023906
     * 	Albright	Joe		C	IL16	2025	5/15/2025	10068916
     * 	Albright	Joe		X	IL16	2025	5/22/2025	30023834
     * ```
     */
    private fun parse(houseFilingList: HouseFilingList, force: Boolean): HouseFilingList {
        if (!force && houseFilingList.parsed) return houseFilingList
        storage.getResource(houseFilingList.gcsUri).inputStream.use { inputStream ->
            inputStream.bufferedReader().useLines { lines ->
                lines.take(1).first().also { header ->
                    check(header == expectedHeader) { "Invalid header: expected \"$expectedHeader\", got $header" }
                }
                lines.forEach { line ->
                    runCatching {
                        val row = parseRow(line, houseFilingList)
                        val existingRow = houseFilingListRowRepository.findByDocId(row.docId)
                        if (existingRow == null) houseFilingListRowRepository.save(row)
                    }.onFailure {
                        println("Failed to parse row: $line")
                        // TODO save the failure to the database
                    }
                }
            }
        }
        return houseFilingListRepository.save(houseFilingList.copy(parsed = true, parsedAt = clock.instant()))
    }

    private fun parseRow(line: String, houseFilingList: HouseFilingList): HouseFilingListRow {
        val (prefix, last, first, suffix, filingType, stateDst, year, filingDate, docId) = line.split('\t')
        return HouseFilingListRow(
            houseFilingListId = houseFilingList.id ?: error("HouseFilingList ID must not be null"),
            prefix = prefix,
            last = last,
            first = first,
            suffix = suffix,
            filingType = filingType,
            stateDst = stateDst,
            year = year.toInt(),
            filingDate = LocalDate.parse(filingDate, dateFormatter),
            docId = docId,
            downloaded = false,
            downloadedAt = null,
            rawRowData = line,
            createdAt = clock.instant(),
            updatedAt = null,
        )
    }
}
