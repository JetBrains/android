/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.material.icons.metadata

import com.android.tools.idea.downloads.DownloadService
import com.android.tools.idea.material.icons.utils.MaterialIconsUtils.METADATA_FILE_NAME
import java.io.File
import java.net.URL

private const val METADATA_DOWNLOAD_URL = "http://fonts.google.com/metadata/icons"
private const val EXISTING_METADATA_FILE_NAME = METADATA_FILE_NAME
private const val DOWNLOADED_METADATA_FILE_NAME = "icons_metadata_temp.txt"

/**
 * Downloads the most recent Metadata for Material Icons from [METADATA_DOWNLOAD_URL].
 *
 * @param sdkTargetPath File path where the metadata file will be downloaded
 * @param existingMetadataUrl URL for existing metadata to use as fallback
 */
class MaterialIconsMetadataDownloadService(
  sdkTargetPath: File,
  existingMetadataUrl: URL
) : DownloadService(
  "Material Icons Metadata Downloader",
  METADATA_DOWNLOAD_URL,
  existingMetadataUrl,
  sdkTargetPath,
  DOWNLOADED_METADATA_FILE_NAME,
  EXISTING_METADATA_FILE_NAME
) {

  @Volatile
  private var latestMetadataUrl: URL = existingMetadataUrl

  fun getLatestMetadataUrl(): URL {
    return latestMetadataUrl
  }

  override fun loadFromFile(url: URL) {
    latestMetadataUrl = url
  }
}