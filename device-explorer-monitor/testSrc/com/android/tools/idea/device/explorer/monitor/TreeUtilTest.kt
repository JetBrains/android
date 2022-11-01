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
package com.android.tools.idea.device.explorer.monitor

import com.android.tools.idea.device.explorer.monitor.mocks.MockDevice
import com.android.tools.idea.device.explorer.monitor.processes.Device
import com.android.tools.idea.device.explorer.monitor.processes.ProcessInfo
import com.google.common.truth.Truth.assertThat
import com.intellij.util.ui.tree.TreeModelAdapter
import org.junit.Before
import org.junit.Test
import javax.swing.event.TreeModelEvent
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.MutableTreeNode

class TreeUtilTest {
  private lateinit var device: Device
  private lateinit var rootNode: DeviceTreeNode
  private lateinit var treeModel: DefaultTreeModel
  private lateinit var treeModelListener: MyTreeModelListener
  private lateinit var newProcesses: MutableList<ProcessInfo>

  @Before
  fun setup() {
    rootNode = createDeviceNode()
    addDefaultProcessNodes(rootNode)
    treeModel = DefaultTreeModel(rootNode)
    treeModelListener = MyTreeModelListener()
    treeModel.addTreeModelListener(treeModelListener)
    newProcesses = createEntriesForChildren(rootNode)
  }

  @Test
  fun testFullTreeAndEmptyList() {
    // Prepare
    newProcesses.clear()

    // Act
    TreeUtil.updateChildrenNodes(treeModel, rootNode, newProcesses, createDefaultOps())

    // Assert
    assertEntriesAreEqual(rootNode, newProcesses)
    assertThat(treeModelListener.structureChangedCount).isEqualTo(1)
    assertThat(treeModelListener.nodesChangedCount).isEqualTo(0)
    assertThat(treeModelListener.nodesInsertedCount).isEqualTo(0)
    assertThat(treeModelListener.nodesRemovedCount).isEqualTo(0)
  }

  @Test
  fun testEmptyTreeAndFullList() {
    // Prepare
    rootNode.removeAllChildren()

    // Act
    TreeUtil.updateChildrenNodes(treeModel, rootNode, newProcesses, createDefaultOps())

    // Assert
    assertEntriesAreEqual(rootNode, newProcesses)
    assertThat(treeModelListener.structureChangedCount).isEqualTo(1)
    assertThat(treeModelListener.nodesChangedCount).isEqualTo(0)
    assertThat(treeModelListener.nodesInsertedCount).isEqualTo(0)
    assertThat(treeModelListener.nodesRemovedCount).isEqualTo(0)
  }

  @Test
  fun testSingleInsertAtStart() {
    // Prepare
    newProcesses.add(createProcessInfo(0))
    newProcesses.sortWith(DeviceMonitorModel.ProcessInfoNameComparator)

    // Act
    TreeUtil.updateChildrenNodes(treeModel, rootNode, newProcesses, createDefaultOps())

    // Assert
    assertEntriesAreEqual(rootNode, newProcesses)
    assertThat(treeModelListener.structureChangedCount).isEqualTo(0)
    assertThat(treeModelListener.nodesChangedCount).isEqualTo(0)
    assertThat(treeModelListener.nodesInsertedCount).isEqualTo(1)
    assertThat(treeModelListener.nodesRemovedCount).isEqualTo(0)
  }

  @Test
  fun testSingleInsertAtEnd() {
    // Prepare
    newProcesses.add(createProcessInfo(30))
    newProcesses.sortWith(DeviceMonitorModel.ProcessInfoNameComparator)

    // Act
    TreeUtil.updateChildrenNodes(treeModel, rootNode, newProcesses, createDefaultOps())

    // Assert
    assertEntriesAreEqual(rootNode, newProcesses)
    assertThat(treeModelListener.structureChangedCount).isEqualTo(0)
    assertThat(treeModelListener.nodesChangedCount).isEqualTo(0)
    assertThat(treeModelListener.nodesInsertedCount).isEqualTo(1)
    assertThat(treeModelListener.nodesRemovedCount).isEqualTo(0)
  }

