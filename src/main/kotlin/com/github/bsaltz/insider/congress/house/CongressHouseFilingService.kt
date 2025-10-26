package com.github.bsaltz.insider.congress.house

import com.github.bsaltz.insider.congress.house.model.CongressHouseFiling
import com.github.bsaltz.insider.congress.house.model.CongressHouseFilingList
import com.github.bsaltz.insider.congress.house.model.CongressHouseFilingListRepository
import com.github.bsaltz.insider.congress.house.model.CongressHouseFilingRepository
import com.github.bsaltz.insider.congress.house.parser.CongressHouseFilingParserService
import com.github.bsaltz.insider.congress.house.parser.ParsedCongressHouseFiling
import com.google.cloud.storage.Storage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.InputStreamReader
import java.nio.file.Files
import java.util.zip.ZipInputStream

@Service
class CongressHouseFilingService(
    private val congressHouseFilingListRepository: CongressHouseFilingListRepository,
    private val congressHouseFilingRepository: CongressHouseFilingRepository,
    private val congressHouseHttpClient: CongressHouseHttpClient,
    private val congressHouseFilingParserService: CongressHouseFilingParserService,
    private val storage: Storage,
) {
    fun getFilingList(year: Int): CongressHouseFilingList? = congressHouseFilingListRepository.findOneByYear(year)

    fun getFiling(docId: String): CongressHouseFiling? = congressHouseFilingRepository.findOneByDocId(docId)

    @Transactional
    fun processFilingList(year: Int): CongressHouseFilingList {
        val existingFileList = getFilingList(year)
        if (existingFileList != null && existingFileList.etag == congressHouseHttpClient.getDisclosureListEtag(year)) {
            return existingFileList
        }
        val storedResponse = congressHouseHttpClient.fetchDisclosureList(year)
        val resource = storedResponse.toResource(storage)
        val parsedFilings = extractAndParseZip(resource, year)
        val filingList =
            (existingFileList ?: CongressHouseFilingList(year = year, etag = storedResponse.etag))
                .let { congressHouseFilingListRepository.save(it.copy(etag = storedResponse.etag)) }
        parsedFilings.forEach {
            val existing = congressHouseFilingRepository.findOneByDocId(it.docId)
            val toSave = existing?.update(it) ?: it.toEntity(congressHouseFilingListId = filingList.id!!)
            congressHouseFilingRepository.save(toSave)
        }
        return filingList
    }

    private fun extractAndParseZip(
        zipResource: com.google.cloud.spring.storage.GoogleStorageResource,
        year: Int,
    ): List<ParsedCongressHouseFiling> {
        // 1) Save the ZIP locally in a temp file
        val tempZipFile = Files.createTempFile("congress-house-$year", ".zip")
        try {
            zipResource.inputStream.use { zipInputStream ->
                Files.copy(zipInputStream, tempZipFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }

            // 2) Extract the ZIP file straight into the GoogleStorageResource write channel
            val tsvFileName = "${year}FD.txt"
            Files.newInputStream(tempZipFile).use { tempFileInputStream ->
                ZipInputStream(tempFileInputStream).use { zipInputStream ->
                    var entry = zipInputStream.nextEntry
                    while (entry != null) {
                        if (entry.name == tsvFileName && !entry.isDirectory) {
                            // Extract directly to Google Storage and parse
                            val extractedResource = com.google.cloud.spring.storage.GoogleStorageResource(
                                storage,
                                "gs://insider-trading-analyzer/congress/house/disclosure-list/$year.tsv"
                            )

                            extractedResource.writableChannel().use { channel ->
                                val buffer = ByteArray(10240)
                                while (true) {
                                    val bytesRead = zipInputStream.read(buffer)
                                    if (bytesRead == -1) break
                                    channel.write(java.nio.ByteBuffer.wrap(buffer, 0, bytesRead))
                                }
                            }

                            // Parse the extracted TSV
                            return extractedResource.inputStream.use { tsvInputStream ->
                                congressHouseFilingParserService.parseTsv(InputStreamReader(tsvInputStream))
                            }
                        }
                        entry = zipInputStream.nextEntry
                    }
                }
            }

            throw IllegalStateException("TSV file $tsvFileName not found in ZIP archive")
        } finally {
            // 4) Delete the temp ZIP file
            Files.deleteIfExists(tempZipFile)
        }
    }
}

private fun CongressHouseFiling.update(parsedFiling: ParsedCongressHouseFiling): CongressHouseFiling =
    copy(
        docId = parsedFiling.docId,
        prefix = parsedFiling.prefix,
        last = parsedFiling.last,
        first = parsedFiling.first,
        suffix = parsedFiling.suffix,
        filingType = parsedFiling.filingType,
        stateDst = parsedFiling.stateDst,
        year = parsedFiling.year,
        filingDate = parsedFiling.filingDate,
    )
