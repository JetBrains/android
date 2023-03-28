/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.adtui.categorytable

import com.android.annotations.concurrency.UiThread
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SizeRequirements
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.border.Border
import javax.swing.table.TableColumn
import javax.swing.table.TableColumnModel

// General-purpose Swing utilities used by CategoryTable. These could be reused by other components
// after finding a more appropriate location for them.

/** Provides a List view of the TableColumns of a TableColumnModel. */
internal val TableColumnModel.columnList: List<TableColumn>
  get() =
    object : AbstractList<TableColumn>() {
      override val size = columnCount
      override fun get(index: Int): TableColumn = getColumn(index)
    }

@get:UiThread
internal val Container.componentList: List<Component>
  get() =
    object : AbstractList<Component>() {
      override val size = componentCount
      override fun get(index: Int): Component = getComponent(index)
    }

internal fun Component.heightRequirements() =
  SizeRequirements(minimumSize.height, preferredSize.height, maximumSize.height, alignmentY)

/**
 * Forwards a MouseEvent received by [from] to [to]. This allows a mouse event to be processed by
 * more than one component, if the [from] component cooperates by forwarding it.
 */
internal fun MouseEvent.forward(from: Component, to: Component) {
  to.dispatchEvent(SwingUtilities.convertMouseEvent(from, this, to))
}

/** Adds the given Container's insets to this dimension. */
internal fun Dimension.addInsets(container: Container) {
  val insets = container.insets
  width += insets.left + insets.right
  height += insets.top + insets.bottom
}

/** Sets minimum, maximum, and preferred sizes to the given value. */
fun Component.constrainSize(size: Dimension) {
  maximumSize = size
  minimumSize = size
  preferredSize = size
}

/**
 * Adds a focus listener using a single lambda, rather than the redundant two-method FocusListener
 * interface. (Whether focus is gained or lost can be checked by comparing [FocusEvent.id] to
 * [FocusEvent.FOCUS_GAINED], or by calling [Component.isFocusOwner].)
 */
internal fun JComponent.addFocusListener(listener: (FocusEvent) -> Unit) {
  addFocusListener(
    object : FocusListener {
      override fun focusGained(e: FocusEvent) {
        listener(e)
      }
      override fun focusLost(e: FocusEvent) {
        listener(e)
      }
    }
  )
}

/** Returns the appropriate Border for a table cell given its focus and row selection state. */
internal fun tableCellBorder(selected: Boolean, focused: Boolean): Border? =
  UIManager.getBorder(
    when {
      focused && selected -> "Table.focusSelectedCellHighlightBorder"
      focused -> "Table.focusCellHighlightBorder"
      else -> "Table.cellNoFocusBorder"
    }
  )
