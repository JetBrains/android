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

import com.android.SdkConstants.ATTR_ARG_TYPE
import com.android.SdkConstants.ATTR_NAME
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.naveditor.property.NavArgumentDefaultValuesProperty
import javax.swing.table.AbstractTableModel

open class NavArgumentDefaultValuesTableModel(private val argumentsProperty: NavArgumentDefaultValuesProperty) : AbstractTableModel() {
  override fun getRowCount() = argumentsProperty.properties.size

  override fun getColumnCount() = 3

  override fun getValueAt(rowIndex: Int, columnIndex: Int): NlProperty {
    val defaultValueProperty = argumentsProperty.properties[rowIndex]
    return when (columnIndex) {
      0 -> SimpleProperty(ATTR_NAME, listOf(), _value = defaultValueProperty.argName)
      1 -> SimpleProperty(ATTR_ARG_TYPE, listOf(), _value = defaultValueProperty.type ?: "<inferred>")
      else -> defaultValueProperty
    }
  }

  override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
    getValueAt(rowIndex, columnIndex).setValue(value)
    argumentsProperty.refreshList()
  }

  override fun isCellEditable(rowIndex: Int, columnIndex: Int) = columnIndex == 2
}
