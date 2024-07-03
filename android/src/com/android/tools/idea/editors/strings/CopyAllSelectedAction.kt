/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.editors.strings

import com.android.annotations.TestOnly
import com.android.tools.idea.editors.strings.table.FrozenColumnTable
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JMenuItem

/**
 * [Action] that copies all the columns from the currently selected rows in the [FrozenColumnTable].
 *
 * For testing a [sendToClipboard] handler method can be passed to handle the paste action.
 */
internal class CopyAllSelectedAction
private constructor(
  private val table: FrozenColumnTable<*>,
  private val sendToClipboard: (String) -> Unit,
) : AbstractAction() {
  fun update(copyAllMenuItem: JMenuItem) {
    copyAllMenuItem.text = "Copy All Columns"
    copyAllMenuItem.isVisible = table.selectedRowCount > 0
  }

  override fun actionPerformed(e: ActionEvent) {
    val model = table.model
    val copyContent = StringBuilder()
    table.selectedModelRows.map { row ->
      val columnCount = model.columnCount
      val rowString = StringBuilder()
      for (column in 0 until columnCount) {
        rowString
          .append(table.model.getValueAt(row, column).toString())
          .append(if (column != columnCount - 1) "\t" else "")
      }
      if (rowString.isNotEmpty()) copyContent.appendLine(rowString)
    }

    if (copyContent.isNotEmpty()) sendToClipboard(copyContent.toString())
  }

  companion object {
    @TestOnly
    fun forTesting(table: FrozenColumnTable<*>, copyContent: (String) -> Unit) =
      CopyAllSelectedAction(table, copyContent)

    @JvmStatic
    fun create(table: FrozenColumnTable<*>) =
      CopyAllSelectedAction(table) { CopyPasteManager.copyTextToClipboard(it) }
  }
}
