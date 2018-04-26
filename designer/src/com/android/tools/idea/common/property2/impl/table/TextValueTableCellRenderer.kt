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

import com.android.tools.adtui.ptable2.PTable
import com.android.tools.adtui.ptable2.PTableCellRenderer
import com.android.tools.adtui.ptable2.PTableItem
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI

/**
 * A simple text cell renderer for displaying the value of a [PTableItem].
 */
class TextValueTableCellRenderer : PTableCellRenderer() {

  override fun customizeCellRenderer(table: PTable, item: PTableItem, selected: Boolean, hasFocus: Boolean) {
    append(item.value.orEmpty())

    // Display a left border for a middle grid line.
    // The vertical grid lines are not used because those grid lines will
    // always include a line to the right of the value column.
    border = JBUI.Borders.customLine(JBColor.border(), 0, 1, 0, 0)
  }
}
