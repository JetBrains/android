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
package com.android.tools.idea.deviceManager.displayList

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.tools.idea.adb.wireless.PairDevicesUsingWiFiService
import com.android.tools.idea.avdmanager.AvdActionPanel
import com.android.tools.idea.deviceManager.actions.PhysicalDeviceUiAction
import com.android.tools.idea.deviceManager.displayList.columns.PhysicalDeviceActionsColumnInfo
import com.android.tools.idea.deviceManager.displayList.columns.PhysicalDeviceColumnInfo
import com.intellij.openapi.project.Project
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.layout.panel
import com.intellij.ui.table.TableView
import com.intellij.util.containers.toArray
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

// Based on AsyncDevicesGetter

typealias SerialNumber = String

data class NamedDevice(
  var name: String,
  val device: IDevice
)

/**
 * A UI component which lists the existing AVDs
 */

class PhysicalDisplayList(override val project: Project?) : JPanel(), ListSelectionListener, PhysicalDeviceUiAction.PhysicalDeviceProvider {
  private val centerCardPanel: JPanel
  private val notificationPanel = JPanel().apply {
    layout = BoxLayout(this, 1)
  }
  private val model = ListTableModel<NamedDevice>().apply {
    isSortable = true
  }
  private val table = TableView<NamedDevice>().apply {
    setModelAndUpdateColumns(this@PhysicalDisplayList.model)
    setDefaultRenderer(Any::class.java, EmulatorDisplayList.NoBorderCellRenderer(this.getDefaultRenderer(Any::class.java)))
  }
  private val listeners: MutableSet<DeviceSelectionListener> = mutableSetOf()

  private val deviceMap: MutableMap<SerialNumber, NamedDevice> = mutableMapOf()

  /*private val deviceNames: MutableMap<SerialNumber, String> = mutableMapOf(
    "xiaomi-mi_8-e1551242" to "CUSTOM DEVICE NAME"
  )*/

  private val deviceChangeListener = object : AndroidDebugBridge.IDeviceChangeListener {
    override fun deviceChanged(device: IDevice, changeMask: Int) {
      refreshDevices()
    }

    override fun deviceConnected(device: IDevice) {
      if (!deviceMap.keys.contains(device.serialNumber)) {
        deviceMap[device.serialNumber] = NamedDevice(device.serialNumber, device)
      }
      else {
        deviceMap[device.serialNumber] = NamedDevice(deviceMap[device.serialNumber]!!.name, device)
      }
      refreshDevices()
    }

    override fun deviceDisconnected(device: IDevice) {
      deviceMap.remove(device.serialNumber)
      refreshDevices()
    }
  }

  init {
    AndroidDebugBridge.addDeviceChangeListener(deviceChangeListener)

    layout = BorderLayout()
    val nonemptyPanel = JPanel(BorderLayout()).apply {
      add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER)
      add(notificationPanel, BorderLayout.NORTH)
    }

    /**
     * If no AVDs are present on the system, the AvdListDialog will display this panel.
     * It contains instructional messages about AVDs and a link to create a new AVD.
     */
    val emptyAvdListPanel = panel {
      row {
        label("No physical devices added. Connect a device via USB cable or pair an Android 11+ device over Wi-Fi.")
      }
      row {
        link("Pair over Wi-Fi") {
          openWifiPairingDialog(project!!)
        }
      }
    }

    centerCardPanel = JPanel(CardLayout()).apply {
      add(nonemptyPanel, NONEMPTY)
      add(emptyAvdListPanel, EMPTY)
    }

    add(centerCardPanel, BorderLayout.CENTER)

    table.apply {
      selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
      selectionModel.addListSelectionListener(this)
      addMouseListener(editingListener)
      addMouseMotionListener(editingListener)
    }

    refreshDevices()

    model.columnInfos = newColumns().toArray(ColumnInfo.EMPTY_ARRAY)
  }

  /**
   * Components which wish to receive a notification when the user has selected an AVD from this
   * table must implement this interface and register themselves through [addSelectionListener]
   */
  interface DeviceSelectionListener {
    fun onDeviceSelected(device: NamedDevice?)
  }

  fun addSelectionListener(listener: DeviceSelectionListener) {
    listeners.add(listener)
  }

  fun removeSelectionListener(listener: DeviceSelectionListener) {
    listeners.remove(listener)
  }

  /**
   * This class implements the table selection interface and passes the selection events on to its listeners.
   */
  override fun valueChanged(e: ListSelectionEvent) {
    // Required so the editor component is updated to know it's selected.
    table.editCellAt(table.selectedRow, table.selectedColumn)
    for (listener in listeners) {
      listener.onDeviceSelected(table.selectedObject)
    }
  }

  /**
   * Reload AVD definitions from disk and repopulate the table
   */
  fun refreshDevices() {
    model.items = deviceMap.values.toList()
    // TODO Sometimes the status is not reset to EMPTY when a user unplugs devices
    val status = if (model.items.isEmpty()) EMPTY else NONEMPTY
    //updateSearchResults(null)
    (centerCardPanel.layout as CardLayout).show(centerCardPanel, status)
    //refreshErrorCheck()
  }

  private val editingListener: MouseAdapter = object : MouseAdapter() {
    override fun mouseMoved(e: MouseEvent) {
      possiblySwitchEditors(e)
    }

    override fun mouseEntered(e: MouseEvent) {
      possiblySwitchEditors(e)
    }

    override fun mouseExited(e: MouseEvent) {
      possiblySwitchEditors(e)
    }

    override fun mouseClicked(e: MouseEvent) {
      possiblySwitchEditors(e)
    }

    override fun mousePressed(e: MouseEvent) {
      possiblyShowPopup(e)
    }

    override fun mouseReleased(e: MouseEvent) {
      possiblyShowPopup(e)
    }
  }

  private fun possiblySwitchEditors(e: MouseEvent) {
    val p = e.point
    val row = table.rowAtPoint(p)
    val col = table.columnAtPoint(p)
    if (row != table.editingRow || col != table.editingColumn) {
      if ((row != -1) && (col != -1) && table.isCellEditable(row, col)) {
        table.editCellAt(row, col)
      }
    }
  }

  private fun possiblyShowPopup(e: MouseEvent) {
    if (!e.isPopupTrigger) {
      return
    }
    val p = e.point
    val row = table.rowAtPoint(p)
    val col = table.columnAtPoint(p)
    if (row != -1 && col != -1) {
      val lastColumn = table.columnCount - 1
      val maybeActionPanel = table.getCellRenderer(row, lastColumn).getTableCellRendererComponent(
        table, table.getValueAt(row, lastColumn), false, true, row, lastColumn
      )
      if (maybeActionPanel is AvdActionPanel) {
        maybeActionPanel.showPopup(table, e)
      }
    }
  }

  override val device: NamedDevice? get() = table.selectedObject

  // needs an initialized table
  fun newColumns(): Collection<ColumnInfo<NamedDevice, *>> {
    return listOf(
      object : PhysicalDeviceColumnInfo("Device") {
        override fun valueOf(item: NamedDevice): String = item.name
      },
      object : PhysicalDeviceColumnInfo("API") {
        override fun valueOf(item: NamedDevice): String = item.device.version.apiString
      },
      PhysicalDeviceActionsColumnInfo("Actions", deviceProvider = this)
    )
  }

  companion object {
    const val NONEMPTY = "nonempty"
    const val EMPTY = "empty"
  }
}

fun openWifiPairingDialog(project: Project) {
  val controller = PairDevicesUsingWiFiService.getInstance(project).createPairingDialogController()
  controller.showDialog()
}
