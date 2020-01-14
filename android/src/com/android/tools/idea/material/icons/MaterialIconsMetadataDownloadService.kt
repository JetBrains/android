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
package com.android.tools.idea.material.icons

import com.android.tools.idea.downloads.DownloadService
import com.android.tools.idea.material.icons.MaterialIconsUtils.METADATA_FILE_NAME
import com.intellij.openapi.diagnostic.Logger
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.URL

private const val METADATA_URL = "http://fonts.google.com/metadata/icons"
private const val EXISTING_METADATA_FILE_NAME = METADATA_FILE_NAME
private const val DOWNLOADED_METADATA_FILE_NAME = "icons_metadata_temp.txt"

private val LOG = Logger.getInstance(MaterialIconsMetadataDownloadService::class.java)

/**
 * Downloads the most recent Metadata for Material Icons from [METADATA_URL].
 *
 * TODO(141628234): Wrap in application service, so that it doesn't download the metadata file every time
 *
 * @param sdkTargetPath File path where the metadata file will be downloaded
 * @param existingMetadataUrl URL for existing metadata to use as fallback
 * @param metadataLoadedCallback Callback called when download is finished
 */
class MaterialIconsMetadataDownloadService(
  sdkTargetPath: File,
  existingMetadataUrl: URL,
  private val metadataLoadedCallback: (MaterialIconsMetadata?) -> Unit
) : DownloadService(
  "Material Icons Metadata Downloader",
  METADATA_URL,
  existingMetadataUrl,
  sdkTargetPath,
  DOWNLOADED_METADATA_FILE_NAME,
  EXISTING_METADATA_FILE_NAME
) {

  override fun loadFromFile(url: URL) {
    try {
      val reader = BufferedReader(InputStreamReader(url.openStream()))
      metadataLoadedCallback(MaterialIconsMetadata.parse(reader))
    }
    catch (e: Exception) {
      LOG.error("Failed to parse downloaded metadata file.", e)
      metadataLoadedCallback(null)
    }
  }
}