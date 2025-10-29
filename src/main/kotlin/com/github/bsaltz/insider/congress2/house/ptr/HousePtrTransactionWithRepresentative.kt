package com.github.bsaltz.insider.congress2.house.ptr

import java.time.LocalDate

/**
 * A PTR transaction enriched with representative information from the filing list.
 *
 * This combines transaction data with the representative's name and district information
 * for easier analysis and reporting.
 */
data class HousePtrTransactionWithRepresentative(
    val transaction: HousePtrTransaction,
    val prefix: String,
    val firstName: String,
    val lastName: String,
    val suffix: String,
    val stateDst: String,
    val filingDate: LocalDate,
) {
    /**
     * Gets the full name of the representative.
     */
    fun getFullName(): String {
        val parts =
            listOfNotNull(
                prefix.takeIf { it.isNotBlank() },
                firstName,
                lastName,
                suffix.takeIf { it.isNotBlank() },
            )
        return parts.joinToString(" ")
    }
}
