/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.monitor

import com.android.tools.idea.device.explorer.monitor.mocks.MockDevice
import com.android.tools.idea.device.explorer.monitor.processes.ProcessInfo
import com.android.tools.idea.device.explorer.monitor.ui.DeviceMonitorTableModel
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import javax.swing.event.TableModelEvent
import javax.swing.event.TableModelListener

class DeviceMonitorTableModelTest {
  private lateinit var tableModel: DeviceMonitorTableModel
  private lateinit var tableModelListener: TestListener
  private val device = MockDevice("Test Device", "Serial Number")

  @Before
  fun setUp() {
    tableModelListener = TestListener()
    tableModel = DeviceMonitorTableModel()
    tableModel.addTableModelListener(tableModelListener)
  }

  @Test
  fun emptyModelFullList() {
    // Prepare
    val newRows = createDefaultProcessInfoList()

    // Act
    tableModel.updateProcessRows(newRows)

    // Assert
    newRows.sortWith(DeviceMonitorTableModel.ProcessInfoNameComparator)
    assertModelAndListAreEqual(newRows)
    assertThat(tableModelListener.insertRowCount).isEqualTo(newRows.size)
    assertThat(tableModelListener.deleteRowCount).isEqualTo(0)
    assertThat(tableModelListener.updateRowCount).isEqualTo(0)
  }

  @Test
  fun fullModelEmptyList() {
    // Prepare
    val defaultNumOfRows = setupModelWithDefaultList()

    // Act
    tableModel.updateProcessRows(listOf())

    // Assert
    assertModelAndListAreEqual(listOf())
    assertThat(tableModelListener.insertRowCount).isEqualTo(0)
    assertThat(tableModelListener.deleteRowCount).isEqualTo(defaultNumOfRows)
    assertThat(tableModelListener.updateRowCount).isEqualTo(0)
  }

  @Test
  fun testSingleInsertAtStart() {
    // Prepare
    setupModelWithDefaultList()
    val newRows = createDefaultProcessInfoList().apply {
      add(createProcessInfo(0))
    }

    // Act
    tableModel.updateProcessRows(newRows)

    // Assert
    newRows.sortWith(DeviceMonitorTableModel.ProcessInfoNameComparator)
    assertModelAndListAreEqual(newRows)
    assertThat(tableModelListener.insertRowCount).isEqualTo(1)
    assertThat(tableModelListener.deleteRowCount).isEqualTo(0)
    assertThat(tableModelListener.updateRowCount).isEqualTo(0)
  }

  @Test
  fun testSingleInsertAtEnd() {
    // Prepare
    setupModelWithDefaultList()
    val newRows = createDefaultProcessInfoList().apply {
      add(createProcessInfo(7))
    }

    // Act
    tableModel.updateProcessRows(newRows)

    // Assert
    newRows.sortWith(DeviceMonitorTableModel.ProcessInfoNameComparator)
    assertModelAndListAreEqual(newRows)
    assertThat(tableModelListener.insertRowCount).isEqualTo(1)
    assertThat(tableModelListener.deleteRowCount).isEqualTo(0)
    assertThat(tableModelListener.updateRowCount).isEqualTo(0)
  }

  @Test
  fun testSingleInsertAtTheMiddle() {
    // Prepare
    setupModelWithDefaultList()
    val newRows = createDefaultProcessInfoList().apply {
      add(createProcessInfo(17))
    }

    // Act
    tableModel.updateProcessRows(newRows)

    // Assert
    newRows.sortWith(DeviceMonitorTableModel.ProcessInfoNameComparator)
    assertModelAndListAreEqual(newRows)
    assertThat(tableModelListener.insertRowCount).isEqualTo(1)
    assertThat(tableModelListener.deleteRowCount).isEqualTo(0)
    assertThat(tableModelListener.updateRowCount).isEqualTo(0)
  }

  @Test
  fun testMultipleInsertsScattered() {
    // Prepare
    setupModelWithDefaultList()
    val newRows = createDefaultProcessInfoList().apply {
      add(createProcessInfo(1))
      add(createProcessInfo(7))
      add(createProcessInfo(18))
    }

    // Act
    tableModel.updateProcessRows(newRows)

    // Assert
    newRows.sortWith(DeviceMonitorTableModel.ProcessInfoNameComparator)
    assertModelAndListAreEqual(newRows)
    assertThat(tableModelListener.insertRowCount).isEqualTo(3)
    assertThat(tableModelListener.deleteRowCount).isEqualTo(0)
    assertThat(tableModelListener.updateRowCount).isEqualTo(0)
  }

