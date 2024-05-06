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
import com.android.tools.idea.sqlite.ui.mainView.DatabaseDiffOperation
import com.android.tools.idea.sqlite.ui.mainView.DatabaseInspectorView
import com.android.tools.idea.sqlite.ui.mainView.DatabaseInspectorView.Listener
import com.android.tools.idea.sqlite.ui.mainView.SchemaDiffOperation
import com.android.tools.idea.sqlite.ui.mainView.ViewDatabase
import java.util.ArrayList
import javax.swing.Icon
import javax.swing.JComponent
import org.mockito.Mockito.mock

open class FakeDatabaseInspectorView : DatabaseInspectorView {
  val viewListeners = ArrayList<Listener>()
  var lastDisplayedResultSetTabId: TabId? = null

  val errorInvocations = mutableListOf<Pair<String, Throwable?>>()

  override fun addListener(listener: Listener) {
    viewListeners.add(listener)
  }

  override fun removeListener(listener: Listener) {
    viewListeners.remove(listener)
  }

  override val component: JComponent = mock(JComponent::class.java)

  override fun updateDatabases(databaseDiffOperations: List<DatabaseDiffOperation>) {}

  override fun updateDatabaseSchema(
    viewDatabase: ViewDatabase,
    diffOperations: List<SchemaDiffOperation>
  ) {}

  override fun openTab(tabId: TabId, tabName: String, tabIcon: Icon, component: JComponent) {
    lastDisplayedResultSetTabId = tabId
  }

  override fun reportSyncProgress(message: String) {}

  override fun focusTab(tabId: TabId) {}

  override fun closeTab(tabId: TabId) {}

  override fun updateKeepConnectionOpenButton(keepOpen: Boolean) {}

  override fun reportError(message: String, throwable: Throwable?) {
    errorInvocations.add(Pair(message, throwable))
  }

  override fun setRefreshButtonState(state: Boolean) {}

  override fun showEnterOfflineModePanel(filesDownloaded: Int, totalFilesToDownload: Int) {}

  override fun showOfflineModeUnavailablePanel() {}
}
