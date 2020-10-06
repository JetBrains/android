/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.getName
import com.google.common.annotations.VisibleForTesting
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import javax.swing.JComponent

/**
 * A table view to display properties of Android device.
 */
class AndroidDeviceInfoTableView {
  private val myModel = AndroidDeviceInfoTableViewModel()
  @VisibleForTesting val myTableView = TableView(myModel).apply {
    isFocusable = false
    rowSelectionAllowed = false
    tableHeader.reorderingAllowed = false
  }
  private val myTableViewContainer = JBScrollPane(myTableView)

  /**
   * Returns a root component of the table view.
   */
  @UiThread
  fun getComponent(): JComponent {
    return myTableViewContainer
  }

  @UiThread
  fun setAndroidDevice(device: AndroidDevice) {
    myModel.setAndroidDevice(device)
  }
}

/**
 * An item of the Android device info table.
 */
@VisibleForTesting
data class AndroidDeviceInfoItem(val propertyName: String,
                                 val propertyValue: String)

/**
 * A view model class of [AndroidDeviceInfoTableView].
 */
private class AndroidDeviceInfoTableViewModel :
  ListTableModel<AndroidDeviceInfoItem>(DevicePropertyNameColumn, DevicePropertyValueColumn) {
  @UiThread
  fun setAndroidDevice(device: AndroidDevice) {
    val itemsBuilder = mutableListOf(
      AndroidDeviceInfoItem("Device Name", device.getName()),
      AndroidDeviceInfoItem("OS Version", device.version.apiString))
    itemsBuilder.addAll(device.additionalInfo.asSequence()
      .map { (key, value) -> AndroidDeviceInfoItem(key, value) })
    items = itemsBuilder
  }
}

/**
 * A column for displaying a device property name.
 */
private object DevicePropertyNameColumn : ColumnInfo<AndroidDeviceInfoItem, String>("Property") {
  override fun valueOf(item: AndroidDeviceInfoItem): String = item.propertyName
}

/**
 * A column for displaying a device property value.
 */
private object DevicePropertyValueColumn : ColumnInfo<AndroidDeviceInfoItem, String>("Description") {
  override fun valueOf(item: AndroidDeviceInfoItem): String = item.propertyValue
}
