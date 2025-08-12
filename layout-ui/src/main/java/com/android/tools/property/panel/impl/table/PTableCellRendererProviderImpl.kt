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
package com.android.tools.property.panel.impl.table

import com.android.tools.property.panel.api.ControlTypeProvider
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.NewPropertyItem
import com.android.tools.property.panel.api.PropertyItem
import com.android.tools.property.ptable.PTable
import com.android.tools.property.ptable.PTableCellRenderer
import com.android.tools.property.ptable.PTableCellRendererProvider
import com.android.tools.property.ptable.PTableColumn
import com.android.tools.property.ptable.PTableItem
import com.intellij.util.ui.UIUtil

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
  valueEditorProvider: EditorProvider<P>,
) : PTableCellRendererProvider {

  private var defaultNameRenderer = DefaultNameTableCellRenderer()
  private var defaultValueRenderer = DefaultValueTableCellRenderer()
  private val nameRenderer =
    EditorBasedTableCellRenderer(
      nameType,
      nameControlTypeProvider,
      nameEditorProvider,
      UIUtil.FontSize.SMALL,
      defaultNameRenderer,
    )
  private val valueRenderer =
    EditorBasedTableCellRenderer(
      valueType,
      valueControlTypeProvider,
      valueEditorProvider,
      UIUtil.FontSize.NORMAL,
      defaultValueRenderer,
    )

  override fun invoke(table: PTable, item: PTableItem, column: PTableColumn): PTableCellRenderer {
    return when (column) {
      PTableColumn.NAME -> nameRenderer
      PTableColumn.VALUE -> valueRenderer
    }
  }

  override fun updateUI() {
    defaultNameRenderer = DefaultNameTableCellRenderer()
    defaultValueRenderer = DefaultValueTableCellRenderer()
    nameRenderer.updateUI(defaultNameRenderer)
    valueRenderer.updateUI(defaultValueRenderer)
  }
}
