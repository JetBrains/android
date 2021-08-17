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
package com.android.tools.idea.naveditor.property.ui

import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.idea.common.model.NlComponent
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.components.JBList
import java.awt.BorderLayout
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.ListModel
import javax.swing.ListSelectionModel

/**
 * UI to display a list of NlComponents in the property inspector.
 * Parameters:
 * [model]: the list of NlComponents to display
 * [cellRenderer]: the renderer to apply to each list item
 */
class ComponentList(model: ListModel<NlComponent>, cellRenderer: ColoredListCellRenderer<NlComponent>)
  : AdtSecondaryPanel(BorderLayout()) {
  val list = JBList(model)

  init {
    list.isOpaque = false
    list.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
    list.fixedCellWidth = 1
    list.cellRenderer = cellRenderer

    list.addFocusListener(object : FocusAdapter() {
      override fun focusGained(event: FocusEvent?) {
        if (list.model.size > 0 && list.selectedIndex < 0) {
          list.selectedIndex = 0
        }
      }

      override fun focusLost(event: FocusEvent?) {
        list.clearSelection()
      }
    })

    add(list, BorderLayout.CENTER)
  }
}