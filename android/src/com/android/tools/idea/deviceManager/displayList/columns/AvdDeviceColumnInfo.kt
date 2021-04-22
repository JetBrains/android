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
package com.android.tools.idea.deviceManager.displayList.columns

import com.android.sdklib.internal.avd.AvdInfo
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import javax.swing.Icon
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

/**
 * This class extends [ColumnInfo] in order to pull an [Icon] value from a given [AvdInfo].
 * This is the column info used for the Type and Status columns.
 */
class AvdDeviceColumnInfo(
  name: String, private val width: Int = 70
) : ColumnInfo<AvdInfo, AvdInfo>(name) {
  private val renderer = VirtualDeviceTableCellRenderer()

  override fun getRenderer(device: AvdInfo): TableCellRenderer = renderer

  override fun getColumnClass(): Class<*> = AvdInfo::class.java

  override fun getWidth(table: JTable): Int = JBUI.scale(width)

  override fun valueOf(avdInfo: AvdInfo): AvdInfo = avdInfo
}
