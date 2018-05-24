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

/**
 * A properties table with 2 columns: name and value.
 *
 * This interface hides the implementation detail of the properties table in [PTableImpl].
 * Expandable groups are supported.
 */
interface PTable {

  /** The table component */
  val component: JComponent

  /** A context where the consumer can store a reference to a model that otherwise would be hard to identify */
  val context: Any?

  /** Returns the number of items in the table. Note this will change after a group is expanded/collapsed */
  val itemCount: Int

  /** The font used in the table */
  val activeFont: Font

  /** The background color used in the table */
  val backgroundColor: Color?

  /** The font color used in the table */
  val foregroundColor: Color

  /** The filter used to limit the items shown in the table. Use the empty string for no filter. */
  var filter: String

  /** Returns the item at the specified [row] */
  fun item(row: Int): PTableItem

  /** Returns true if the item is currently expanded. */
  fun isExpanded(item: PTableGroupItem): Boolean

  /** Stops editing the current row and starts editing in the next row. */
  fun startNextEditor(): Boolean

  companion object {
    fun create(tableModel: PTableModel,
               context: Any? = null,
               rendererProvider: PTableCellRendererProvider = DefaultPTableCellRendererProvider(),
               editorProvider: PTableCellEditorProvider = DefaultPTableCellEditorProvider()): PTable {
      return PTableImpl(tableModel, context, rendererProvider, editorProvider)
    }
  }
}

/** A provider for [PTableCellRenderer] which should be used for a certain cell in the table */
interface PTableCellRendererProvider : (PTable, PTableItem, PTableColumn) -> PTableCellRenderer

/** A provider for [PTableCellEditor] which should be used for a certain cell in the table */
interface PTableCellEditorProvider : (PTable, PTableItem, PTableColumn) -> PTableCellEditor
