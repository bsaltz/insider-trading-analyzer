package com.github.bsaltz.insider.vision

import com.google.cloud.spring.storage.GoogleStorageLocation
import com.google.cloud.spring.storage.GoogleStorageResource
import com.google.cloud.spring.vision.CloudVisionTemplate
import com.google.cloud.storage.Storage
import org.springframework.stereotype.Service
import java.time.Clock

@Service
class OcrProcessorService(
    private val storage: Storage,
    private val cloudVisionTemplate: CloudVisionTemplate,
    private val ocrParseResultRepository: OcrParseResultRepository,
    private val clock: Clock,
) {
    fun parsePdf(pdfLocation: GoogleStorageLocation): OcrParseResult {
        val pdfResource = GoogleStorageResource(storage, pdfLocation, true)
        val text = cloudVisionTemplate.extractTextFromPdf(pdfResource).joinToString("\n")
        return ocrParseResultRepository.save(
            OcrParseResult(
                response = text,
                createdTime = clock.instant(),
            ),
        )
    }
}
