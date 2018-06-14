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
import com.intellij.ui.SimpleColoredComponent
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Insets
import javax.swing.JComponent
import kotlin.math.max

private const val LEFT_ICON_AREA = 20
private const val ICON_CENTER_X_POS = 12
private const val MIN_SPACING = 2

/**
 * Table cell renderer for the name of a [PTableItem].
 */
class NameTableCellRenderer : SimpleColoredComponent(), PTableCellRenderer {
  private val leftIconArea = JBUI.scale(LEFT_ICON_AREA)
  private val iconCenterXPos = JBUI.scale(ICON_CENTER_X_POS)
  private val minSpacing = JBUI.scale(MIN_SPACING)

  override fun getEditorComponent(table: PTable, item: PTableItem, column: PTableColumn,
                                  isSelected: Boolean, hasFocus: Boolean): JComponent {
    clear()
    append(item.name)
    setPaintFocusBorder(false)
    setFocusBorderAroundIcon(true)
    var indent = leftIconArea
    if (item is PTableGroupItem) {
      icon = UIUtil.getTreeNodeIcon(table.isExpanded(item), isSelected, hasFocus)
      indent = max(iconCenterXPos - icon.iconWidth / 2, minSpacing)
      iconTextGap = max(leftIconArea - indent - icon.iconWidth, minSpacing)
    }
    ipad = Insets(0, indent, 0, 0)
    return this
  }
}
