/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.componenttree.treetable

import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.componenttree.api.BadgeItem
import com.intellij.ui.ColoredTableCellRenderer
import com.intellij.util.ui.UIUtil
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

/**
 * Renderer used for each [BadgeItem] specified.
 */
class BadgeRenderer(val badge: BadgeItem) : TableCellRenderer, ColoredTableCellRenderer() {

  override fun customizeCellRenderer(
    table: JTable,
    value: Any?,
    selected: Boolean,
    hasFocus: Boolean,
    row: Int,
    column: Int
  ) {
    val focused = selected && table.hasFocus()
    background = UIUtil.getTreeBackground(selected, focused)
    icon = value?.let { badge.getIcon(it) }?.let { if (focused) ColoredIconGenerator.generateWhiteIcon(it) else it }
    isTransparentIconBackground = true
  }
}
