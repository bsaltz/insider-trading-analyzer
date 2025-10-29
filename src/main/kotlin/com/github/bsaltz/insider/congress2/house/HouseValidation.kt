package com.github.bsaltz.insider.congress2.house

import java.time.Clock

/**
 * Validation utilities for House disclosure processing.
 */
object HouseValidation {
    /**
     * Validates that a single year is within acceptable bounds.
     *
     * @param year The year to validate
     * @param clock Clock for determining current year
     * @throws IllegalArgumentException if year is invalid
     */
    fun validateYear(
        year: Int,
        clock: Clock,
    ) {
        require(year >= HouseConfig.MINIMUM_DISCLOSURE_YEAR) {
            "Year must be ${HouseConfig.MINIMUM_DISCLOSURE_YEAR} or later"
        }
        val currentYear = clock.instant().atZone(clock.zone).year
        require(year <= currentYear) {
            "Year cannot be in the future"
        }
    }

    /**
     * Validates that all years in a list are within acceptable bounds.
     *
     * @param years The list of years to validate
     * @param clock Clock for determining current year
     * @throws IllegalArgumentException if any year is invalid
     */
    fun validateYears(
        years: List<Int>,
        clock: Clock,
    ) {
        require(years.all { it >= HouseConfig.MINIMUM_DISCLOSURE_YEAR }) {
            "Years must be ${HouseConfig.MINIMUM_DISCLOSURE_YEAR} or later"
        }
        val currentYear = clock.instant().atZone(clock.zone).year
        require(years.all { it <= currentYear }) {
            "Years cannot be in the future"
        }
    }

    /**
     * Validates that a document ID is not blank.
     *
     * @param docId The document ID to validate
     * @throws IllegalArgumentException if document ID is blank
     */
    fun validateDocId(docId: String) {
        require(docId.isNotBlank()) { "Document ID cannot be blank" }
    }
}
