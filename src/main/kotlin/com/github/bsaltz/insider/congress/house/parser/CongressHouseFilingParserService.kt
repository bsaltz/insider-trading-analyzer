package com.github.bsaltz.insider.congress.house.parser

import org.springframework.stereotype.Service
import java.io.Reader
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Parses TSV files published by the House Office of the Clerk. Here's a sample of the TSV:
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
 *
 * This service does not save the parsed records.
 */
@Service
class CongressHouseFilingParserService {
    private val dateFormatter = DateTimeFormatter.ofPattern("M/d/yyyy")

    fun parseTsv(reader: Reader): List<ParsedCongressHouseFiling> =
        reader.buffered().useLines { lines ->
            lines
                .drop(1) // Skip header row
                .mapNotNull { line -> parseTsvLine(line) }
                .toList()
        }

    private fun parseTsvLine(line: String): ParsedCongressHouseFiling? {
        val columns = line.split('\t')
        if (columns.size != 9) {
            println("Invalid column count: expected 9, got ${columns.size} in line: $line")
            return null
        }

        try {
            // Extract and validate individual columns
            val prefix = columns[0]
            val last = columns[1]
            val first = columns[2]
            val suffix = columns[3]
            val filingType = columns[4]
            val stateDst = columns[5]
            val yearStr = columns[6]
            val filingDateStr = columns[7]
            val docId = columns[8]

            // Validate required fields
            if (last.isBlank()) {
                println("Validation error: Last name is blank in line: $line")
                return null
            }

            if (first.isBlank()) {
                println("Validation error: First name is blank in line: $line")
                return null
            }

            if (filingType.isBlank()) {
                println("Validation error: Filing type is blank in line: $line")
                return null
            }

            if (stateDst.isBlank()) {
                println("Validation error: State/District is blank in line: $line")
                return null
            }

            if (docId.isBlank()) {
                println("Validation error: Document ID is blank in line: $line")
                return null
            }

            // Parse and validate year
            val year =
                try {
                    yearStr.toInt()
                } catch (e: NumberFormatException) {
                    println("Validation error: Invalid year '$yearStr' in line: $line")
                    return null
                }

            if (year !in 2000..2100) {
                println("Validation error: Year $year is out of valid range (2000-2100) in line: $line")
                return null
            }

            // Parse and validate filing date
            val filingDate =
                try {
                    LocalDate.parse(filingDateStr, dateFormatter)
                } catch (e: Exception) {
                    println("Validation error: Invalid date format '$filingDateStr' in line: $line")
                    return null
                }

            return ParsedCongressHouseFiling(
                docId = docId,
                prefix = prefix,
                last = last,
                first = first,
                suffix = suffix,
                filingType = filingType,
                stateDst = stateDst,
                year = year,
                filingDate = filingDate,
            )
        } catch (e: Exception) {
            println("Unexpected parsing error in line: $line - ${e.message}")
            return null
        }
    }
}
