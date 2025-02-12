/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.settingssync

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.InputStreamContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File as DriveFile
import com.intellij.openapi.diagnostic.thisLogger
import java.io.InputStream

private const val TIMEOUT_MS = 5000
private const val FILE_COUNT_LIMIT = 10

/**
 * A client for interacting with Google Drive.
 *
 * This client provides functionalities to manage files on Google Drive, including:
 * - Writing content to a file.
 * - Reading the content of a file by its name.
 * - Deleting files.
 * - Retrieving the latest file metadata.
 * - Enforcing a limit on the number of files with the same name.
 */
class GoogleDriveClient(private val credentialProvider: () -> Credential) {
  private val jsonFactory: JsonFactory = GsonFactory()
  private val transport: HttpTransport = NetHttpTransport()
  private val drive: Drive = initDrive()

  private fun initDrive(): Drive {
    val initializer = HttpRequestInitializer { httpRequest ->
      httpRequest.apply {
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        headers = HttpHeaders()
        credentialProvider().initialize(this)
      }
    }

    return Drive.Builder(transport, jsonFactory, initializer)
      .apply { applicationName = "Google IDE Settings Sync" }
      .build()
  }

  /**
   * Writes the content of an InputStream to a file on Google Drive.
   *
   * @param filePath The name of the file to write on Google Drive.
   * @param content The InputStream containing the content to be written to the file.
   * @return The metadata of the newly created file on Google Drive.
   */
  fun write(filePath: String, content: InputStream): DriveFileMetadata {
    val fileContent = InputStreamContent("application/zip", content)

    val fileMetadata =
      DriveFile()
        .setName(filePath) // e.g. StudioSettingsSyncSnapshot.zip
        .setParents(listOf("appDataFolder"))

    val file = drive.files().create(fileMetadata, fileContent).setFields("id").execute()

    thisLogger()
      .info("Successfully write $filePath to the google drive, reference file id = ${file.id}")
    return DriveFileMetadata(versionId = file.id)
  }

  /**
   * Reads the content of a file from Google Drive.
   *
   * This function retrieves the latest version of the file with the given name from Google Drive
   * and returns its content as an InputStream.
   *
   * @param filePath The name of the file to read on Google Drive.
   * @return A Pair containing the following:
   *     - A bytearray representing the content of the file, or empty if the file is not found.
   *     - The metadata of the file read from Google Drive.
   */
  fun read(filePath: String): Pair<ByteArray, DriveFileMetadata>? {
    val fileInfo = getLatestUpdatedFileMetadata(filePath) ?: return null

    return Pair(readByFileId(fileInfo.versionId), fileInfo)
  }

  /**
   * Deletes all files on Google Drive with a name matching the given file path.
   *
   * @param filePath The name of the file to delete on Google Drive.
   */
  fun delete(filePath: String) {
    getAllFileIds(filePath).forEach { deleteByFileId(it) }
  }

  /**
   * Retrieves information about the latest version of a file on Google Drive.
   *
   * This function searches for files on Google Drive with a name matching the provided file path.
   * It returns information about the most recently modified file found.
   *
   * @param filePath The File object representing the file to search for. The path of this file is
   *   used to find matching files on Google Drive.
   * @return The metadata of the most recently updated file in Google Drive.
   */
  fun getLatestUpdatedFileMetadata(filePath: String): DriveFileMetadata? {
    // Search by file name
    val file = listAllFilesLatestFirst(filePath)?.firstOrNull() ?: return null
    return DriveFileMetadata(versionId = file.id)
  }

  /**
   * Ensures a limit on the number of files with the same name on Google Drive.
   *
   * This function retrieves all files on Google Drive with a name matching the provided file path,
   * sorted by modification time (latest first). If the number of files exceeds the
   * `FILE_COUNT_LIMIT`, it deletes the oldest files to enforce the limit.
   */
  fun deleteOldestFilesOverLimit(filePath: String) {
    val files = listAllFilesLatestFirst(filePath) ?: return
    if (files.size <= FILE_COUNT_LIMIT) return

    files.takeLast(files.size - FILE_COUNT_LIMIT).forEach { delete(it.id) }
  }

  private fun listAllFilesLatestFirst(filePath: String): List<DriveFile>? {
    return drive
      .files()
      .list()
      .setQ("name = '$filePath'")
      .setSpaces("appDataFolder")
      .setFields("nextPageToken, files(id, name, modifiedTime, version)")
      .setOrderBy("modifiedTime desc")
      .execute()
      .files
  }

  private fun readByFileId(fileId: String): ByteArray {
    return drive.files().get(fileId).executeMediaAsInputStream().readAllBytes()
  }

  private fun getAllFileIds(filePath: String): List<String> {
    // Search by file name
    val fileList =
      drive
        .files()
        .list()
        .setQ("name = '$filePath'")
        .setSpaces("appDataFolder")
        .setFields("nextPageToken, files(id, name, modifiedTime, version)")
        .execute()

    return fileList.files?.map { it.id } ?: emptyList()
  }

  private fun deleteByFileId(fileId: String) {
    drive.files().delete(fileId).execute()
  }
}

/**
 * Data class representing metadata about a file on Google Drive.
 *
 * @property versionId The ID of the file version.
 */
data class DriveFileMetadata(val versionId: String)
