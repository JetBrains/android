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

import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.appinspection.inspector.api.awaitForDisposal
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTab
import com.android.tools.idea.appinspection.inspector.ide.FrameworkInspectorLaunchParams
import com.android.tools.idea.appinspection.inspector.ide.SingleAppInspectorTab
import com.android.tools.idea.appinspection.inspector.ide.SingleAppInspectorTabProvider
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.sqlite.databaseConnection.live.LiveDatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.live.handleError
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.settings.DatabaseInspectorSettings
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import icons.StudioIcons
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.ide.PooledThreadExecutor
import javax.swing.Icon
import javax.swing.JComponent

class DatabaseInspectorTabProvider : SingleAppInspectorTabProvider() {
  companion object {
    const val DATABASE_INSPECTOR_ID = "androidx.sqlite.inspection"
  }

  override val inspectorId = DATABASE_INSPECTOR_ID
  override val displayName = "Database Inspector"
  override val icon: Icon = StudioIcons.Shell.ToolWindows.DATABASE_INSPECTOR

  override val inspectorLaunchParams = FrameworkInspectorLaunchParams(
    AppInspectorJar(
      name = "sqlite-inspection.jar",
      developmentDirectory = "prebuilts/tools/common/app-inspection/androidx/sqlite/",
      releaseDirectory = "plugins/android/resources/app-inspection/"
    )
  )

  override fun isApplicable(): Boolean {
    return true
  }

  override fun supportsOffline() = DatabaseInspectorSettings.getInstance().isOfflineModeEnabled

  override fun createTab(
    project: Project,
    ideServices: AppInspectionIdeServices,
    processDescriptor: ProcessDescriptor,
    messenger: AppInspectorMessenger,
    parentDisposable: Disposable
  ): AppInspectorTab {
    return object : SingleAppInspectorTab(messenger) {
      private val taskExecutor = PooledThreadExecutor.INSTANCE
      private val errorsSideChannel = createErrorSideChannel(project)
      private val databaseInspectorProjectService = DatabaseInspectorProjectService.getInstance(project)
      private val openDatabase: (SqliteDatabaseId, LiveDatabaseConnection) -> Unit = { databaseId, databaseConnection ->
        databaseInspectorProjectService.openSqliteDatabase(databaseId, databaseConnection)
      }

      private val handleError: (String) -> Unit = { databaseInspectorProjectService.handleError(it, null) }
      private val onDatabasePossiblyChanged: () -> Unit = { databaseInspectorProjectService.databasePossiblyChanged() }
      private val onDatabaseClosed: (SqliteDatabaseId) -> Unit = { databaseId ->
        databaseInspectorProjectService.handleDatabaseClosed(databaseId)
      }

      private val dbClient = DatabaseInspectorClient(
        messenger,
        parentDisposable,
        handleError,
        openDatabase,
        onDatabasePossiblyChanged,
        onDatabaseClosed,
        taskExecutor,
        databaseInspectorProjectService.projectScope,
        errorsSideChannel
      )

      override val component: JComponent = databaseInspectorProjectService.sqliteInspectorComponent

      init {
        databaseInspectorProjectService.projectScope.launch {
          databaseInspectorProjectService.startAppInspectionSession(dbClient, ideServices, processDescriptor)
          dbClient.startTrackingDatabaseConnections()
          messenger.awaitForDisposal()
          withContext(AndroidDispatchers.uiThread) {
            databaseInspectorProjectService.stopAppInspectionSession(processDescriptor)
          }
        }
      }
    }
  }
}

fun createErrorSideChannel(project: Project): ErrorsSideChannel = { command, errorResponse ->
  handleError(project, command, errorResponse.content, logger<DatabaseInspectorMessenger>())
}

/**
 * Interface used to send commands to on-device inspector
 */
interface DatabaseInspectorClientCommandsChannel {
  fun keepConnectionsOpen(keepOpen: Boolean): ListenableFuture<Boolean?>
  fun acquireDatabaseLock(databaseId: Int): ListenableFuture<Int?>
  fun releaseDatabaseLock(lockId: Int): ListenableFuture<Unit>
}