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

import com.android.tools.idea.sqlite.SchemaProvider
import com.android.tools.idea.sqlite.ui.sqliteEvaluator.SqliteEvaluatorViewImpl
import com.android.tools.idea.sqlite.ui.tableView.TableViewImpl
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project

class SqliteEditorViewFactoryImpl : SqliteEditorViewFactory {

  companion object {
    @JvmStatic fun getInstance() = ServiceManager.getService(SqliteEditorViewFactoryImpl::class.java)!!
  }

  override fun createEvaluatorView(project: Project, schemaProvider: SchemaProvider) = SqliteEvaluatorViewImpl(project, schemaProvider)

  override fun createTableView() = TableViewImpl()
}