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
package com.android.tools.idea.devicemanager.displayList

import com.android.sdklib.devices.Device
import com.android.tools.idea.avdmanager.ApiLevelComparator
import com.android.tools.idea.avdmanager.AvdActionPanel
import com.android.tools.idea.avdmanager.AvdOptionsModel
import com.android.tools.idea.avdmanager.SystemImageDescription
import com.android.tools.idea.devicemanager.displayList.columns.DeviceColumnInfo
import com.android.tools.idea.devicemanager.displayList.columns.PreconfiguredDeviceColumnInfo
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.google.common.collect.Sets
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.table.TableView
import com.intellij.util.containers.toArray
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import javax.swing.table.TableCellRenderer

data class PreconfiguredDeviceDefinition(
  val device: Device,
  /**
   * System image to install.
   *
   * `null` should only be possible if the desired image is not installed locally and the remote image was not fetched yet.
   */
  val systemImage: SystemImageDescription?,
  val estimatedSize: Int // In megabytes
)

// TODO(qumeric): this class has a lot in common with EmulatorDisplayList.
/**
 * A UI component which lists the existing AVDs
 */
class PreconfiguredDisplayList(
  val project: Project?,
  private val avdList: AvdActionPanel.AvdRefreshProvider
) : JPanel(), ListSelectionListener {
  private val centerCardPanel: JPanel
  private val notificationPanel = JPanel().apply {
    layout = BoxLayout(this, 1)
  }

  private val model = PreconfiguredDisplayListModel(project)
  private val table = TableView<PreconfiguredDeviceDefinition>()
  private val listeners: MutableSet<DeviceSelectionListener> = Sets.newHashSet()

  init {
    layout = BorderLayout()
    val nonemptyPanel = JPanel(BorderLayout()).apply {
      add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER)
      add(notificationPanel, BorderLayout.NORTH)
    }

    centerCardPanel = JPanel(CardLayout()).apply {
      add(nonemptyPanel, NONEMPTY)
      add(JPanel(), EMPTY)
    }

    add(centerCardPanel, BorderLayout.CENTER)

    table.setModelAndUpdateColumns(model)
    table.setDefaultRenderer(Any::class.java, NoBorderCellRenderer(table.getDefaultRenderer(Any::class.java)))
    table.apply {
      selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
      selectionModel.addListSelectionListener(this)
      addMouseListener(editingListener)
      addMouseMotionListener(editingListener)
      addMouseListener(LaunchListener())
      getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).apply {
        put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enter")
        put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "enter")
        put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteAvd")
        put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "deleteAvd")
      }
    }
    table.actionMap.apply {
      put("enter", object : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
          installPreconfiguredDevice()
        }
      })
    }
    refreshDevices()

    model.isSortable = true
    model.columnInfos = listOf(
      PreconfiguredDeviceColumnInfo("Device"),
      object : DeviceColumnInfo("API", JBUI.scale(50)) {
        override fun valueOf(device: PreconfiguredDeviceDefinition): String = device.systemImage?.version?.apiString ?: "fetching..."

        /**
         * We override the comparator here to sort the API levels numerically
         * (when possible; with preview platforms codenames are compared alphabetically)
         */
        override fun getComparator(): Comparator<PreconfiguredDeviceDefinition> = with(ApiLevelComparator()) {
          Comparator { o1, o2 -> compare(valueOf(o1), valueOf(o2)) }
        }
      },

      object : DeviceColumnInfo("") {
        // TODO(qumeric): show in GB etc if needed. There are some helper functions for it.
        override fun valueOf(item: PreconfiguredDeviceDefinition): String = item.estimatedSize.toString() + "M"
      }
    ).toArray(ColumnInfo.EMPTY_ARRAY)
  }

  /**
   * Components which wish to receive a notification when the user has selected an AVD from this
   * table must implement this interface and register themselves through [addSelectionListener]
   */
  interface DeviceSelectionListener {
    fun onDeviceSelected(device: PreconfiguredDeviceDefinition?)
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

  // TODO(qumeric): probably we don't need it because it's handled by the model?
  /**
   * Reload device definitions from disk and repopulate the table
   */
  fun refreshDevices() {
    model.refreshImages(true)
    return
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

  private inner class LaunchListener : MouseAdapter() {
    override fun mouseClicked(e: MouseEvent) {
      if (e.clickCount == 2) {
        installPreconfiguredDevice()
      }
    }
  }

  private fun installPreconfiguredDevice() {
    val pfd = table.selectedObject!!

    if (pfd.systemImage == null) {
      // TODO(qumeric): make it a toast
      Messages.showInfoMessage(
        project,
        """A corresponding system image for the ${pfd.device.displayName} configuration was not found.
Check your internet connection and try again.""",
        "System image is not found"
      )
      return
    }

    if (pfd.systemImage.isRemote) {
      downloadImage(pfd.systemImage)
      return
    }

    AvdOptionsModel(null) {
      avdList.refreshAvds()
    }.apply {
      device().setNullableValue(pfd.device)
      systemImage().setNullableValue(pfd.systemImage)
      avdDisplayName().set(pfd.device.displayName)
      handleFinished()
    }
  }

  private fun downloadImage(image: SystemImageDescription) {
    val requestedPackages = listOf(image.remotePackage!!.path)
    val dialog = SdkQuickfixUtils.createDialogForPaths(project, requestedPackages)
    if (dialog != null) {
      dialog.show()
      refreshDevices()
    }
  }

  /**
   * Renders a cell with borders.
   */
  private class NoBorderCellRenderer(var defaultRenderer: TableCellRenderer) : TableCellRenderer {
    override fun getTableCellRendererComponent(
      table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): Component = (defaultRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JComponent).apply {
      border = JBUI.Borders.empty(10)
    }
  }
}

private const val NONEMPTY = "nonempty"
private const val EMPTY = "empty"
