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
package com.android.tools.componenttree.api

import com.intellij.ui.components.JBLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer
import kotlin.math.absoluteValue

/**
 * Creates a [IntColumnInfo]
 *
 * @param name the name of the column
 * @param getter extractor of the [Int] from the given tree item [T].
 * @param getMax optional specify the largest (absolute) integer in the column.
 */
inline fun <reified T> createIntColumnInfo(name: String, noinline getter: (T) -> Int?, noinline getMax: () -> Int? = { null }): ColumnInfo =
  IntColumnInfo(name, T::class.java, getter, getMax)

/**
 * A [ColumnInfo] that contains right aligned integers.
 *
 * See the parameters for [createIntColumnInfo].
 */
class IntColumnInfo<T>(
  override val name: String,
  private val itemClass: Class<T>,
  private val getter: (T) -> Int?,
  private val getMaxInt: () -> Int?
) : ColumnInfo {
  override val width = -1

  override var renderer: TableCellRenderer = IntTableCellRenderer()
    private set

  override fun updateUI() {
    renderer = IntTableCellRenderer()
  }

  override fun computeWidth(table: JTable, data: Sequence<*>): Int {
    val high = getMaxInt() ?: maxAbsInt(data)
    val negativeHigh = if (high > 0) -high else high
    val number = StringBuilder()
    number.append(negativeHigh)
    number.forEachIndexed { i, _ -> number.setCharAt(i, '8') }
    val width = table.getFontMetrics(table.font).stringWidth(number.toString())
    return JBUIScale.scale(2) + width
  }

  private fun maxAbsInt(data: Sequence<*>): Int =
    data.filter { itemClass.isInstance(it) }.mapNotNull { itemClass.cast(it) }.maxOfOrNull { getter(it)?.absoluteValue ?: 0 } ?: 0

  private inner class IntTableCellRenderer : TableCellRenderer, JBLabel() {
    init {
      horizontalAlignment = JLabel.RIGHT
      border = JBUI.Borders.empty(0, 2)
    }

    override fun getTableCellRendererComponent(
      table: JTable,
      value: Any,
      isSelected: Boolean,
      hasFocus: Boolean,
      row: Int,
      column: Int
    ): Component {
      val intValue = if (itemClass.isInstance(value)) getter(itemClass.cast(value)).takeIf { it != 0 } else null
      val focused = table.hasFocus()
      text = intValue?.toString() ?: ""
      background = UIUtil.getTableBackground(isSelected, focused)
      foreground = UIUtil.getTableForeground(isSelected, focused)
      return this
    }
  }
}
