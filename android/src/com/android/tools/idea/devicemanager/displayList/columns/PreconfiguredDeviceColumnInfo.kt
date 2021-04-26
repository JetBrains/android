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
package com.android.tools.idea.devicemanager.displayList.columns

import com.android.sdklib.internal.avd.AvdInfo
import com.android.tools.idea.devicemanager.displayList.PreconfiguredDeviceDefinition
import com.intellij.ui.components.JBLabel
import com.intellij.ui.layout.panel
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.Component
import javax.swing.Icon
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

/**
 * This class extends [ColumnInfo] in order to pull an [Icon] value from a given [AvdInfo].
 * This is the column info used for the Type and Status columns.
 */
class PreconfiguredDeviceColumnInfo(
  name: String, private val width: Int = 70
) : ColumnInfo<PreconfiguredDeviceDefinition, PreconfiguredDeviceDefinition>(name) {
  override fun getRenderer(o: PreconfiguredDeviceDefinition): TableCellRenderer? = staticRenderer

  override fun getWidth(table: JTable): Int = JBUI.scale(width)

  override fun valueOf(item: PreconfiguredDeviceDefinition): PreconfiguredDeviceDefinition = item

  companion object {
    /**
     * Renders an icon in a small square field
     */
    private val staticRenderer: TableCellRenderer = object : DefaultTableCellRenderer() {
      override fun getTableCellRendererComponent(
        table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
      ): Component {
        val deviceDefinition = value as PreconfiguredDeviceDefinition
        // TODO(qumeric): use the proper icon
        val downloadIconLabel = JBLabel(StudioIcons.LayoutEditor.Extras.PALETTE_DOWNLOAD).apply {
          if (table.selectedRow == row) {
            background = table.selectionBackground
            foreground = table.selectionForeground
            isOpaque = true
          }
          if (deviceDefinition.systemImage == null) {
            icon = StudioIcons.LayoutEditor.Extras.PALETTE_DOWNLOAD_SELECTED
          }
          else if (!deviceDefinition.systemImage.isRemote) {
            // TODO(qumeric): maybe we should display something else here?
            icon = StudioIcons.LayoutEditor.Extras.PALETTE_DOWNLOAD_SELECTED
          }
        }
        return panel {
          row {
            downloadIconLabel()
            label(deviceDefinition.device.displayName)
          }
        }.apply {
          if (table.selectedRow == row) {
            background = table.selectionBackground
            foreground = table.selectionForeground
          } else {
            background = table.background
            foreground = table.foreground
          }
          isOpaque = true
        }
      }
    }
  }
}
