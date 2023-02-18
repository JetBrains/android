/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.sqlite.ui

import com.android.tools.idea.sqlite.DatabaseInspectorAnalyticsTracker
import com.android.tools.idea.sqlite.SchemaProvider
import com.android.tools.idea.sqlite.model.ExportDialogParams
import com.android.tools.idea.sqlite.ui.exportToFile.ExportInProgressView
import com.android.tools.idea.sqlite.ui.exportToFile.ExportInProgressViewImpl
import com.android.tools.idea.sqlite.ui.exportToFile.ExportToFileDialogView
import com.android.tools.idea.sqlite.ui.exportToFile.ExportToFileDialogViewImpl
import com.android.tools.idea.sqlite.ui.mainView.DatabaseInspectorViewImpl
import com.android.tools.idea.sqlite.ui.parametersBinding.ParametersBindingDialogViewImpl
import com.android.tools.idea.sqlite.ui.sqliteEvaluator.SqliteEvaluatorViewImpl
import com.android.tools.idea.sqlite.ui.tableView.TableView
import com.android.tools.idea.sqlite.ui.tableView.TableViewImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job

class DatabaseInspectorViewsFactoryImpl : DatabaseInspectorViewsFactory {
  companion object {
    @JvmStatic
    fun getInstance() =
      ApplicationManager.getApplication()
        .getService(DatabaseInspectorViewsFactoryImpl::class.java)!!
  }

  override fun createTableView() = TableViewImpl()

  override fun createEvaluatorView(
    project: Project,
    schemaProvider: SchemaProvider,
    tableView: TableView
  ) = SqliteEvaluatorViewImpl(project, tableView, schemaProvider)

  override fun createParametersBindingView(project: Project, sqliteStatementText: String) =
    ParametersBindingDialogViewImpl(sqliteStatementText, project, true)

  override fun createExportToFileView(
    project: Project,
    params: ExportDialogParams,
    analyticsTracker: DatabaseInspectorAnalyticsTracker
  ): ExportToFileDialogView = ExportToFileDialogViewImpl(project, params)

  override fun createExportInProgressView(
    project: Project,
    job: Job,
    taskDispatcher: CoroutineDispatcher
  ): ExportInProgressView = ExportInProgressViewImpl(project, job, taskDispatcher)

  override fun createDatabaseInspectorView(project: Project) =
    DatabaseInspectorViewImpl(project, project)
}
