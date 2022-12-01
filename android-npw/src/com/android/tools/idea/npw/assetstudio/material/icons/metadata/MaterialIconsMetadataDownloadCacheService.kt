/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.npw.assetstudio.material.icons.metadata

import com.android.tools.idea.npw.assetstudio.material.icons.common.BundledMetadataUrlProvider
import com.android.tools.idea.npw.assetstudio.material.icons.common.SdkMetadataUrlProvider
import com.android.tools.idea.npw.assetstudio.material.icons.utils.MaterialIconsUtils.getMetadata
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.Semaphore
import java.io.File
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

/**
 * Application Service that caches a [MaterialIconsMetadataDownloadService].
 *
 * The [MaterialIconsMetadata] file is a file that gets updated infrequently, [MaterialIconsMetadataDownloadService] takes care of properly
 * managing when to actually download the file, but the instance to it must be kept around.
 */
class MaterialIconsMetadataDownloadCacheService {

  private var cachedMetadataDownloadService: MaterialIconsMetadataDownloadService? = null

  /**
   * Returns a [CompletableFuture] with a [MaterialIconsMetadata] that executes on a background thread.
   *
   * If the download fails or there is no directory to download the file, it will contain an object from a pre-existing metadata file,
   * either bundled with studio or present in the Android/Sdk directory.
   *
   * @see MaterialIconsMetadataDownloadService
   */
  fun getMetadata(): CompletableFuture<MaterialIconsMetadata> {
    val fallbackMetadataURL: URL =
      // The bundled URL for the metadata is expected to exist.
      checkNotNull(SdkMetadataUrlProvider().getMetadataUrl() ?: BundledMetadataUrlProvider().getMetadataUrl())

    // Return the fallback URL for the metadata if there's no Sdk directory.
    val downloadDir = File(FileUtil.getTempDirectory())
    if (!downloadDir.isDirectory) {
      return CompletableFuture.completedFuture(getMetadata(fallbackMetadataURL))
    }
    return getDownloadService(downloadDir, fallbackMetadataURL).refreshAndGetMetadata(fallbackMetadataURL)
  }

  private fun getDownloadService(downloadDir: File, fallbackMetadataURL: URL): MaterialIconsMetadataDownloadService {
    val downloadService = cachedMetadataDownloadService ?: MaterialIconsMetadataDownloadService(downloadDir, fallbackMetadataURL)
    cachedMetadataDownloadService = downloadService
    return downloadService
  }
}

/**
 * Returns a [CompletableFuture] that waits for [MaterialIconsMetadataDownloadService] to be refreshed on a background thread
 * and returns the **refreshed** metadata object. Note that it doesn't guarantee that the metadata was properly downloaded.
 */
private fun MaterialIconsMetadataDownloadService.refreshAndGetMetadata(fallbackMetadataURL: URL): CompletableFuture<MaterialIconsMetadata> {
  val semaphore = Semaphore().apply { down() }
  val onComplete = semaphore::up
  refresh(onComplete, onComplete)

  return CompletableFuture.supplyAsync(Supplier {
    // Wait for async refresh to complete, then ask the service for the metadata.
    semaphore.waitFor(TimeUnit.MINUTES.toMillis(1L))
    return@Supplier getMetadata(this.getLatestMetadataUrl()).takeUnless { it === MaterialIconsMetadata.EMPTY }
                    // Fallback URL should not fail
                    ?: getMetadata(fallbackMetadataURL)
  }, AppExecutorUtil.getAppExecutorService())
}