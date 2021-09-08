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
package com.android.tools.idea.devicemanager.virtualtab

import com.android.sdklib.internal.avd.AvdInfo
import com.android.tools.idea.avdmanager.AccelerationErrorCode
import com.android.tools.idea.avdmanager.AccelerationErrorNotificationPanel
import com.android.tools.idea.avdmanager.AvdActionPanel
import com.android.tools.idea.avdmanager.AvdActionPanel.AvdRefreshProvider
import com.android.tools.idea.avdmanager.AvdDisplayList
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.avdmanager.AvdUiAction.AvdInfoProvider
import com.android.tools.idea.avdmanager.DeleteAvdAction
import com.android.tools.idea.devicemanager.virtualtab.columns.AvdActionsColumnInfo
import com.android.tools.idea.devicemanager.virtualtab.columns.AvdDeviceColumnInfo
import com.android.tools.idea.devicemanager.virtualtab.columns.SizeOnDiskColumn
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.containers.toArray
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import javax.swing.table.TableCellRenderer

/**
 * A UI component which lists the existing AVDs
 */
class VirtualDisplayList @TestOnly constructor(
  private val project: Project?,
  private val virtualDeviceModel: VirtualDeviceModel,
  modelListenerLatch: CountDownLatch?,
  private val deviceTableCellRenderer: TableCellRenderer
) : JPanel(), ListSelectionListener, AvdRefreshProvider, AvdInfoProvider {

  constructor(project: Project?) : this(project,
                                        VirtualDeviceModel(),
                                        null,
                                        VirtualDeviceTableCellRenderer())

  private val notificationPanel = JPanel().apply {
    layout = BoxLayout(this, 1)
  }
  private val tableModel = ListTableModel<AvdInfo>().apply {
    isSortable = true
  }

  val table: VirtualTableView
  private val logger: Logger get() = logger<VirtualDisplayList>()

  private var latestSearchString: String = ""

  init {
    layout = BorderLayout()
    table = VirtualTableView(tableModel, this)
    virtualDeviceModel.addListener(ModelListener(modelListenerLatch))
    add(notificationPanel, BorderLayout.NORTH)
    add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER)

    table.apply {
      selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
      selectionModel.addListSelectionListener(this)

      val adapter = avdActionPanelMouseAdapter()

      addMouseListener(adapter)
      addMouseMotionListener(adapter)

      val map = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)

      map.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteAvd")
      map.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "deleteAvd")
    }

    val map = table.actionMap

    map.put("deleteAvd", DeleteAvdAction(this, false))
    map.put("selectNextColumn", SelectNextColumnAction())
    map.put("selectNextColumnCell", SelectNextColumnCellAction())
    map.put("selectPreviousColumn", SelectPreviousColumnAction())

    tableModel.columnInfos = newColumns().toArray(ColumnInfo.EMPTY_ARRAY)
    table.setRowSorter()
    refreshAvds()
  }

  /**
   * This class implements the table selection interface and passes the selection events on to its listeners.
   */
  override fun valueChanged(e: ListSelectionEvent) {
    // Required so the editor component is updated to know it's selected.
    table.editCellAt(table.selectedRow, table.selectedColumn)
  }

  override fun getAvdInfo(): AvdInfo? = table.selectedObject

  private val avdProviderComponent: JComponent = this
  override fun getAvdProviderComponent(): JComponent {
    return avdProviderComponent
  }

  fun updateSearchResults(searchString: String?) {
    if (searchString != null) {
      latestSearchString = searchString
    }
    tableModel.items = tableModel.items.filter {
      it.displayName.contains(latestSearchString, ignoreCase = true)
    }
  }

  /**
   * Reload AVD definitions from disk and repopulate the table
   */
  override fun refreshAvds() {
    virtualDeviceModel.refreshAvds()
  }

  /**
   * Reload AVD definitions from disk, repopulate the table, and select the indicated AVD
   */
  override fun refreshAvdsAndSelect(avdToSelect: AvdInfo?) {
    refreshAvds()

    avdToSelect ?: return
    val avdInList = table.items.firstOrNull { it.name == avdToSelect.name } ?: return
    table.selection = listOf(avdInList)
  }

  override fun getProject(): Project? {
    return project
  }

  override fun getComponent(): JComponent = this

  @VisibleForTesting
  fun getTableItems(): List<AvdInfo> {
    return tableModel.items
  }

  private fun avdActionPanelMouseAdapter(): MouseAdapter = object : MouseAdapter() {
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

  // needs an initialized table
  fun newColumns(): Collection<ColumnInfo<AvdInfo, *>> {
    return listOf(
      AvdDeviceColumnInfo("Device", deviceTableCellRenderer),
      object : AvdDisplayList.AvdColumnInfo("API") {
        override fun valueOf(avdInfo: AvdInfo): String = avdInfo.androidVersion.apiString
      },
      SizeOnDiskColumn(table),
      AvdActionsColumnInfo("Actions", project != null, this)
    )
  }

  private fun refreshErrorCheck() {
    val refreshUI = AtomicBoolean(notificationPanel.componentCount > 0)
    notificationPanel.removeAll()
    val error = AvdManagerConnection.getDefaultAvdManagerConnection().checkAccelerationAsync()
    Futures.addCallback(
      error,
      object : FutureCallback<AccelerationErrorCode> {
        override fun onSuccess(result: AccelerationErrorCode?) {
          requireNotNull(result)
          if (result != AccelerationErrorCode.ALREADY_INSTALLED) {
            refreshUI.set(true)
            notificationPanel.add(
              AccelerationErrorNotificationPanel(result, project) { refreshErrorCheck() })
          }
          if (refreshUI.get()) {
            notificationPanel.revalidate()
            notificationPanel.repaint()
          }
        }

        override fun onFailure(t: Throwable) {
          logger.warn("Check for emulation acceleration failed", t)
        }
      }, EdtExecutorService.getInstance())
  }

  inner class ModelListener(private val latch: CountDownLatch?) : VirtualDeviceModel.VirtualDeviceModelListener {

    override fun avdListChanged(avds: MutableList<AvdInfo>) {
      tableModel.items = avds
      updateSearchResults(null)
      table.setWidths()

      refreshErrorCheck()
      latch?.countDown()
    }
  }
}
