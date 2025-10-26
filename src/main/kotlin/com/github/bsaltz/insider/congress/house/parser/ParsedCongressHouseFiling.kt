package com.github.bsaltz.insider.congress.house.parser

import com.github.bsaltz.insider.congress.house.model.CongressHouseFiling
import java.time.LocalDate

data class ParsedCongressHouseFiling(
    val docId: String,
    val prefix: String,
    val last: String,
    val first: String,
    val suffix: String,
    val filingType: String,
    val stateDst: String,
    val year: Int,
    val filingDate: LocalDate,
) {
    fun toEntity(congressHouseFilingListId: Long): CongressHouseFiling =
        CongressHouseFiling(
            docId = docId,
            congressHouseFilingListId = congressHouseFilingListId,
            prefix = prefix,
            last = last,
            first = first,
            suffix = suffix,
            filingType = filingType,
            stateDst = stateDst,
            year = year,
            filingDate = filingDate,
        )
}