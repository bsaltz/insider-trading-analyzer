package com.github.bsaltz.insider.congress2.house.client

import com.github.bsaltz.insider.utils.StorageUtil.toResource
import com.google.cloud.spring.storage.GoogleStorageLocation
import com.google.cloud.spring.storage.GoogleStorageResource
import com.google.cloud.storage.Storage

data class StoredResponse(
    val googleStorageLocation: GoogleStorageLocation,
    val etag: String,
) {
    fun toResource(storage: Storage): GoogleStorageResource =
        googleStorageLocation.toResource(storage)
}
