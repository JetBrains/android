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
package com.android.tools.idea.ui.resourcemanager.widget

import com.android.tools.adtui.font.FontUtil
import java.awt.Component
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

/**
 * CellRenderer that'll try to correctly render [String]s for different languages, by looking for appropriate fonts for it.
 *
 * @see FontUtil.getFontAbleToDisplay
 */
class I18nStringCellRenderer : DefaultTableCellRenderer() {
  override fun getTableCellRendererComponent(table: JTable?,
                                             value: Any?,
                                             isSelected: Boolean,
                                             hasFocus: Boolean,
                                             row: Int,
                                             column: Int): Component {
    val defaultRenderer =  super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
    val s = value.toString()
    table?.font?.let { defaultRenderer.font = FontUtil.getFontAbleToDisplay(s, it) }
    return defaultRenderer
  }
}