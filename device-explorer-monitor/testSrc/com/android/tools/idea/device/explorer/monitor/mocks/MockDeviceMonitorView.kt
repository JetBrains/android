/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.monitor.mocks

import com.android.tools.idea.device.explorer.monitor.DeviceMonitorModel
import com.android.tools.idea.device.explorer.monitor.ui.DeviceMonitorView
import com.android.tools.idea.device.explorer.monitor.DeviceMonitorViewListener
import com.android.tools.idea.device.explorer.monitor.ui.DeviceMonitorViewImpl
import com.android.tools.idea.device.explorer.monitor.ui.ProcessListTableBuilder
import com.android.tools.idea.device.explorer.monitor.ui.menu.item.DebugMenuItem
import com.android.tools.idea.device.explorer.monitor.ui.menu.item.ForceStopMenuItem
import com.android.tools.idea.device.explorer.monitor.ui.menu.item.MenuContext
import javax.swing.JComponent

class MockDeviceMonitorView(model: DeviceMonitorModel): DeviceMonitorView {
  private val table = ProcessListTableBuilder().build(model.tableModel)
  private val viewImpl = DeviceMonitorViewImpl(model, table)

  override fun addListener(listener: DeviceMonitorViewListener) {
    viewImpl.addListener(listener)
  }

  override fun removeListener(listener: DeviceMonitorViewListener) {
    viewImpl.removeListener(listener)
  }

  override fun setup() {
    viewImpl.setup()
  }

  override val panelComponent: JComponent
    get() = viewImpl.panelComponent

  fun killNodes() {
    if (table.model.rowCount > 0) {
      table.setRowSelectionInterval(0, 0)
    }
    val menuItem = ForceStopMenuItem(viewImpl, MenuContext.Popup)
    menuItem.run()
  }

  fun debugNodes() {
    if (table.model.rowCount > 0) {
      table.setRowSelectionInterval(0, 0)
    }
    val menuItem = DebugMenuItem(viewImpl, MenuContext.Popup)
    menuItem.run()
  }
}