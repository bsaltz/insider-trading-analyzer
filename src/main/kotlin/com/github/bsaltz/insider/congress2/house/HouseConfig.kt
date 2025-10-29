package com.github.bsaltz.insider.congress2.house

/**
 * Configuration constants for House of Representatives disclosure processing.
 */
object HouseConfig {
    /** Minimum year for which House disclosures are available */
    const val MINIMUM_DISCLOSURE_YEAR = 2008

    /** Base URL for House disclosure clerk website */
    const val DISCLOSURE_BASE_URL = "https://disclosures-clerk.house.gov/public_disc"

    /** Path segment for financial disclosure filing lists */
    const val FILING_LIST_PATH = "/financial-pdfs"

    /** Path segment for PTR (Periodic Transaction Report) documents */
    const val PTR_PATH = "/ptr-pdfs"

    /** Google Cloud Storage bucket name */
    const val GCS_BUCKET = "insider-trading-analyzer"

    /** GCS path for House Congress data */
    const val GCS_HOUSE_CONGRESS_PATH = "congress/house"

    /** GCS subdirectory for disclosure lists */
    const val GCS_DISCLOSURE_LIST_SUBPATH = "disclosure-list"

    /** Buffer size for file operations (10KB) */
    const val BUFFER_SIZE = 10240

    /** Filing type format suffix (FD = Financial Disclosure) */
    const val FILING_TYPE_FORMAT = "FD"

    /** File extension for text files */
    const val FILE_EXTENSION_TXT = "txt"

    /** File extension for PDF files */
    const val FILE_EXTENSION_PDF = "pdf"

    /** File extension for ZIP archives */
    const val FILE_EXTENSION_ZIP = "zip"

    /** Filing type code for Periodic Transaction Reports */
    const val FILING_TYPE_P = "P"

    /**
     * Constructs the URL for downloading a year's filing list.
     *
     * @param year The disclosure year
     * @return The complete URL to the filing list ZIP file
     */
    fun filingListUrl(year: Int): String =
        "$DISCLOSURE_BASE_URL$FILING_LIST_PATH/$year$FILING_TYPE_FORMAT.$FILE_EXTENSION_ZIP"

    /**
     * Constructs the URL for downloading a PTR document PDF.
     *
     * @param docId The document ID
     * @param year The disclosure year
     * @return The complete URL to the PTR PDF
     */
    fun ptrDocUrl(
        docId: String,
        year: Int,
    ): String = "$DISCLOSURE_BASE_URL$PTR_PATH/$year/$docId.$FILE_EXTENSION_PDF"

    /**
     * Constructs the GCS URI for storing a filing list.
     *
     * @param year The disclosure year
     * @return The GCS URI for the filing list storage
     */
    fun filingListGcsUri(year: Int): String =
        "gs://$GCS_BUCKET/$GCS_HOUSE_CONGRESS_PATH/$GCS_DISCLOSURE_LIST_SUBPATH/$year.$FILE_EXTENSION_ZIP"

    /**
     * Constructs the GCS URI for storing a PTR PDF.
     *
     * @param year The disclosure year
     * @param docId The document ID
     * @return The GCS URI for the PTR PDF storage
     */
    fun ptrPdfGcsUri(
        year: Int,
        docId: String,
    ): String = "gs://$GCS_BUCKET/$GCS_HOUSE_CONGRESS_PATH/$year/$docId.$FILE_EXTENSION_PDF"

    /**
     * Constructs the GCS URI for storing OCR text results.
     *
     * @param year The disclosure year
     * @param docId The document ID
     * @return The GCS URI for the OCR text storage
     */
    fun ptrOcrGcsUri(
        year: Int,
        docId: String,
    ): String = "gs://$GCS_BUCKET/$GCS_HOUSE_CONGRESS_PATH/$year/$docId.$FILE_EXTENSION_TXT"

    /**
     * Constructs the expected filename for a filing list.
     *
     * @param year The disclosure year
     * @return The expected filename (e.g., "2024FD.txt")
     */
    fun expectedFilingFileName(year: Int): String =
        "$year$FILING_TYPE_FORMAT.$FILE_EXTENSION_TXT"
}
