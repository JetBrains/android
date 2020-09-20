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

import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.sqlite.model.DatabaseFileData
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.isInMemoryDatabase
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Class used to download files to enter offline mode.
 */
class OfflineModeManager(
  project: Project,
  private val fileDatabaseManager: FileDatabaseManager
) {
  private val databaseInspectorAnalyticsTracker = DatabaseInspectorAnalyticsTracker.getInstance(project)

  /**
   * Downloads files for all [SqliteDatabaseId.LiveSqliteDatabaseId] databases.
   */
  fun downloadFiles(
    databases: List<SqliteDatabaseId>,
    processDescriptor: ProcessDescriptor,
    appPackageName: String?,
    handleError: (String, Throwable?) -> Unit
  ): Flow<DownloadProgress> {
    return flow {
      val downloadedFiles = mutableListOf<DatabaseFileData>()

      val databasesToDownload = databases
        .filterIsInstance<SqliteDatabaseId.LiveSqliteDatabaseId>()
        .filter { !it.isInMemoryDatabase() }

      try {
        emit(DownloadProgress(DownloadState.IN_PROGRESS, emptyList(), databasesToDownload.size))

        databasesToDownload.forEach { liveSqliteDatabaseId ->
          try {
            val databaseFileData = fileDatabaseManager.loadDatabaseFileData(
              appPackageName ?: processDescriptor.processName,
              processDescriptor,
              liveSqliteDatabaseId
            )
            downloadedFiles.add(databaseFileData)
            emit(DownloadProgress(DownloadState.IN_PROGRESS, downloadedFiles.toList(), databasesToDownload.size))
          } catch (e: FileDatabaseException) {
            databaseInspectorAnalyticsTracker.trackOfflineDatabaseDownloadFailed()
            handleError("Can't open offline database `${liveSqliteDatabaseId.path}`", e)
          }
        }
        emit(DownloadProgress(DownloadState.COMPLETED, downloadedFiles.toList(), databasesToDownload.size))
      } catch (e: CancellationException) {
        withContext(NonCancellable) {
          // databases won't be opened, therefore we need to delete files manually
          downloadedFiles.forEach { fileDatabaseManager.cleanUp(it) }
        }
        throw e
      }
    }
  }

  data class DownloadProgress(val downloadState: DownloadState, val filesDownloaded: List<DatabaseFileData>, val totalFiles: Int)
  enum class DownloadState {
    IN_PROGRESS, COMPLETED
  }
}