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

import com.android.tools.idea.sqlite.controllers.TabId
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.ui.logtab.LogTabView
import com.android.tools.idea.sqlite.ui.mainView.DatabaseInspectorView
import com.android.tools.idea.sqlite.ui.mainView.DatabaseInspectorView.Listener
import com.android.tools.idea.sqlite.ui.mainView.SchemaDiffOperation
import org.mockito.Mockito.mock
import java.util.ArrayList
import javax.swing.JComponent

open class MockDatabaseInspectorView : DatabaseInspectorView {
  val viewListeners = ArrayList<Listener>()
  var lastDisplayedResultSetTabId: TabId? = null

  override fun addListener(listener: Listener) {
    viewListeners.add(listener)
  }

  override fun removeListener(listener: Listener) {
    viewListeners.remove(listener)
  }

  override val component: JComponent = mock(JComponent::class.java)

  override fun getLogTabView(): LogTabView = mock(LogTabView::class.java)

  override fun startLoading(text: String) { }

  override fun stopLoading() { }

  override fun addDatabaseSchema(database: SqliteDatabase, schema: SqliteSchema, index: Int) { }

  override fun removeDatabaseSchema(database: SqliteDatabase) { }

  override fun openTab(tableId: TabId, tabName: String, component: JComponent) {
    lastDisplayedResultSetTabId = tableId
  }

  override fun reportSyncProgress(message: String) {}

  override fun focusTab(tabId: TabId) { }

  override fun closeTab(tabId: TabId) { }

  override fun reportError(message: String, t: Throwable) { }

  override fun updateDatabaseSchema(database: SqliteDatabase, diffOperations: List<SchemaDiffOperation>) { }
}