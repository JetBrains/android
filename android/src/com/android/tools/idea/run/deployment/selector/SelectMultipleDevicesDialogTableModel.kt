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
package com.android.tools.idea.run.deployment.selector

import com.google.common.collect.HashMultiset
import javax.swing.Icon
import javax.swing.table.AbstractTableModel

internal class SelectMultipleDevicesDialogTableModel(devices: List<DeploymentTargetDevice>) :
  AbstractTableModel() {
  private val rows =
    devices.flatMap { device -> device.targets.map(::SelectMultipleDevicesDialogTableModelRow) }
  private val deviceNames = devices.mapTo(HashMultiset.create()) { it.name }

  var selectedTargets: List<DeploymentTarget>
    get() = rows.filter { it.isSelected }.map { it.target }
    set(selectedTargets) {
      for (rowIndex in 0 until rows.size) {
        setValueAt(
          selectedTargets.contains(rows[rowIndex].target),
          rowIndex,
          SELECTED_MODEL_COLUMN_INDEX
        )
      }
    }

  override fun getRowCount() = rows.size

  override fun getColumnCount() = 5

  override fun getColumnName(modelColumnIndex: Int): String {
    return when (modelColumnIndex) {
      SELECTED_MODEL_COLUMN_INDEX -> ""
      TYPE_MODEL_COLUMN_INDEX -> "Type"
      DEVICE_MODEL_COLUMN_INDEX -> "Device"
      SERIAL_NUMBER_MODEL_COLUMN_INDEX -> "Serial Number"
      BOOT_OPTION_MODEL_COLUMN_INDEX -> "Boot Option"
      else -> throw AssertionError(modelColumnIndex)
    }
  }

  override fun getColumnClass(modelColumnIndex: Int): Class<*> {
    return when (modelColumnIndex) {
      SELECTED_MODEL_COLUMN_INDEX -> java.lang.Boolean::class.java
      TYPE_MODEL_COLUMN_INDEX -> Icon::class.java
      DEVICE_MODEL_COLUMN_INDEX,
      SERIAL_NUMBER_MODEL_COLUMN_INDEX,
      BOOT_OPTION_MODEL_COLUMN_INDEX -> Any::class.java
      else -> throw AssertionError(modelColumnIndex)
    }
  }

  override fun isCellEditable(modelRowIndex: Int, modelColumnIndex: Int): Boolean {
    return when (modelColumnIndex) {
      SELECTED_MODEL_COLUMN_INDEX -> true
      TYPE_MODEL_COLUMN_INDEX,
      DEVICE_MODEL_COLUMN_INDEX,
      SERIAL_NUMBER_MODEL_COLUMN_INDEX,
      BOOT_OPTION_MODEL_COLUMN_INDEX -> false
      else -> throw AssertionError(modelColumnIndex)
    }
  }

  override fun getValueAt(modelRowIndex: Int, modelColumnIndex: Int): Any {
    return when (modelColumnIndex) {
      SELECTED_MODEL_COLUMN_INDEX -> rows[modelRowIndex].isSelected
      TYPE_MODEL_COLUMN_INDEX -> rows[modelRowIndex].target.device.icon
      DEVICE_MODEL_COLUMN_INDEX -> rows[modelRowIndex].deviceCellText
      SERIAL_NUMBER_MODEL_COLUMN_INDEX -> getSerialNumber(rows[modelRowIndex].target.device)
      BOOT_OPTION_MODEL_COLUMN_INDEX -> rows[modelRowIndex].bootOption
      else -> throw AssertionError(modelColumnIndex)
    }
  }

  private fun getSerialNumber(device: DeploymentTargetDevice): Any {
    return if (deviceNames.count(device.name) != 1) {
      device.disambiguator ?: ""
    } else ""
  }

  override fun setValueAt(value: Any, modelRowIndex: Int, modelColumnIndex: Int) {
    rows[modelRowIndex].isSelected = (value as Boolean)
    fireTableCellUpdated(modelRowIndex, modelColumnIndex)
  }

  companion object {
    const val SELECTED_MODEL_COLUMN_INDEX = 0
    const val TYPE_MODEL_COLUMN_INDEX = 1
    private const val DEVICE_MODEL_COLUMN_INDEX = 2
    private const val SERIAL_NUMBER_MODEL_COLUMN_INDEX = 3
    private const val BOOT_OPTION_MODEL_COLUMN_INDEX = 4
  }
}
