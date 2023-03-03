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

import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.event.MouseEvent
import javax.swing.SizeRequirements
import javax.swing.SwingUtilities
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
