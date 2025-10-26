package com.github.bsaltz.insider.congress.house.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.StringReader
import java.time.LocalDate

class CongressHouseFilingParserServiceTest {
    private val parserService = CongressHouseFilingParserService()

    @Test
    fun `should parse TSV data correctly`() {
        val tsvData =
            """
            Prefix	Last	First	Suffix	FilingType	StateDst	Year	FilingDate	DocID
            	Aaron	Richard		D	MI04	2025	3/24/2025	40003749
            	Abrevaya	David		W	IL09	2025	5/19/2025	8005
            	Abughazaleh	Katherine M.		C	IL09	2025	5/14/2025	10065677
            	Adams	Jared		W	MN07	2025	6/3/2025	8011
            	Adeimy	Deborah		X	FL22	2025	5/22/2025	30023997
            Hon.	Aderholt	Robert B.		P	AL04	2025	9/10/2025	20032062
            	Ager	Jamie		X	NC11	2025	9/3/2025	30025402
            	Aisen	Joshua		W	VA11	2025	6/24/2025	8013
            	Al-Aqidi	Dalia		X	MN05	2025	5/22/2025	30023906
            	Albright	Joe		C	IL16	2025	5/15/2025	10068916
            	Albright	Joe		X	IL16	2025	5/22/2025	30023834
            """.trimIndent()

        val result = parserService.parseTsv(StringReader(tsvData))

        assertEquals(11, result.size)

        // Test first record (Aaron)
        val firstRecord = result[0]
        assertEquals("40003749", firstRecord.docId)
        assertEquals("", firstRecord.prefix)
        assertEquals("Aaron", firstRecord.last)
        assertEquals("Richard", firstRecord.first)
        assertEquals("", firstRecord.suffix)
        assertEquals("D", firstRecord.filingType)
        assertEquals("MI04", firstRecord.stateDst)
        assertEquals(2025, firstRecord.year)
        assertEquals(LocalDate.of(2025, 3, 24), firstRecord.filingDate)

        // Test record with prefix (Aderholt)
        val aderholtRecord = result[5]
        assertEquals("20032062", aderholtRecord.docId)
        assertEquals("Hon.", aderholtRecord.prefix)
        assertEquals("Aderholt", aderholtRecord.last)
        assertEquals("Robert B.", aderholtRecord.first)
        assertEquals("", aderholtRecord.suffix)
        assertEquals("P", aderholtRecord.filingType)
        assertEquals("AL04", aderholtRecord.stateDst)
        assertEquals(2025, aderholtRecord.year)
        assertEquals(LocalDate.of(2025, 9, 10), aderholtRecord.filingDate)

        // Test last record (Albright)
        val lastRecord = result[10]
        assertEquals("30023834", lastRecord.docId)
        assertEquals("", lastRecord.prefix)
        assertEquals("Albright", lastRecord.last)
        assertEquals("Joe", lastRecord.first)
        assertEquals("", lastRecord.suffix)
        assertEquals("X", lastRecord.filingType)
        assertEquals("IL16", lastRecord.stateDst)
        assertEquals(2025, lastRecord.year)
        assertEquals(LocalDate.of(2025, 5, 22), lastRecord.filingDate)
    }

    @Test
    fun `should handle empty TSV with header only`() {
        val tsvData = "Prefix\tLast\tFirst\tSuffix\tFilingType\tStateDst\tYear\tFilingDate\tDocID"

        val result = parserService.parseTsv(StringReader(tsvData))

        assertEquals(0, result.size)
    }
}
