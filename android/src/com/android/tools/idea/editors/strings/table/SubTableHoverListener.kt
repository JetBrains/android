/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.editors.strings.table

import com.intellij.ui.hover.TableHoverListener
import com.intellij.ui.table.JBTable
import javax.swing.JTable

/**
 * Install a hover listener that allows the hover row to be synchronized between [frozenTable] and [scrollableTable].
 */
@Suppress("UnstableApiUsage")
class SubTableHoverListener(private val frozenTable: JBTable, private val scrollableTable: JBTable) : TableHoverListener() {
  private val delegate = DEFAULT as TableHoverListener

  fun install() {
    DEFAULT.removeFrom(frozenTable)
    DEFAULT.removeFrom(scrollableTable)
    addTo(frozenTable)
    addTo(scrollableTable)
  }

  override fun onHover(table: JTable, row: Int, column: Int) {
    delegate.onHover(frozenTable, row, column)
    delegate.onHover(scrollableTable, row, column)
  }
}
