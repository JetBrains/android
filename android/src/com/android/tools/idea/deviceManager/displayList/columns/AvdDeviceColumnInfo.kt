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

import com.android.sdklib.AndroidVersion
import com.android.sdklib.internal.avd.AvdInfo
import com.android.tools.adtui.stdui.CommonButton
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.deviceManager.displayList.EmulatorDisplayList
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.layout.panel
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
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
class AvdDeviceColumnInfo(
  name: String, private val width: Int = 70
) : ColumnInfo<AvdInfo, AvdInfo>(name) {
  override fun getRenderer(o: AvdInfo): TableCellRenderer? = staticRenderer

  override fun getColumnClass(): Class<*> = AvdInfo::class.java

  override fun getWidth(table: JTable): Int = JBUI.scale(width)

  override fun valueOf(avdInfo: AvdInfo): AvdInfo = avdInfo

  companion object {
    /**
     * Renders an icon in a small square field
     */
    val staticRenderer: TableCellRenderer = object : DefaultTableCellRenderer() {
      override fun getTableCellRendererComponent(
        table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
      ): Component {
        val avdInfo = value as AvdInfo
        val deviceInfo = avdInfo.toDeviceInfo()
        val iconLabel = JBLabel(deviceInfo.iconPair.baseIcon).apply {
          if (value === StudioIcons.Avd.DEVICE_PLAY_STORE) {
            // (No accessible name for the Device Type column)
            AccessibleContextUtil.setName(this, "Play Store")
          }
          if (table.selectedRow == row) {
            background = table.selectionBackground
            foreground = table.selectionForeground
            isOpaque = true
            icon = deviceInfo.iconPair.highlightedIcon
          }
        }
        val infoString = "${deviceInfo.architecture} | ${deviceInfo.target}"

        val onlineStatus = if (deviceInfo.isOnline) StudioIcons.Common.CIRCLE_GREEN else StudioIcons.Common.CIRCLE_RED

        fun DialogPanel.colored() = this.apply {
          if (table.selectedRow == row) {
            background = table.selectionBackground
            foreground = table.selectionForeground
            isOpaque = true
          } else {
            background = table.background
            foreground = table.foreground
          }
        }

        val infoPanel = panel {
          row {
            cell{
              label(deviceInfo.name)
              CommonButton(onlineStatus)()
            }
          }
          row {
            label(infoString, UIUtil.ComponentStyle.SMALL, UIUtil.FontColor.BRIGHTER)
          }
        }

        return panel {
          row {
            iconLabel()
            infoPanel.colored()()
          }
        }.colored()
      }
    }
  }
}

/**
 * Get device info representing the device class of the given AVD (e.g. phone/tablet, Wear, TV)
 */
@VisibleForTesting
fun AvdInfo.toDeviceInfo(): DeviceInfo = DeviceInfo(
  this.displayName,
  EmulatorDisplayList.DeviceType.VIRTUAL,
  AvdManagerConnection.getDefaultAvdManagerConnection().isAvdRunning(this),
  androidVersion,
  EmulatorDisplayList.getDeviceClassIconPair(this),
  cpuArch,
  EmulatorDisplayList.getTargetString(androidVersion, tag)
)

/**
 * Stores device (virtual, real or preconfigured)
 * info for the "Device" column of emulator and physical tabs.
 */
data class DeviceInfo(
  val name: String,
  val type: EmulatorDisplayList.DeviceType,
  var isOnline: Boolean,
  val api: AndroidVersion,
  val iconPair: EmulatorDisplayList.HighlightableIconPair,
  val architecture: String,
  val target: String
)
