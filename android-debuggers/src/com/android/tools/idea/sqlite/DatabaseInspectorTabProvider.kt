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

import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTab
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTabProvider
import com.intellij.openapi.project.Project
import javax.swing.JComponent

class DatabaseInspectorTabProvider : AppInspectorTabProvider {
  override val inspectorId = "androidx.sqlite.inspection"
  override val displayName = "Database Inspector"
  override val inspectorAgentJar = AppInspectorJar(
    name = "sqlite-inspection.jar",
    developmentDirectory = "../../prebuilts/tools/common/app-inspection/androidx/sqlite/",
    releaseDirectory = "plugins/android/resources/app-inspection/"
  )

  override fun isApplicable(): Boolean {
    return DatabaseInspectorFlagController.isFeatureEnabled
  }

  override fun createTab(project: Project, messenger: AppInspectorClient.CommandMessenger): AppInspectorTab {
    return object : AppInspectorTab {

      private val databaseInspectorProjectService = DatabaseInspectorProjectService.getInstance(project)
      private val openDatabase: (AppInspectorClient.CommandMessenger, Int, String) -> Unit = { messenger, databaseId, databaseName ->
        databaseInspectorProjectService.openSqliteDatabase(messenger, databaseId, databaseName)
      }

      private val handleError: (String) -> Unit = { databaseInspectorProjectService.handleError(it, null) }

      override val client = DatabaseInspectorClient(messenger, handleError, openDatabase)
      override val component: JComponent = databaseInspectorProjectService.sqliteInspectorComponent

      init {
        client.startTrackingDatabaseConnections()
      }
    }
  }
}