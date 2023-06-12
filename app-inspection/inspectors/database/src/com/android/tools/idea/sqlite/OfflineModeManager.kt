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
import com.android.tools.idea.sqlite.localization.DatabaseInspectorBundle
import com.android.tools.idea.sqlite.model.DatabaseFileData
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.isInMemoryDatabase
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting

/** Class used to download files needed to enter offline mode. */
interface OfflineModeManager {
  fun downloadFiles(
    databases: List<SqliteDatabaseId>,
    processDescriptor: ProcessDescriptor,
    appPackageName: String?,
    handleError: (String, Throwable?) -> Unit
  ): Flow<DownloadProgress>

  data class DownloadProgress(
    val downloadState: DownloadState,
    val filesDownloaded: List<DatabaseFileData>,
    val totalFiles: Int
  )
  enum class DownloadState {
    IN_PROGRESS,
    COMPLETED
  }
}

class OfflineModeManagerImpl(
  private val project: Project,
  private val fileDatabaseManager: FileDatabaseManager,
  private val uiDispatcher: CoroutineContext,
  private val isFileDownloadAllowed: suspend () -> Boolean = {
    doIsFileDownloadAllowed(project, uiDispatcher)
  },
) : OfflineModeManager {
  private val databaseInspectorAnalyticsTracker =
    DatabaseInspectorAnalyticsTracker.getInstance(project)

  /** Downloads files for all [SqliteDatabaseId.LiveSqliteDatabaseId] databases. */
  override fun downloadFiles(
    databases: List<SqliteDatabaseId>,
    processDescriptor: ProcessDescriptor,
    appPackageName: String?,
    handleError: (String, Throwable?) -> Unit
  ): Flow<OfflineModeManager.DownloadProgress> {
    return flow {
      val downloadedFiles = mutableListOf<DatabaseFileData>()

      val databasesToDownload =
        databases.filterIsInstance<SqliteDatabaseId.LiveSqliteDatabaseId>().filter {
          !it.isInMemoryDatabase()
        }

      try {
        emit(
          OfflineModeManager.DownloadProgress(
            OfflineModeManager.DownloadState.IN_PROGRESS,
            emptyList(),
            databasesToDownload.size
          )
        )

        when {
          isFileDownloadAllowed() -> {
            downloadFiles(
              databasesToDownload,
              appPackageName,
              processDescriptor,
              downloadedFiles,
              handleError
            )
          }
          else -> {
            handleError(
              "For security reasons offline mode is disabled when " +
                "the process being inspected does not correspond to the project open in studio " +
                "or when the project has been generated from a prebuilt apk.",
              null
            )
          }
        }

        emit(
          OfflineModeManager.DownloadProgress(
            OfflineModeManager.DownloadState.COMPLETED,
            downloadedFiles.toList(),
            databasesToDownload.size
          )
        )
      } catch (e: CancellationException) {
        withContext(NonCancellable) {
          // databases won't be opened, therefore we need to delete files manually
          downloadedFiles.forEach { fileDatabaseManager.cleanUp(it) }
        }
        throw e
      }
    }
  }

  private suspend fun FlowCollector<OfflineModeManager.DownloadProgress>.downloadFiles(
    databasesToDownload: List<SqliteDatabaseId.LiveSqliteDatabaseId>,
    appPackageName: String?,
    processDescriptor: ProcessDescriptor,
    downloadedFiles: MutableList<DatabaseFileData>,
    handleError: (String, Throwable?) -> Unit
  ) {
    databasesToDownload.forEach { liveSqliteDatabaseId ->
      try {
        val databaseFileData =
          fileDatabaseManager.loadDatabaseFileData(
            appPackageName ?: processDescriptor.name,
            processDescriptor,
            liveSqliteDatabaseId
          )
        downloadedFiles.add(databaseFileData)
        emit(
          OfflineModeManager.DownloadProgress(
            OfflineModeManager.DownloadState.IN_PROGRESS,
            downloadedFiles.toList(),
            databasesToDownload.size
          )
        )
      } catch (e: FileDatabaseException) {
        databaseInspectorAnalyticsTracker.trackOfflineDatabaseDownloadFailed()
        handleError("Can't open offline database `${liveSqliteDatabaseId.path}`", e)
      } catch (e: DeviceNotFoundException) {
        handleError("Can't open offline database `${liveSqliteDatabaseId.path}`", e)
      }
    }
  }

  companion object {
    private const val PROJECT_TRUSTED_KEY = "PROJECT_TRUSTED_KEY"
    /**
     * Before downloading any database, ask the user if they trust the app. We're doing this because
     * downloading a db and running statements on it might result in executing malicious code.
     *
     * If user trusts the app we store the value as a project level property, so that we don't ask
     * each time.
     */
    @VisibleForTesting
    suspend fun doIsFileDownloadAllowed(
      project: Project,
      uiDispatcher: CoroutineContext,
      askUser: () -> Boolean = { askUserIfAppIsTrusted(project) }
    ) =
      withContext(uiDispatcher) {
        val isProjectTrusted =
          PropertiesComponent.getInstance(project).getBoolean(PROJECT_TRUSTED_KEY)

        if (isProjectTrusted) {
          return@withContext true
        }

        val userAnswer = askUser()

        if (userAnswer) {
          PropertiesComponent.getInstance(project).setValue(PROJECT_TRUSTED_KEY, true)
        }

        return@withContext userAnswer
      }

    private fun askUserIfAppIsTrusted(project: Project): Boolean {
      return MessageDialogBuilder.yesNo(
          DatabaseInspectorBundle.message("trust.database.title"),
          DatabaseInspectorBundle.message("trust.database.message")
        )
        .yesText(DatabaseInspectorBundle.message("trust.and.continue"))
        .noText(DatabaseInspectorBundle.message("dont.trust.app"))
        .asWarning()
        .ask(project)
    }
  }
}
