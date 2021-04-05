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
package com.android.tools.idea.naveditor.property.ui

import javax.swing.table.AbstractTableModel

class DefaultValueTableModel(val list: List<DefaultValueModel>) : AbstractTableModel() {
  override fun getRowCount() = list.size
  override fun getColumnCount() = 3

  override fun getValueAt(rowIndex: Int, columnIndex: Int): String? {
    val defaultValueModel = list[rowIndex]
    return when (columnIndex) {
      0 -> defaultValueModel.name
      1 -> defaultValueModel.type
      else -> defaultValueModel.defaultValue
    }
  }

  override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
    val newValue = value as? String ?: return
    val defaultValueModel = list[rowIndex]
    defaultValueModel.defaultValue = newValue
  }

  override fun isCellEditable(rowIndex: Int, columnIndex: Int) = columnIndex == 2
}