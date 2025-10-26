package com.github.bsaltz.insider.congress.house.model

import io.zonky.test.db.AutoConfigureEmbeddedDatabase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate

@DataJdbcTest
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase
class PeriodicTransactionReportTransactionRepositoryTest(
    @param:Autowired
    private val periodicTransactionReportRepository: PeriodicTransactionReportRepository,
    @param:Autowired
    private val periodicTransactionReportTransactionRepository: PeriodicTransactionReportTransactionRepository,
) {
    @Test
    fun `should save and retrieve PeriodicTransactionReportTransaction`() {
        // Given - create parent report first
        val report =
            PeriodicTransactionReport(
                docId = "20032062",
                filerFullName = "Hon. Robert B. Aderholt",
                filerStatus = FilerStatus.MEMBER,
                state = "AL",
                district = 4,
                fileSourceUrl = "https://test-source-url.com/sample.pdf",
            )
        val savedReport = periodicTransactionReportRepository.save(report)

        // Given - create transaction
        val transaction =
            PeriodicTransactionReportTransaction(
                periodicTransactionReportId = savedReport.id!!,
                owner = Ownership.SP,
                assetName = "GSK plc American Depositary Shares (GSK)",
                assetType = "ST",
                filingStatus = FilingStatus.NEW,
                tradeType = TradeType.SALE,
                amountRange = AmountRange.A_1001_15000,
                tradeDate = LocalDate.of(2025, 7, 28),
                fileSourceUrl = "https://test-source-url.com/sample.pdf",
                parsedDate = LocalDate.now(),
            )

        // When
        val savedTransaction = periodicTransactionReportTransactionRepository.save(transaction)

        // Then
        assertNotNull(savedTransaction.id)
        assertEquals(savedReport.id, savedTransaction.periodicTransactionReportId)
        assertEquals(Ownership.SP, savedTransaction.owner)
        assertEquals("GSK plc American Depositary Shares (GSK)", savedTransaction.assetName)
        assertEquals("ST", savedTransaction.assetType)
        assertEquals(FilingStatus.NEW, savedTransaction.filingStatus)
        assertEquals(TradeType.SALE, savedTransaction.tradeType)
        assertEquals(AmountRange.A_1001_15000, savedTransaction.amountRange)
        assertEquals(LocalDate.of(2025, 7, 28), savedTransaction.tradeDate)
        assertEquals("https://test-source-url.com/sample.pdf", savedTransaction.fileSourceUrl)

        // When retrieving by parent ID
        val transactions = periodicTransactionReportTransactionRepository.findByPeriodicTransactionReportId(savedReport.id!!)

        // Then
        assertEquals(1, transactions.size)
        val retrievedTransaction = transactions[0]
        assertEquals(savedTransaction.id, retrievedTransaction.id)
        assertEquals(savedReport.id, retrievedTransaction.periodicTransactionReportId)
        assertEquals(Ownership.SP, retrievedTransaction.owner)
        assertEquals("GSK plc American Depositary Shares (GSK)", retrievedTransaction.assetName)
        assertEquals(TradeType.SALE, retrievedTransaction.tradeType)
    }

    @Test
    fun `should handle multiple transactions for same report`() {
        // Given - create parent report
        val report =
            PeriodicTransactionReport(
                docId = "20029060",
                filerFullName = "Hon. David J. Taylor",
                filerStatus = FilerStatus.MEMBER,
                state = "OH",
                district = 2,
                fileSourceUrl = "https://test-source-url.com/20029060.pdf",
            )
        val savedReport = periodicTransactionReportRepository.save(report)

        // Given - create multiple transactions
        val transaction1 =
            PeriodicTransactionReportTransaction(
                periodicTransactionReportId = savedReport.id!!,
                owner = null,
                assetName = "Amazon.com, Inc. - Common Stock (AMZN)",
                assetType = "ST",
                filingStatus = FilingStatus.NEW,
                tradeType = TradeType.PURCHASE,
                amountRange = AmountRange.A_1001_15000,
                tradeDate = LocalDate.of(2025, 3, 27),
                fileSourceUrl = "https://test-source-url.com/20029060.pdf",
                parsedDate = LocalDate.now(),
            )

        val transaction2 =
            PeriodicTransactionReportTransaction(
                periodicTransactionReportId = savedReport.id!!,
                owner = Ownership.DC,
                assetName = "Apple Inc. (AAPL)",
                assetType = "ST",
                filingStatus = FilingStatus.AMENDED,
                tradeType = TradeType.SALE,
                amountRange = AmountRange.B_15001_50000,
                tradeDate = LocalDate.of(2025, 3, 28),
                fileSourceUrl = "https://test-source-url.com/20029060.pdf",
                parsedDate = LocalDate.now(),
            )

        // When
        periodicTransactionReportTransactionRepository.save(transaction1)
        periodicTransactionReportTransactionRepository.save(transaction2)

        // Then
        val transactions = periodicTransactionReportTransactionRepository.findByPeriodicTransactionReportId(savedReport.id!!)
        assertEquals(2, transactions.size)

        // Verify different ownership values
        val ownerships = transactions.map { it.owner }.toSet()
        assertTrue(ownerships.contains(null))
        assertTrue(ownerships.contains(Ownership.DC))

        // Verify different trade types
        val tradeTypes = transactions.map { it.tradeType }.toSet()
        assertTrue(tradeTypes.contains(TradeType.PURCHASE))
        assertTrue(tradeTypes.contains(TradeType.SALE))
    }

    @Test
    fun `should handle all enum values correctly`() {
        // Given - create parent report
        val report =
            PeriodicTransactionReport(
                docId = "ENUM_TEST",
                filerFullName = "Test Filer",
                filerStatus = FilerStatus.OFFICER_OR_EMPLOYEE,
                state = "NY",
                district = 1,
                fileSourceUrl = "https://test.com/enum.pdf",
            )
        val savedReport = periodicTransactionReportRepository.save(report)

        // Given - transaction with various enum values
        val transaction =
            PeriodicTransactionReportTransaction(
                periodicTransactionReportId = savedReport.id!!,
                owner = Ownership.JT,
                assetName = "Test Asset",
                assetType = "ST",
                filingStatus = FilingStatus.AMENDED,
                tradeType = TradeType.EXCHANGE,
                amountRange = AmountRange.J_OVER_50000000,
                tradeDate = LocalDate.of(2025, 1, 1),
                fileSourceUrl = "https://test.com/enum.pdf",
                parsedDate = LocalDate.now(),
            )

        // When
        val savedTransaction = periodicTransactionReportTransactionRepository.save(transaction)

        // Then - verify enum values are preserved correctly
        assertEquals(Ownership.JT, savedTransaction.owner)
        assertEquals(FilingStatus.AMENDED, savedTransaction.filingStatus)
        assertEquals(TradeType.EXCHANGE, savedTransaction.tradeType)
        assertEquals(AmountRange.J_OVER_50000000, savedTransaction.amountRange)
    }
}
