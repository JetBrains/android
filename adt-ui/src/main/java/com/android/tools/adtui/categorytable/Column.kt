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

import com.android.tools.adtui.event.DelegateMouseEventHandler
import com.intellij.ui.components.JBLabel
import javax.swing.JComponent

/**
 * Provides the UI for a column in a [CategoryTable].
 *
 * @param T the row type of the CategoryTable
 * @param C the type of the values this Column extracts from the row
 * @param U the JComponent used to render this value
 */
interface Column<T, C, U : JComponent> {
  /** The name of the column. */
  val name: String

  /** If overridden, an alternative name to be used in column headers. */
  val columnHeaderName: String
    get() = name

  /** Provides the value of this column for a given row value. */
  val attribute: Attribute<T, C>

  /** If true, the column remains displayed even when the table is grouped by this column. */
  val visibleWhenGrouped: Boolean
    get() = false

  /** Creates the JComponent for this column for a particular row. */
  fun createUi(rowValue: T): U

  /**
   * If the UI for this column has MouseListeners, this delegate should be installed as an
   * additional MouseListener if the standard mouse behavior for the row is still desired (i.e.,
   * selecting the row).
   */
  fun installMouseDelegate(component: U, mouseDelegate: DelegateMouseEventHandler) {}

  /**
   * Updates the UI based on the current value. Updating the UI only in response to this method
   * helps to ensure that the overall state of the table remains in sync.
   */
  fun updateValue(rowValue: T, component: U, value: C)

  val widthConstraint: SizeConstraint

  data class SizeConstraint(
    val min: Int = 0,
    val max: Int = Int.MAX_VALUE,
    val preferred: Int = max
  ) {
    init {
      check(preferred != Int.MAX_VALUE) {
        "Either max or preferred must be set to a non-default value"
      }
    }
    companion object {
      fun exactly(size: Int): SizeConstraint = SizeConstraint(size, size, size)
    }
  }
}

typealias ColumnList<T> = List<Column<T, *, *>>

/** A Column with UI provided by a JBLabel. */
open class LabelColumn<T>(
  override val name: String,
  override val widthConstraint: Column.SizeConstraint,
  override val attribute: Attribute<T, String>,
) : Column<T, String, JBLabel> {

  override fun createUi(rowValue: T) = JBLabel()

  // KT-39603
  override fun installMouseDelegate(component: JBLabel, mouseDelegate: DelegateMouseEventHandler) {}

  override fun updateValue(rowValue: T, component: JBLabel, value: String) {
    component.text = value
  }
}
