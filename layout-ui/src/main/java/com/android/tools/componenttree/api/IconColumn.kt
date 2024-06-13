/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.componenttree.treetable.BadgeRenderer
import com.intellij.util.ui.EmptyIcon
import java.awt.Rectangle
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.table.TableCellRenderer

/**
 * Creates an [IconColumn]
 *
 * @param name the name of the column
 * @param getter extractor of the [Icon] from the given tree item [T].
 * @param hoverIcon extractor of the hover [Icon] from a given tree item [T].
 * @param action to be performed on a single click on the column.
 * @param popup action to be performed on mouse popup on the column.
 * @param tooltip tooltip to show when hovering over the column.
 * @param leftDivider show a divider line to the left of the column.
 * @param emptyIcon default icon to be used when no icon is available for the column.
 *
 * The [emptyIcon] is used to determine the width of the column and should be specified if the icons
 * from [getter] and [hoverIcon] are not of size 16*16.
 *
 * Warning: Use this if the component tree only has data in this column from a single [NodeType]. If
 * multiple [NodeType]s should show data in this column, then a custom implementation of [BadgeItem]
 * should be created possibly using [IconColumn].
 */
inline fun <reified T> createIconColumn(
  name: String,
  noinline getter: (T) -> Icon?,
  noinline hoverIcon: (T) -> Icon? = { null },
  noinline action: (item: T, component: JComponent, bounds: Rectangle) -> Unit = { _, _, _ -> },
  noinline popup: (item: T, component: JComponent, x: Int, y: Int) -> Unit = { _, _, _, _ -> },
  noinline tooltip: (item: T) -> String? = { _ -> null },
  leftDivider: Boolean = false,
  emptyIcon: Icon = EmptyIcon.ICON_16,
  headerRenderer: TableCellRenderer? = null,
): BadgeItem =
  SingleTypeIconColumn(
    name,
    T::class.java,
    getter,
    hoverIcon,
    action,
    popup,
    tooltip,
    leftDivider,
    emptyIcon,
    headerRenderer,
  )

/**
 * A [BadgeItem] implementation.
 *
 * See the parameters and warning for [createIconColumn].
 */
class SingleTypeIconColumn<T>(
  name: String,
  private val itemClass: Class<T>,
  private val getter: (T) -> Icon?,
  private val hoverIcon: (T) -> Icon?,
  private val action: (item: T, component: JComponent, bounds: Rectangle) -> Unit,
  private val popup: (item: T, component: JComponent, x: Int, y: Int) -> Unit,
  private val tooltip: (item: T) -> String?,
  override val leftDivider: Boolean,
  emptyIcon: Icon,
  override val headerRenderer: TableCellRenderer?,
) : IconColumn(name, emptyIcon) {

  override fun getIcon(item: Any): Icon? = cast(item)?.let { getter(it) }

  override fun getHoverIcon(item: Any): Icon? = cast(item)?.let { hoverIcon(it) }

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
 * A [ColumnInfo] that contains a single icon.
 *
 * The [emptyIcon] is used to determine the width of the column and should be specified if the icons
 * from [getIcon] and [getHoverIcon] are not of size 16*16.
 */
abstract class IconColumn(
  override val name: String,
  private val emptyIcon: Icon = EmptyIcon.ICON_16,
) : BadgeItem {

  override var renderer: BadgeRenderer? = null

  override var width = computeWidth()

  override fun updateUI() {
    renderer = BadgeRenderer(this, emptyIcon)
    width = computeWidth()
  }

  private fun computeWidth(): Int {
    val renderer = renderer ?: BadgeRenderer(this, emptyIcon)
    renderer.icon = emptyIcon
    return renderer.preferredSize.width
  }
}
