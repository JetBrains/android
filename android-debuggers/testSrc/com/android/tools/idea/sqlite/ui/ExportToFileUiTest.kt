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
package com.android.tools.idea.sqlite.ui

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.stdui.CommonButton
import com.android.tools.idea.sqlite.DatabaseInspectorFlagController
import com.android.tools.idea.sqlite.ui.tableView.TableView
import com.android.tools.idea.sqlite.ui.tableView.TableViewImpl
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.LightPlatformTestCase
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.awt.event.ActionEvent
import javax.swing.AbstractButton

private const val EXPORT_TO_FILE_ENABLED_DEFAULT = false
private const val TABLE_ACTION_PANEL_COMPONENT_NAME = "table-actions-panel"
private const val EXPORT_BUTTON_COMPONENT_NAME = "export-button"

/** Test suite verifying Export-to-File feature's UI layer */
class ExportToFileUiTest : LightPlatformTestCase() {
  fun test_defaultFlagState() {
    assertThat(DatabaseInspectorFlagController.isExportToFileEnabled).isEqualTo(EXPORT_TO_FILE_ENABLED_DEFAULT)
  }

  fun test_tableView_exportButtonHiddenByDefault() {
    assertThat(findExportButtonInActionPanel(TableViewImpl())).isNull()
  }

  // verify that the button is visible with the flag on and that it calls the listener when clicked
  fun test_tableView_exportButton() = runWithFlagEnabled {
    // given
    val listener = mock(TableView.Listener::class.java)
    val tableView = TableViewImpl().also { it.addListener(listener) }
    val exportButton = findExportButtonInActionPanel(tableView)!!

    // when
    exportButton.simulateClick()

    // then
    verify(listener).showExportToFileDialogInvoked()
  }

  private fun findExportButtonInActionPanel(tableView: TableViewImpl): CommonButton? {
    val actionPanel = TreeWalker(tableView.component).descendants().first { it.name == TABLE_ACTION_PANEL_COMPONENT_NAME }
    return TreeWalker(actionPanel).descendants().filterIsInstance<CommonButton>().firstOrNull { it.name == EXPORT_BUTTON_COMPONENT_NAME }
  }

  private fun runWithFlagEnabled(block: () -> Unit) {
    val before = DatabaseInspectorFlagController.enableExportToFile(true)
    try {
      block()
    }
    finally {
      DatabaseInspectorFlagController.enableExportToFile(before)
    }
  }

  private fun AbstractButton.simulateClick() {
    this.actionListeners.forEach { it.actionPerformed(mock(ActionEvent::class.java)) }
  }
}