  @Test
  fun testSingleInsertInTheMiddle() {
    // Prepare
    newProcesses.add(createProcessInfo(7))
    newProcesses.sortWith(DeviceMonitorModel.ProcessInfoNameComparator)

    // Act
    TreeUtil.updateChildrenNodes(treeModel, rootNode, newProcesses, createDefaultOps())

    // Assert
    assertEntriesAreEqual(rootNode, newProcesses)
    assertThat(treeModelListener.structureChangedCount).isEqualTo(0)
    assertThat(treeModelListener.nodesChangedCount).isEqualTo(0)
    assertThat(treeModelListener.nodesInsertedCount).isEqualTo(1)
    assertThat(treeModelListener.nodesRemovedCount).isEqualTo(0)
  }

  @Test
  fun testMultipleInsertsScattered() {
    // Prepare
    newProcesses.add(createProcessInfo(1))
    newProcesses.add(createProcessInfo(7))
    newProcesses.add(createProcessInfo(12))
    newProcesses.sortWith(DeviceMonitorModel.ProcessInfoNameComparator)

    // Act
    TreeUtil.updateChildrenNodes(treeModel, rootNode, newProcesses, createDefaultOps())

    // Assert
    assertEntriesAreEqual(rootNode, newProcesses)
    assertThat(treeModelListener.structureChangedCount).isEqualTo(0)
    assertThat(treeModelListener.nodesChangedCount).isEqualTo(0)
    assertThat(treeModelListener.nodesInsertedCount).isEqualTo(3)
    assertThat(treeModelListener.nodesRemovedCount).isEqualTo(0)
  }

  @Test
  fun testSingleDeleteAtStart() {
    // Prepare
    newProcesses.removeFirst()

    // Act
    TreeUtil.updateChildrenNodes(treeModel, rootNode, newProcesses, createDefaultOps())

    // Assert
    assertEntriesAreEqual(rootNode, newProcesses)
    assertThat(treeModelListener.structureChangedCount).isEqualTo(0)
    assertThat(treeModelListener.nodesChangedCount).isEqualTo(0)
    assertThat(treeModelListener.nodesInsertedCount).isEqualTo(0)
    assertThat(treeModelListener.nodesRemovedCount).isEqualTo(1)
  }

  @Test
  fun testSingleDeleteAtEnd() {
    // Prepare
    newProcesses.removeLast()

    // Act
    TreeUtil.updateChildrenNodes(treeModel, rootNode, newProcesses, createDefaultOps())

    // Assert
    assertEntriesAreEqual(rootNode, newProcesses)
    assertThat(treeModelListener.structureChangedCount).isEqualTo(0)
    assertThat(treeModelListener.nodesChangedCount).isEqualTo(0)
    assertThat(treeModelListener.nodesInsertedCount).isEqualTo(0)
    assertThat(treeModelListener.nodesRemovedCount).isEqualTo(1)
  }

  @Test
  fun testSingleDeleteInTheMiddle() {
    // Prepare
    newProcesses.removeAt(2)

    // Act
    TreeUtil.updateChildrenNodes(treeModel, rootNode, newProcesses, createDefaultOps())

    // Assert
    assertEntriesAreEqual(rootNode, newProcesses)
    assertThat(treeModelListener.structureChangedCount).isEqualTo(0)
    assertThat(treeModelListener.nodesChangedCount).isEqualTo(0)
    assertThat(treeModelListener.nodesInsertedCount).isEqualTo(0)
    assertThat(treeModelListener.nodesRemovedCount).isEqualTo(1)
  }

  @Test
  fun testMultipleDeleteScattered() {
    // Prepare
    newProcesses.removeFirst()
    newProcesses.removeAt(1)
    newProcesses.removeLast()

    // Act
    TreeUtil.updateChildrenNodes(treeModel, rootNode, newProcesses, createDefaultOps())

    // Assert
    assertEntriesAreEqual(rootNode, newProcesses)
    assertThat(treeModelListener.structureChangedCount).isEqualTo(0)
    assertThat(treeModelListener.nodesChangedCount).isEqualTo(0)
    assertThat(treeModelListener.nodesInsertedCount).isEqualTo(0)
    assertThat(treeModelListener.nodesRemovedCount).isEqualTo(3)
  }

