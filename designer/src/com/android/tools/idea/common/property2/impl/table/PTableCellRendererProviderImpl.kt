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
package com.android.tools.idea.common.property2.impl.table

import com.android.tools.adtui.ptable2.*
import com.android.tools.idea.common.property2.api.*

/**
 * Standard table cell renderer provider.
 *
 * Returns (cached) a table cell renderer based on the column displayed.
 */
class PTableCellRendererProviderImpl<N : NewPropertyItem, P : PropertyItem>(
  nameType: Class<N>,
  nameControlTypeProvider: ControlTypeProvider<N>,
  nameEditorProvider: EditorProvider<N>,
  valueType: Class<P>,
  valueControlTypeProvider: ControlTypeProvider<P>,
  valueEditorProvider: EditorProvider<P>) : PTableCellRendererProvider {

  private val defaultNameRenderer = DefaultNameTableCellRenderer()
  private val defaultValueRenderer = DefaultValueTableCellRenderer()
  private val nameRenderer = EditorBasedTableCellRenderer(nameType, nameControlTypeProvider, nameEditorProvider, defaultNameRenderer)
  private val valueRenderer = EditorBasedTableCellRenderer(valueType, valueControlTypeProvider, valueEditorProvider, defaultValueRenderer)

  override fun invoke(table: PTable, item: PTableItem, column: PTableColumn): PTableCellRenderer {
    return when (column) {
      PTableColumn.NAME -> nameRenderer
      PTableColumn.VALUE -> valueRenderer
    }
  }
}
