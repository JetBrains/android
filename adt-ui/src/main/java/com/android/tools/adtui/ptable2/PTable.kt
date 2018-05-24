/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.adtui.ptable2

import com.android.tools.adtui.ptable2.impl.PTableImpl
import java.awt.Color
import java.awt.Font
import javax.swing.JComponent

interface PTable {

  val component: JComponent
  val itemCount: Int
  val activeFont: Font
  val backgroundColor: Color?
  val foregroundColor: Color
  var filter: String

  fun item(row: Int): PTableItem
  fun isExpanded(item: PTableGroupItem): Boolean

  companion object {
    fun create(tableModel: PTableModel,
               rendererProvider: PTableCellRendererProvider = DefaultPTableCellRendererProvider(),
               editorProvider: PTableCellEditorProvider = DefaultPTableCellEditorProvider()): PTable {
      return PTableImpl(tableModel, rendererProvider, editorProvider)
    }
  }
}

interface PTableCellRendererProvider : (PTable, PTableItem, PTableColumn) -> PTableCellRenderer

interface PTableCellEditorProvider : (PTable, PTableItem, PTableColumn) -> PTableCellEditor
