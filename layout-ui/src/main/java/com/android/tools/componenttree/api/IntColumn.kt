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

import com.android.tools.componenttree.treetable.IntTableCellRenderer
import com.intellij.ui.components.JBLabel
import java.awt.Color
import java.awt.Rectangle
import java.lang.Integer.max
import javax.swing.JComponent
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

/**
 * Creates a [IntColumn]
 *
 * @param name the name of the column
 * @param getter extractor of the [Int] from the given tree item [T].
 * @param maxInt optional specify the largest (absolute) integer in the column.
 * @param action to be performed on a single click on the column.
 * @param popup action to be performed on mouse popup on the column.
 * @param tooltip tooltip to show when hovering over the column.
 * @param leftDivider show a divider line to the left of the column.
 *
 * Warning: Use this if the component tree only has data in this column from a single [NodeType]. If
 * multiple [NodeType]s should show data in this column, then a custom implementation of
 * [ColumnInfo] should be created possibly using [IntColumn].
 */
inline fun <reified T> createIntColumn(
  name: String,
  noinline getter: (T) -> Int?,
  noinline maxInt: () -> Int? = { null },
  noinline minInt: () -> Int? = { null },
  noinline action: (item: T, component: JComponent, bounds: Rectangle) -> Unit = { _, _, _ -> },
  noinline popup: (item: T, component: JComponent, x: Int, y: Int) -> Unit = { _, _, _, _ -> },
  noinline tooltip: (item: T) -> String? = { _ -> null },
  leftDivider: Boolean = false,
  foreground: Color? = null,
  headerRenderer: TableCellRenderer? = null,
): ColumnInfo =
  SingleTypeIntColumn(
    name,
    T::class.java,
    getter,
    maxInt,
    minInt,
    action,
    popup,
    tooltip,
    leftDivider,
    foreground,
    headerRenderer,
  )

/**
 * A [ColumnInfo] implementation with Int values.
 *
 * See the parameters for [createIconColumn].
 */
class SingleTypeIntColumn<T>(
  name: String,
  private val itemClass: Class<T>,
  private val getter: (T) -> Int?,
  private val getMaxInt: () -> Int?,
  private val getMinInt: () -> Int?,
  private val action: (item: T, component: JComponent, bounds: Rectangle) -> Unit,
  private val popup: (item: T, component: JComponent, x: Int, y: Int) -> Unit,
  private val tooltip: (item: T) -> String?,
  override val leftDivider: Boolean,
  override val foreground: Color?,
  override val headerRenderer: TableCellRenderer?,
) : IntColumn(name) {
  override fun getInt(item: Any): Int = cast(item)?.let { getter(it) } ?: 0

  override val maxInt: Int?
    get() = getMaxInt()

  override val minInt: Int?
    get() = getMinInt()

  override fun performAction(item: Any, component: JComponent, bounds: Rectangle) {
    cast(item)?.let { action(it, component, bounds) }
  }

  override fun showPopup(item: Any, component: JComponent, x: Int, y: Int) {
    cast(item)?.let { popup(it, component, x, y) }
  }

  override fun getTooltipText(item: Any): String? = cast(item)?.let { tooltip(it) }

  private fun cast(item: Any): T? = if (itemClass.isInstance(item)) itemClass.cast(item) else null
}

/**
 * A [ColumnInfo] that contains right aligned integers.
 *
 * Zero values are not shown.
 */
abstract class IntColumn(override val name: String) : ColumnInfo {
  abstract fun getInt(item: Any): Int

  open val maxInt: Int? = null

  open val minInt: Int? = null

  override val width = -1

  override var renderer: TableCellRenderer? = null

  override fun updateUI() {
    renderer = IntTableCellRenderer(this)
  }

  override fun computeWidth(table: JTable, data: Sequence<*>): Int {
    val high = StringBuilder((maxInt ?: maxInt(data)).toString())
    val low = StringBuilder((minInt ?: minInt(data)).toString())
    high.forEachIndexed { i, c -> if (Character.isDigit(c)) high.setCharAt(i, '8') }
    low.forEachIndexed { i, c -> if (Character.isDigit(c)) low.setCharAt(i, '8') }
    return max(widthOf(high), widthOf(low))
  }

  private fun widthOf(str: StringBuilder): Int {
    val renderer = renderer ?: IntTableCellRenderer(this)
    val component: JBLabel = renderer as JBLabel
    component.text = str.toString()
    return component.preferredSize.width
  }

  private fun maxInt(data: Sequence<*>): Int = data.filterNotNull().maxOfOrNull { getInt(it) } ?: 0

  private fun minInt(data: Sequence<*>): Int = data.filterNotNull().minOfOrNull { getInt(it) } ?: 0
}
