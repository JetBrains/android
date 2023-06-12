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
package com.android.tools.idea.sqlite.mocks

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.sqlite.DatabaseInspectorAnalyticsTracker
import com.android.tools.idea.sqlite.SchemaProvider
import com.android.tools.idea.sqlite.model.ExportDialogParams
import com.android.tools.idea.sqlite.ui.DatabaseInspectorViewsFactory
import com.android.tools.idea.sqlite.ui.exportToFile.ExportInProgressView
import com.android.tools.idea.sqlite.ui.exportToFile.ExportToFileDialogView
import com.android.tools.idea.sqlite.ui.sqliteEvaluator.SqliteEvaluatorView
import com.android.tools.idea.sqlite.ui.tableView.TableView
import com.intellij.openapi.project.Project
import javax.swing.JComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import org.mockito.Mockito.spy

open class FakeDatabaseInspectorViewsFactory : DatabaseInspectorViewsFactory {
  val sqliteEvaluatorView: FakeSqliteEvaluatorView = spy(FakeSqliteEvaluatorView::class.java)
  val tableView: FakeTableView = spy(FakeTableView())
  val parametersBindingDialogView: FakeParametersBindingDialogView =
    spy(FakeParametersBindingDialogView())
  val databaseInspectorView: FakeDatabaseInspectorView = spy(FakeDatabaseInspectorView())
  private val exportToFileDialogView: ExportToFileDialogView = mock()
  private val exportInProgressView: ExportInProgressView = mock()

  init {
    whenever(tableView.component).thenReturn(mock<JComponent>())
    whenever(sqliteEvaluatorView.tableView).thenReturn(tableView)
  }

  override fun createTableView(): TableView = tableView

  override fun createEvaluatorView(
    project: Project,
    schemaProvider: SchemaProvider,
    tableView: TableView
  ): SqliteEvaluatorView = sqliteEvaluatorView

  override fun createParametersBindingView(project: Project, sqliteStatementText: String) =
    parametersBindingDialogView

  override fun createDatabaseInspectorView(project: Project) = databaseInspectorView

  override fun createExportToFileView(
    project: Project,
    params: ExportDialogParams,
    analyticsTracker: DatabaseInspectorAnalyticsTracker
  ): ExportToFileDialogView = exportToFileDialogView

  override fun createExportInProgressView(
    project: Project,
    job: Job,
    taskDispatcher: CoroutineDispatcher
  ) = exportInProgressView
}