  @Test
  fun testSingleDeleteAtStart() {
    // Prepare
    setupModelWithDefaultList()
    val newRows = createDefaultProcessInfoList().apply {
      sortWith(DeviceMonitorTableModel.ProcessInfoNameComparator)
      removeFirst()
    }

    // Act
    tableModel.updateProcessRows(newRows)

    // Assert
    assertModelAndListAreEqual(newRows)
    assertThat(tableModelListener.insertRowCount).isEqualTo(0)
    assertThat(tableModelListener.deleteRowCount).isEqualTo(1)
    assertThat(tableModelListener.updateRowCount).isEqualTo(0)
  }

  @Test
  fun testSingleDeleteAtEnd() {
    // Prepare
    setupModelWithDefaultList()
    val newRows = createDefaultProcessInfoList().apply {
      sortWith(DeviceMonitorTableModel.ProcessInfoNameComparator)
      removeLast()
    }

    // Act
    tableModel.updateProcessRows(newRows)

    // Assert
    assertModelAndListAreEqual(newRows)
    assertThat(tableModelListener.insertRowCount).isEqualTo(0)
    assertThat(tableModelListener.deleteRowCount).isEqualTo(1)
    assertThat(tableModelListener.updateRowCount).isEqualTo(0)
  }

  @Test
  fun testSingleDeleteAtTheMiddle() {
    // Prepare
    setupModelWithDefaultList()
    val newRows = createDefaultProcessInfoList().apply {
      sortWith(DeviceMonitorTableModel.ProcessInfoNameComparator)
      removeAt(2)
    }

    // Act
    tableModel.updateProcessRows(newRows)

    // Assert
    assertModelAndListAreEqual(newRows)
    assertThat(tableModelListener.insertRowCount).isEqualTo(0)
    assertThat(tableModelListener.deleteRowCount).isEqualTo(1)
    assertThat(tableModelListener.updateRowCount).isEqualTo(0)
  }

  @Test
  fun testMultipleDeleteScattered() {
    // Prepare
    setupModelWithDefaultList()
    val newRows = createDefaultProcessInfoList().apply {
      sortWith(DeviceMonitorTableModel.ProcessInfoNameComparator)
      removeFirst()
      removeAt(1)
      removeLast()
    }

    // Act
    tableModel.updateProcessRows(newRows)

    // Assert
    assertModelAndListAreEqual(newRows)
    assertThat(tableModelListener.insertRowCount).isEqualTo(0)
    assertThat(tableModelListener.deleteRowCount).isEqualTo(3)
    assertThat(tableModelListener.updateRowCount).isEqualTo(0)
  }

  @Test
  fun testMultipleChanges() {
    // Prepare
    setupModelWithDefaultList()
    val newRows = createDefaultProcessInfoList().apply {
      removeFirst()
      removeAt(1)
      add(createProcessInfo(0))
      add(createProcessInfo(17))
      sortWith(DeviceMonitorTableModel.ProcessInfoNameComparator)
    }
    val infoToUpdate = newRows[newRows.size - 1]
    newRows[newRows.size - 1] = createChangedProcessInfo(infoToUpdate.pid, infoToUpdate.pid+1)

    // Act
    tableModel.updateProcessRows(newRows)

    // Assert
    assertModelAndListAreEqual(newRows)
    assertThat(tableModelListener.insertRowCount).isEqualTo(2)
    assertThat(tableModelListener.deleteRowCount).isEqualTo(2)
    assertThat(tableModelListener.updateRowCount).isEqualTo(1)
  }

  private fun assertModelAndListAreEqual(rowList: List<ProcessInfo>) {
    assertThat(tableModel.rowCount).isEqualTo(rowList.size)

    for (index in rowList.indices) {
      assertThat(tableModel.getValueForRow(index)).isEqualTo(rowList[index])
    }
  }

  private fun setupModelWithDefaultList(): Int {
    val previousRows = createDefaultProcessInfoList()
    tableModel.updateProcessRows(previousRows)
    tableModelListener.insertRowCount = 0
    return previousRows.size
  }

  private fun createDefaultProcessInfoList() = mutableListOf(
    createProcessInfo(3),
    createProcessInfo(5),
    createProcessInfo(10),
    createProcessInfo(15),
    createProcessInfo(20)
  )

  private fun createProcessInfo(pid: Int) =
    ProcessInfo(device, pid, "Test Process $pid")

  private fun createChangedProcessInfo(oldPid: Int, newPid: Int) =
    ProcessInfo(device, newPid, "Test Process $oldPid")

  class TestListener : TableModelListener {
    var insertRowCount = 0
    var deleteRowCount = 0
    var updateRowCount = 0

    override fun tableChanged(e: TableModelEvent?) {
      e?.let {
        when (it.type) {
          TableModelEvent.INSERT -> insertRowCount += (it.lastRow - it.firstRow + 1)
          TableModelEvent.DELETE -> deleteRowCount += (it.lastRow - it.firstRow + 1)
          TableModelEvent.UPDATE -> updateRowCount += (it.lastRow - it.firstRow + 1)
          else -> {}
        }
      }
    }
  }
}