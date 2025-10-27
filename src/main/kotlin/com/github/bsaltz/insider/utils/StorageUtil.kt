package com.github.bsaltz.insider.utils

import com.google.cloud.spring.storage.GoogleStorageLocation
import com.google.cloud.spring.storage.GoogleStorageResource
import com.google.cloud.storage.Storage

object StorageUtil {
    fun GoogleStorageLocation.toResource(storage: Storage): GoogleStorageResource = GoogleStorageResource(storage, this, true)

    fun Storage.getResource(location: GoogleStorageLocation): GoogleStorageResource = location.toResource(this)

    fun Storage.getResource(gcsUri: String): GoogleStorageResource = getResource(GoogleStorageLocation(gcsUri))
}
