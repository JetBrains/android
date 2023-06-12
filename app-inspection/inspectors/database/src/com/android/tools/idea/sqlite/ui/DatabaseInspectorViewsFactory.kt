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
import com.android.tools.idea.sqlite.ui.exportToFile.ExportToFileDialogView
import com.android.tools.idea.sqlite.ui.mainView.DatabaseInspectorView
import com.android.tools.idea.sqlite.ui.parametersBinding.ParametersBindingDialogView
import com.android.tools.idea.sqlite.ui.sqliteEvaluator.SqliteEvaluatorView
import com.android.tools.idea.sqlite.ui.tableView.TableView
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job

interface DatabaseInspectorViewsFactory {
  fun createTableView(): TableView

  fun createEvaluatorView(
    project: Project,
    schemaProvider: SchemaProvider,
    tableView: TableView
  ): SqliteEvaluatorView

  fun createParametersBindingView(
    project: Project,
    sqliteStatementText: String
  ): ParametersBindingDialogView

  fun createExportToFileView(
    project: Project,
    params: ExportDialogParams,
    analyticsTracker: DatabaseInspectorAnalyticsTracker
  ): ExportToFileDialogView

  fun createExportInProgressView(
    project: Project,
    job: Job,
    taskDispatcher: CoroutineDispatcher
  ): ExportInProgressView

  fun createDatabaseInspectorView(project: Project): DatabaseInspectorView
}
