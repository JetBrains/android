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
package com.android.tools.idea.sqlite

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.file.explorer.toolwindow.fs.DeviceFileDownloaderService
import com.android.tools.idea.file.explorer.toolwindow.fs.DownloadProgress
import com.android.tools.idea.io.IdeFileService
import com.android.tools.idea.sqlite.model.DatabaseFileData
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job

/** Class responsible for downloading and deleting file database data */
interface FileDatabaseManager {
  /**
   * Downloads a local copy of the database passed as argument, from the device.
   * @throws IOException if the device corresponding to [processDescriptor] is not found
   * @throws FileNotFoundException if the database file is not found.
   */
  suspend fun loadDatabaseFileData(
    packageName: String,
    processDescriptor: ProcessDescriptor,
    databaseToDownload: SqliteDatabaseId.LiveSqliteDatabaseId
  ): DatabaseFileData

  /** Deletes the files associated to [databaseFileData]. */
  suspend fun cleanUp(databaseFileData: DatabaseFileData)
}

class FileDatabaseManagerImpl(
  private val project: Project,
  private val edtDispatcher: CoroutineDispatcher,
  private val deviceFileDownloaderService: DeviceFileDownloaderService =
    DeviceFileDownloaderService.getInstance(project),
) : FileDatabaseManager {

  override suspend fun loadDatabaseFileData(
    packageName: String,
    processDescriptor: ProcessDescriptor,
    databaseToDownload: SqliteDatabaseId.LiveSqliteDatabaseId
  ): DatabaseFileData {
    val path = databaseToDownload.path
    // Room uses write-ahead-log, so we need to download these additional files to open the db
    val pathsToDownload = listOf(path, "$path-shm", "$path-wal")

    val disposableDownloadProgress = DisposableDownloadProgress(coroutineContext[Job]!!)
    Disposer.register(project, disposableDownloadProgress)

    val files =
      try {
        // store files in Studio caches
        val downloadDestinationFolder = IdeFileService("database-inspector").cacheRoot
        deviceFileDownloaderService.downloadFiles(
          processDescriptor.device.serial,
          pathsToDownload,
          disposableDownloadProgress,
          downloadDestinationFolder
        )
      } catch (e: IllegalArgumentException) {
        throw DeviceNotFoundException(
          "Device '${processDescriptor.device.model} ${processDescriptor.device.serial}' not found.",
          e
        )
      } catch (e: DeviceFileDownloaderService.FileDownloadFailedException) {
        throw FileDatabaseException(e.message, e)
      }

    val mainFile =
      files[path]
        ?: throw FileDatabaseException("Can't download database '${databaseToDownload.path}'")
    val shmFile = files["$path-shm"]
    val walFile = files["$path-wal"]

    val additionalFiles = listOfNotNull(shmFile, walFile)
    return DatabaseFileData(mainFile, additionalFiles)
  }

  override suspend fun cleanUp(databaseFileData: DatabaseFileData) {
    val filesToClose =
      (listOf(databaseFileData.mainFile) + databaseFileData.walFiles).filter { it.exists() }
    deviceFileDownloaderService.deleteFiles(filesToClose)
  }

  private class DisposableDownloadProgress(private val coroutineJob: Job) :
    DownloadProgress, Disposable {
    private var isDisposed = false

    @AnyThread override fun isCancelled() = isDisposed || coroutineJob.isCancelled

    @UiThread override fun onStarting(entryFullPath: String) {}

    @UiThread
    override fun onProgress(entryFullPath: String, currentBytes: Long, totalBytes: Long) {}

    @UiThread override fun onCompleted(entryFullPath: String) {}

    override fun dispose() {
      isDisposed = true
    }
  }
}

class FileDatabaseException(override val message: String?, override val cause: Throwable? = null) :
  RuntimeException()

class DeviceNotFoundException(
  override val message: String?,
  override val cause: Throwable? = null
) : RuntimeException()
