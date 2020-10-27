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
package com.android.tools.idea.serverflags

import com.google.common.io.ByteStreams
import java.io.File
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Path

/**
 * ServerFlagDownloader downloads the protobuf file for the current version of Android Studio.
 * If it succeeds it will save the file to a local path.
 */
class ServerFlagDownloader {
  companion object {
    /**
     * Download the server flag list
     * @param baseUrl: The base url where the download files are located.
     * @param localCacheDirectory: The local directory to store the most recent download.
     * @param version: The current version of Android Studio. This is used to construct the full paths from the first two parameters.
     * a given flag is enabled.
     */
    fun downloadServerFlagList(baseUrl: String, localCacheDirectory: Path, version: String) {
      val url = buildUrl(baseUrl, version) ?: return
      val tempFile = downloadFile(url) ?: return
      // check whether file is valid before saving
      unmarshalFlagList(tempFile) ?: return

      val localFilePath = buildLocalFilePath(localCacheDirectory, version)
      saveFile(tempFile, localFilePath.toFile())
    }

    private fun buildUrl(baseUrl: String, version: String): URL? {
      return try {
        URL("$baseUrl/$version/$FILE_NAME")
      }
      catch (e: MalformedURLException) {
        null
      }
    }

    private fun downloadFile(url: URL): File? {
      return try {
        val tempFile = File.createTempFile(DIRECTORY_PREFIX, "")
        url.openStream().use { inputStream ->
          tempFile.outputStream().use { outputStream ->
            ByteStreams.copy(inputStream, outputStream)
          }
        }
        tempFile
      }
      catch (e: IOException) {
        null
      }
    }

    private fun saveFile(tempFile: File, localFilePath: File) {
      try {
        tempFile.copyTo(localFilePath, true)
      }
      catch (e: IOException) {
      }
      finally {
        try {
          tempFile.delete()
        }
        catch (e: IOException) {
        }
      }
    }
  }
}