/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.editors.strings.table.filter

import com.android.tools.idea.editors.strings.table.StringResourceTableModel
import com.android.tools.idea.editors.strings.table.StringResourceTableModel.UNTRANSLATABLE_COLUMN
import javax.swing.Icon
import javax.swing.RowFilter

/** A filter that decides which rows to show in the table. */
abstract class StringResourceTableRowFilter : RowFilter<StringResourceTableModel, Int>() {
  /** Returns an icon representing the filter, or `null` if none exists. */
  open fun getIcon(): Icon? = null
  /** Returns a string description of what the filter does, for use in the UI. */
  abstract fun getDescription(): String
  /** Returns a sequence of all text-like values in the row as [String]s. */
  protected fun Entry<out StringResourceTableModel, out Int>.stringValues(
      startIndex: Int = 0
  ): Sequence<String> =
      (startIndex until valueCount)
          .asSequence()
          .filter { it != UNTRANSLATABLE_COLUMN }
          .map(this::getStringValue)
}
