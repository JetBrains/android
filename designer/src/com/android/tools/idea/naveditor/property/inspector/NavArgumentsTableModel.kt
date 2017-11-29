// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.naveditor.property.inspector

import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.naveditor.property.NavArgumentsProperty
import javax.swing.table.AbstractTableModel

open class NavArgumentsTableModel(private val argumentsProperty: NavArgumentsProperty) : AbstractTableModel() {
  override fun getRowCount() = argumentsProperty.properties.size

  override fun getColumnCount() = 2

  override fun getValueAt(rowIndex: Int, columnIndex: Int): NlProperty {
    val nameProperty = argumentsProperty.properties[rowIndex]
    return if (columnIndex == 0) nameProperty else nameProperty.defaultValueProperty
  }

  override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
    getValueAt(rowIndex, columnIndex).setValue(value)
    argumentsProperty.refreshList()
  }

  override fun isCellEditable(rowIndex: Int, columnIndex: Int) = true
}
