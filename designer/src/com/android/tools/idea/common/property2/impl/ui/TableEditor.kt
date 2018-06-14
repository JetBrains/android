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
package com.android.tools.idea.common.property2.impl.ui

import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.adtui.ptable2.PTable
import com.android.tools.adtui.ptable2.PTableCellEditorProvider
import com.android.tools.adtui.ptable2.PTableCellRendererProvider
import com.android.tools.idea.common.property2.impl.model.TableLineModel

/**
 * A standard table control for editing multiple properties in a tabular form.
 */
class TableEditor(private val lineModel: TableLineModel,
                  rendererProvider: PTableCellRendererProvider,
                  editorProvider: PTableCellEditorProvider) {

  private val table = PTable.create(lineModel.tableModel, lineModel, rendererProvider, editorProvider)

  val component = table.component

  init {
    lineModel.addValueChangedListener(ValueChangedListener { handleValueChanged() })
  }

  private fun handleValueChanged() {
    component.isVisible = lineModel.visible
    table.filter = lineModel.filter
    if (lineModel.startEditing) {
      table.startNextEditor()
    }
  }
}