  @Test
  fun testMultipleChangedNodes() {
    // Prepare
    var node = newProcesses.removeFirst()
    newProcesses.add(0, createChangedProcessInfo(node.pid, node.pid+1))
    node = newProcesses.removeAt(2)
    newProcesses.add(2, createChangedProcessInfo(node.pid, node.pid+1))
    node = newProcesses.removeLast()
    newProcesses.add(createChangedProcessInfo(node.pid, node.pid+1))

    // Act
    TreeUtil.updateChildrenNodes(treeModel, rootNode, newProcesses, createDefaultOps())

    // Assert
    assertEntriesAreEqual(rootNode, newProcesses)
    assertThat(treeModelListener.structureChangedCount).isEqualTo(0)
    assertThat(treeModelListener.nodesChangedCount).isEqualTo(3)
    assertThat(treeModelListener.nodesInsertedCount).isEqualTo(0)
    assertThat(treeModelListener.nodesRemovedCount).isEqualTo(0)
  }

  private fun createDeviceNode(): DeviceTreeNode {
    device = MockDevice("Test Device", "Test Serial Number")
    return DeviceTreeNode(device)
  }

  private class MyTreeModelListener : TreeModelAdapter() {
    var structureChangedCount = 0
      private set
    var nodesChangedCount = 0
      private set
    var nodesInsertedCount = 0
      private set
    var nodesRemovedCount = 0
      private set

    override fun treeStructureChanged(event: TreeModelEvent) {
      super.treeStructureChanged(event)
      structureChangedCount++
    }

    override fun treeNodesChanged(event: TreeModelEvent) {
      super.treeNodesChanged(event)
      nodesChangedCount++
    }

    override fun treeNodesInserted(event: TreeModelEvent) {
      super.treeNodesInserted(event)
      nodesInsertedCount++
    }

    override fun treeNodesRemoved(event: TreeModelEvent) {
      super.treeNodesRemoved(event)
      nodesRemovedCount++
    }
  }

  private fun createDefaultOps() = object : TreeUtil.UpdateChildrenOps<ProcessInfoTreeNode, ProcessInfo> {
    override fun getChildNode(parentNode: MutableTreeNode, index: Int): ProcessInfoTreeNode? =
      parentNode.getChildAt(index) as? ProcessInfoTreeNode

    override fun updateNode(node: ProcessInfoTreeNode, entry: ProcessInfo) {
      node.processInfo = entry
    }

    override fun equals(node: ProcessInfoTreeNode, entry: ProcessInfo): Boolean =
      node.processInfo == entry

    override fun compareNodesForSorting(node: ProcessInfoTreeNode, entry: ProcessInfo): Int =
      DeviceMonitorModel.ProcessInfoNameComparator.compare(node.processInfo, entry)

    override fun mapEntry(entry: ProcessInfo): ProcessInfoTreeNode =
      ProcessInfoTreeNode(entry)
  }

  private fun addDefaultProcessNodes(deviceNode: DeviceTreeNode) {
    val defaultProcessInfoList = createDefaultProcessInfoList()
    for (processInfo in defaultProcessInfoList) {
      deviceNode.add(ProcessInfoTreeNode(processInfo))
    }
  }

  private fun createDefaultProcessInfoList(): MutableList<ProcessInfo>  {
    val list = mutableListOf(
      createProcessInfo(3),
      createProcessInfo(5),
      createProcessInfo(10),
      createProcessInfo(15),
      createProcessInfo(20)
    )
    list.sortWith(DeviceMonitorModel.ProcessInfoNameComparator)
    return list
  }


  private fun createProcessInfo(pid: Int) =
    ProcessInfo(device, pid, "Test Process $pid")

  private fun createChangedProcessInfo(oldPid: Int, newPid: Int) =
    ProcessInfo(device, newPid, "Test Process $oldPid")

  private fun assertEntriesAreEqual(rootNode: ProcessTreeNode, entries: List<ProcessInfo>) {
    assertThat(rootNode.childCount).isEqualTo(entries.size)
    for (i in 0 until rootNode.childCount) {
      assertThat(rootNode.getChildAt(i)).isInstanceOf(ProcessInfoTreeNode::class.java)
      assertThat((rootNode.getChildAt(i) as ProcessInfoTreeNode).processInfo).isEqualTo(entries[i])
    }
  }

  private fun createEntriesForChildren(node: DeviceTreeNode): MutableList<ProcessInfo> {
    val list = mutableListOf<ProcessInfo>()
    for (child in node.children()) {
      if (child is ProcessInfoTreeNode) {
        list.add(child.processInfo)
      }
    }

    return list
  }
}