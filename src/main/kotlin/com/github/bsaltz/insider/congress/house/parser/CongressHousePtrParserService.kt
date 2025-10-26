package com.github.bsaltz.insider.congress.house.parser

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.bsaltz.insider.congress.house.model.AmountRange
import com.github.bsaltz.insider.congress.house.model.FilerStatus
import com.github.bsaltz.insider.congress.house.model.FilingStatus
import com.github.bsaltz.insider.congress.house.model.Ownership
import com.github.bsaltz.insider.congress.house.model.ParseIssue
import com.github.bsaltz.insider.congress.house.model.ParseIssueCategory
import com.github.bsaltz.insider.congress.house.model.ParseIssueSeverity
import com.github.bsaltz.insider.congress.house.model.TradeType
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class CongressHousePtrParserService(
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    fun parseFromJson(
        jsonContent: String,
        sourceUrl: String,
    ): ParseResult<ParsedPeriodicTransactionReport> =
        try {
            val visionResponse = parseJson(jsonContent)
            val ocrText = extractOcrText(visionResponse)
            parsePeriodicTransactionReport(ocrText, sourceUrl)
        } catch (e: Exception) {
            val issue =
                ParseIssue(
                    docId = "UNKNOWN", // We don't have docId yet
                    severity = ParseIssueSeverity.ERROR,
                    category = ParseIssueCategory.DOCUMENT_STRUCTURE,
                    message = "Failed to parse JSON or extract OCR text",
                    details = e.message,
                    createdAt = clock.instant(),
                )
            ParseResult.Error(listOf(issue))
        }

    private fun parseJson(jsonContent: String): Map<String, Any> =
        objectMapper.readValue(jsonContent, object : TypeReference<Map<String, Any>>() {})

    private fun extractOcrText(visionResponse: Map<String, Any>): String {
        val responses = visionResponse["responses"] as List<Map<String, Any>>
        val fullTextAnnotation = responses[0]["fullTextAnnotation"] as Map<String, Any>
        return fullTextAnnotation["text"] as String
    }

    private fun parsePeriodicTransactionReport(
        ocrText: String,
        sourceUrl: String,
    ): ParseResult<ParsedPeriodicTransactionReport> {
        val issues = mutableListOf<ParseIssue>()

        // Extract document ID
        val docIdRegex = Regex("Filing ID #(\\d+)")
        val docId = docIdRegex.find(ocrText)?.groupValues?.get(1)
        if (docId == null) {
            val issue =
                ParseIssue(
                    docId = "UNKNOWN",
                    severity = ParseIssueSeverity.ERROR,
                    category = ParseIssueCategory.DOCUMENT_STRUCTURE,
                    message = "Could not extract document ID",
                    details = "No Filing ID pattern found in OCR text",
                    location = "Document header",
                    createdAt = clock.instant(),
                )
            return ParseResult.Error(listOf(issue))
        }

        // Extract filer information
        val filerInfoResult = parseFilerInformation(ocrText, docId)
        when (filerInfoResult) {
            is ParseResult.Error -> return filerInfoResult
            is ParseResult.SuccessWithWarnings -> issues.addAll(filerInfoResult.warnings)
            is ParseResult.Success -> {} // No issues to add
        }
        val filerInfo = filerInfoResult.getDataOrNull()!!

        // Parse transactions (warnings are allowed here)
        val transactionsResult = parseTransactionsWithIssues(ocrText, sourceUrl, docId)
        issues.addAll(transactionsResult.getAllIssues())

        val parsedReport =
            ParsedPeriodicTransactionReport(
                docId = docId,
                filerFullName = filerInfo.filerFullName,
                filerStatus = filerInfo.filerStatus,
                state = filerInfo.state,
                district = filerInfo.district,
                fileSourceUrl = sourceUrl,
                transactions = transactionsResult.getDataOrNull() ?: emptyList(),
            )

        return if (issues.any { it.severity == ParseIssueSeverity.ERROR }) {
            ParseResult.Error(issues.filter { it.severity == ParseIssueSeverity.ERROR })
        } else if (issues.isNotEmpty()) {
            ParseResult.SuccessWithWarnings(parsedReport, issues)
        } else {
            ParseResult.Success(parsedReport)
        }
    }

    private data class FilerInformation(
        val filerFullName: String,
        val filerStatus: FilerStatus,
        val state: String,
        val district: Int,
    )

    private fun parseFilerInformation(
        ocrText: String,
        docId: String,
    ): ParseResult<FilerInformation> {
        val issues = mutableListOf<ParseIssue>()

        // Extract filer information from FILER INFORMATION block
        val filerInfoBlockStart = ocrText.indexOf("FILER INFORMATION")
        if (filerInfoBlockStart == -1) {
            val issue =
                ParseIssue(
                    docId = docId,
                    severity = ParseIssueSeverity.ERROR,
                    category = ParseIssueCategory.FILER_INFORMATION_PARSING,
                    message = "Could not find FILER INFORMATION block",
                    details = "FILER INFORMATION section not found in OCR text",
                    location = "FILER INFORMATION block",
                    createdAt = clock.instant(),
                )
            return ParseResult.Error(listOf(issue))
        }

        // Get the next 6 lines starting from FILER INFORMATION
        val fromFilerInfo = ocrText.substring(filerInfoBlockStart)
        val filerInfoLines = fromFilerInfo.split("\n").take(6)

        if (filerInfoLines.size < 5) {
            val issue =
                ParseIssue(
                    docId = docId,
                    severity = ParseIssueSeverity.ERROR,
                    category = ParseIssueCategory.FILER_INFORMATION_PARSING,
                    message = "FILER INFORMATION block does not contain enough lines",
                    details = "Expected at least 5 lines, found ${filerInfoLines.size}",
                    location = "FILER INFORMATION block",
                    createdAt = clock.instant(),
                )
            return ParseResult.Error(listOf(issue))
        }

        // Parse the lines flexibly
        var filerFullName: String? = null
        var statusText: String? = null
        var state: String? = null
        var district: Int? = null

        for (line in filerInfoLines) {
            val trimmedLine = line.trim()

            // Skip empty lines and header
            if (trimmedLine.isEmpty() ||
                trimmedLine == "FILER INFORMATION" ||
                trimmedLine == "Name:" ||
                trimmedLine == "Status:"
            ) {
                continue
            }

            // Check for state/district pattern (AA00)
            val stateDistrictMatch = Regex("([A-Z]{2})(\\d+)").find(trimmedLine)
            if (stateDistrictMatch != null) {
                state = stateDistrictMatch.groupValues[1]
                district = stateDistrictMatch.groupValues[2].toInt()
                continue
            }

            // Check for status keywords
            val lowerLine = trimmedLine.lowercase()
            if (lowerLine == "member" || lowerLine == "officer" || lowerLine == "employee" || lowerLine == "candidate") {
                statusText = trimmedLine
                continue
            }

            // Check if line starts with "Hon." or contains a full name pattern
            if (trimmedLine.startsWith("Hon.") ||
                (trimmedLine.contains(" ") && trimmedLine.length > 5 && filerFullName == null)
            ) {
                filerFullName = trimmedLine
                continue
            }
        }

        // Validate required fields were found
        if (filerFullName == null) {
            val issue =
                ParseIssue(
                    docId = docId,
                    severity = ParseIssueSeverity.ERROR,
                    category = ParseIssueCategory.FILER_INFORMATION_PARSING,
                    message = "Could not extract filer name from FILER INFORMATION block",
                    details = "No valid filer name pattern found in the block",
                    location = "FILER INFORMATION block",
                    createdAt = clock.instant(),
                )
            return ParseResult.Error(listOf(issue))
        }
        if (statusText == null) {
            val issue =
                ParseIssue(
                    docId = docId,
                    severity = ParseIssueSeverity.ERROR,
                    category = ParseIssueCategory.FILER_INFORMATION_PARSING,
                    message = "Could not extract filer status from FILER INFORMATION block",
                    details = "No valid status keyword found in the block",
                    location = "FILER INFORMATION block",
                    createdAt = clock.instant(),
                )
            return ParseResult.Error(listOf(issue))
        }
        if (state == null || district == null) {
            val issue =
                ParseIssue(
                    docId = docId,
                    severity = ParseIssueSeverity.ERROR,
                    category = ParseIssueCategory.FILER_INFORMATION_PARSING,
                    message = "Could not extract state/district from FILER INFORMATION block",
                    details = "No valid state/district pattern (e.g., AL04) found in the block",
                    location = "FILER INFORMATION block",
                    createdAt = clock.instant(),
                )
            return ParseResult.Error(listOf(issue))
        }

        // Map status text to enum
        val filerStatus =
            when (statusText.lowercase()) {
                "member" -> FilerStatus.MEMBER
                "officer", "employee" -> FilerStatus.OFFICER_OR_EMPLOYEE
                "candidate" -> FilerStatus.CANDIDATE
                else -> {
                    val issue =
                        ParseIssue(
                            docId = docId,
                            severity = ParseIssueSeverity.ERROR,
                            category = ParseIssueCategory.DATA_VALIDATION,
                            message = "Unknown filer status: $statusText",
                            details = "Status must be one of: member, officer, employee, candidate",
                            location = "FILER INFORMATION block",
                            createdAt = clock.instant(),
                        )
                    return ParseResult.Error(listOf(issue))
                }
            }

        val filerInfo = FilerInformation(filerFullName, filerStatus, state, district)
        return if (issues.isNotEmpty()) {
            ParseResult.SuccessWithWarnings(filerInfo, issues)
        } else {
            ParseResult.Success(filerInfo)
        }
    }

    private fun parseTransactionsWithIssues(
        ocrText: String,
        sourceUrl: String,
        docId: String,
    ): ParseResult<List<ParsedPeriodicTransactionReportTransaction>> {
        val transactions = mutableListOf<ParsedPeriodicTransactionReportTransaction>()
        val issues = mutableListOf<ParseIssue>()

        // Find all asset patterns: [AT] where AT is 2-character asset type code
        val assetPattern = Regex("""\[([A-Z0-9]{2})\]""")
        val assetMatches = assetPattern.findAll(ocrText)

        var transactionIndex = 1
        for (assetMatch in assetMatches) {
            try {
                val transaction = parseTransaction(ocrText, assetMatch, sourceUrl)
                transactions.add(transaction)
            } catch (e: Exception) {
                // Transaction parsing failures are warnings, not errors
                val issue =
                    ParseIssue(
                        docId = docId,
                        severity = ParseIssueSeverity.WARNING,
                        category = ParseIssueCategory.TRANSACTION_PARSING,
                        message = "Could not parse transaction for asset ${assetMatch.value}",
                        details = e.message,
                        location = "Transaction #$transactionIndex",
                        createdAt = clock.instant(),
                    )
                issues.add(issue)
            }
            transactionIndex++
        }

        return if (issues.isNotEmpty()) {
            ParseResult.SuccessWithWarnings(transactions, issues)
        } else {
            ParseResult.Success(transactions)
        }
    }

    private fun parseTransaction(
        ocrText: String,
        assetMatch: MatchResult,
        sourceUrl: String,
    ): ParsedPeriodicTransactionReportTransaction {
        val assetType = assetMatch.groupValues[1] // e.g., "ST"
        val assetMatchStart = assetMatch.range.first
        val assetMatchText = assetMatch.value // e.g., "[ST]"

        // Find the asset name by looking backwards from the [AT] pattern
        // Asset names can span multiple lines, so we need to collect all relevant text
        val beforeAssetMatch = ocrText.substring(0, assetMatchStart)
        val lines = beforeAssetMatch.split("\n")

        // Collect potential asset name lines by going backwards
        val assetNameParts = mutableListOf<String>()
        for (i in lines.size - 1 downTo maxOf(0, lines.size - 10)) {
            val line = lines[i].trim()

            // Skip empty lines and clearly non-asset content
            if (line.isEmpty() ||
                line.matches(Regex("[A-Z]{1,2}")) ||
                // Skip ownership codes like "SP"
                line.contains("TRANSACTIONS") ||
                line.contains("Date") ||
                line.contains("Amount") ||
                line.contains("Type") ||
                line.contains("Cap.") ||
                line.contains("Gains") ||
                line.contains("\$200") ||
                line.contains("FILING STATUS") ||
                line.contains("SUBHOLDING") ||
                line.matches(Regex("\\d{2}/\\d{2}/\\d{4}")) ||
                // Skip dates
                line.matches(Regex("\\$[\\d,]+\\s*-\\s*\\$[\\d,]+")) // Skip amount ranges
            ) {
                // If we've already found some asset name parts, stop here
                if (assetNameParts.isNotEmpty()) break
                continue
            }

            // Add this line as part of the asset name
            assetNameParts.add(0, line) // Add to beginning to maintain order

            // If this line looks like a complete asset name, we can stop
            if (line.length > 10 && (line.contains("Inc") || line.contains("Corp") || line.contains("Company") || line.contains("."))) {
                break
            }
        }

        if (assetNameParts.isEmpty()) {
            error("Could not extract asset name for $assetMatchText")
        }

        // Join the asset name parts and clean up
        var assetName = assetNameParts.joinToString(" ").trim()

        // Clean asset name - remove trailing trade type indicators (S, P, Р)
        // Also handle cases where trade type appears at end of asset name line
        assetName = assetName.replace(Regex("\\s+[PS\u0420]\\b"), "").trim()

        // Additional cleaning: remove isolated single letters at the end (trade types that got collected)
        assetName = assetName.replace(Regex("\\s+\\[[A-Z]{2}]\\s*$"), "").trim()

        // Find transaction context around this asset match
        val transactionStart = maxOf(0, assetMatchStart - 200)
        val transactionEnd = minOf(ocrText.length, assetMatch.range.last + 1000)
        val transactionContext = ocrText.substring(transactionStart, transactionEnd)

        // Extract ownership - look for ownership codes before the asset
        val ownershipPattern = Regex("\\b(SP|DC|JT)\\b")
        val ownershipMatch = ownershipPattern.find(beforeAssetMatch.takeLast(100))
        val owner =
            ownershipMatch?.let { match ->
                when (match.value) {
                    "SP" -> Ownership.SP
                    "DC" -> Ownership.DC
                    "JT" -> Ownership.JT
                    else -> null
                }
            }

        // Extract trade type - look for P (Purchase) or S (Sale) near the asset
        // Note: OCR may return Cyrillic Р (U+0420) instead of Latin P (U+0050)
        // Handle both word boundaries and line boundaries
        val tradeTypePattern = Regex("(?:^|\\s)([PSР])(?:\\s|$)", RegexOption.MULTILINE)
        val tradeTypeMatch =
            tradeTypePattern.find(transactionContext)
                ?: error("Could not determine trade type for $assetName, no match found in context:\n$transactionContext")
        val tradeType =
            tradeTypeMatch.let { match ->
                when (match.groupValues[1]) {
                    "P", "Р" -> TradeType.PURCHASE // Handle both Latin P and Cyrillic Р
                    "S" -> TradeType.SALE
                    else -> error("Could not determine trade type for $assetName, [${match.groupValues[1]}]")
                }
            }

        // Extract dates - pattern: MM/DD/YYYY MM/DD/YYYY
        val dateRegex = Regex("(\\d{2}/\\d{2}/\\d{4})\\s+(\\d{2}/\\d{2}/\\d{4})")
        val dateMatch =
            dateRegex.find(transactionContext)
                ?: error("Could not extract transaction dates for $assetName")

        val tradeDate = parseDate(dateMatch.groupValues[1])

        // Extract amount range
        val amountRegex = Regex("\\$([\\d,]+)\\s*-\\s*\\$([\\d,]+)")
        val amountMatch =
            amountRegex.find(transactionContext)
                ?: error("Could not extract amount range for $assetName")

        val minAmount = amountMatch.groupValues[1].replace(",", "").toInt()
        val maxAmount = amountMatch.groupValues[2].replace(",", "").toInt()

        val amountRange =
            when {
                minAmount == 1001 && maxAmount == 15000 -> AmountRange.A_1001_15000
                minAmount == 15001 && maxAmount == 50000 -> AmountRange.B_15001_50000
                minAmount == 50001 && maxAmount == 100000 -> AmountRange.C_50001_100000
                minAmount == 100001 && maxAmount == 250000 -> AmountRange.D_100001_250000
                minAmount == 250001 && maxAmount == 500000 -> AmountRange.E_250001_500000
                minAmount == 500001 && maxAmount == 1000000 -> AmountRange.F_500001_1000000
                minAmount == 1000001 && maxAmount == 5000000 -> AmountRange.G_1000001_5000000
                minAmount == 5000001 && maxAmount == 25000000 -> AmountRange.H_5000001_25000000
                minAmount == 25000001 && maxAmount == 50000000 -> AmountRange.I_25000001_50000000
                minAmount >= 50000000 -> AmountRange.J_OVER_50000000
                else -> error("Unknown amount range: $minAmount - $maxAmount")
            }

        // Filing status
        val filingStatusRegex = Regex("FILING STATUS:\\s*(New|Amended)", RegexOption.IGNORE_CASE)
        val filingStatus =
            filingStatusRegex.find(transactionContext)?.let { match ->
                when (match.groupValues[1].lowercase()) {
                    "new" -> FilingStatus.NEW
                    "amended" -> FilingStatus.AMENDED
                    else -> FilingStatus.NEW
                }
            } ?: FilingStatus.NEW

        return ParsedPeriodicTransactionReportTransaction(
            owner = owner,
            assetName = assetName,
            assetType = assetType,
            filingStatus = filingStatus,
            tradeType = tradeType,
            amountRange = amountRange,
            tradeDate = tradeDate,
            fileSourceUrl = sourceUrl,
        )
    }

    private fun parseDate(dateString: String): LocalDate {
        // Convert MM/DD/YYYY to LocalDate
        val formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")
        return LocalDate.parse(dateString, formatter)
    }
}
