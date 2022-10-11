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

import com.android.annotations.concurrency.UiThread
import com.android.ddmlib.IDevice
import com.android.tools.idea.device.explorer.monitor.adbimpl.AdbDevice
import com.android.tools.idea.device.explorer.monitor.processes.DeviceProcessService
import com.android.tools.idea.device.explorer.monitor.processes.ProcessInfo
import com.android.tools.idea.device.explorer.monitor.processes.isPidOnly
import com.android.tools.idea.device.explorer.monitor.processes.safeProcessName
import com.intellij.openapi.diagnostic.thisLogger
import java.util.ArrayList
import java.util.function.Consumer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.DefaultTreeSelectionModel
import javax.swing.tree.MutableTreeNode

@UiThread
class DeviceMonitorModel(private val processService: DeviceProcessService) {
  private var activeDevice: AdbDevice? = null
  private var treeModel: DefaultTreeModel? = null
  private var treeModelSelection: DefaultTreeSelectionModel? = null
  private val listeners: MutableList<DeviceMonitorModelListener> = ArrayList()

  fun addListener(listener: DeviceMonitorModelListener) {
    listeners.add(listener)
  }

  fun removeListener(listener: DeviceMonitorModelListener) {
    listeners.remove(listener)
  }

  suspend fun activeDeviceChanged(device: IDevice?) {
    if (device != null) {
      if (activeDevice?.device != device) {
        activeDevice = AdbDevice(device).apply {
          val deviceNode = DeviceTreeNode(this)
          treeModel = DefaultTreeModel(deviceNode)
          treeModelSelection = DefaultTreeSelectionModel()

          refreshProcessList(treeModel, treeModelSelection)
        }
      }
    } else {
      listeners.forEach(Consumer { x: DeviceMonitorModelListener -> x.treeModelChanged(null, null) })
    }
  }

  suspend fun refreshProcessListForDevice(device: IDevice) {
    if (activeDevice?.device == device) {
      refreshCurrentProcessList()
    }
  }

  suspend fun refreshCurrentProcessList() {
    refreshProcessList(treeModel, treeModelSelection)
  }

  suspend fun killNodesInvoked(nodes: List<ProcessTreeNode>) {
    invokeOnProcessInfo(nodes) { processInfo ->
      activeDevice?.let {
        processService.killProcess(processInfo, it.device)
      }
    }
  }

  suspend fun forceStopNodesInvoked(nodes: List<ProcessTreeNode>) {
    invokeOnProcessInfo(nodes) { processInfo ->
      activeDevice?.let {
        processService.forceStopProcess(processInfo, it.device)
      }
    }
  }

  private suspend fun invokeOnProcessInfo(nodes: List<ProcessTreeNode>, block: suspend (ProcessInfo) -> Unit) {
    nodes.forEach { node ->
      val processInfoNode = ProcessInfoTreeNode.fromNode(node) ?: return@forEach
      block(processInfoNode.processInfo)
    }
  }

  private suspend fun refreshProcessList(treeModel: DefaultTreeModel?, treeModelSelection: DefaultTreeSelectionModel?) {
    val deviceNode = treeModel?.root as? DeviceTreeNode
    val device = deviceNode?.device as? AdbDevice
    if (deviceNode != null && device != null) {
      val processList = processService.fetchProcessList(device)
      updateChildrenNodes(treeModel, deviceNode, processList)
      listeners.forEach(Consumer { x: DeviceMonitorModelListener -> x.treeModelChanged(treeModel, treeModelSelection) })
    }
  }

  private fun updateChildrenNodes(
    treeModel: DefaultTreeModel,
    parentNode: DeviceTreeNode,
    newEntries: List<ProcessInfo>
  ) {
    val updateChildrenOps: TreeUtil.UpdateChildrenOps<ProcessInfoTreeNode, ProcessInfo> =
      object : TreeUtil.UpdateChildrenOps<ProcessInfoTreeNode, ProcessInfo> {
        override fun getChildNode(parentNode: MutableTreeNode, index: Int): ProcessInfoTreeNode? {
          // Some nodes (e.g. "error" or "loading" nodes) are not of the same type,
          // we return null in those cases to that the update algorithm will remove them from
          // the parent node.
          return ProcessInfoTreeNode.fromNode(parentNode.getChildAt(index))
        }

        override fun mapEntry(entry: ProcessInfo): ProcessInfoTreeNode {
          return ProcessInfoTreeNode(entry)
        }

        override fun compareNodesForSorting(node: ProcessInfoTreeNode, entry: ProcessInfo): Int {
          return ProcessInfoNameComparator.compare(node.processInfo, entry)
        }

        /**
         * Returns `true` if [node] and [entry] are equal. This is used by [updateChildrenNodes] to
         * know if a tree node needs to be rendered if it was already present in the tree.
         */
        override fun equals(node: ProcessInfoTreeNode, entry: ProcessInfo): Boolean {
          // ProcessInfo is a data class, which implements equality by comparing all fields.
          return node.processInfo == entry
        }

        override fun updateNode(node: ProcessInfoTreeNode, entry: ProcessInfo) {
          node.processInfo = entry
        }
      }

    TreeUtil.updateChildrenNodes(treeModel, parentNode, newEntries, updateChildrenOps)
    parentNode.allowsChildren = parentNode.childCount > 0
    thisLogger().debug("${parentNode.device}: Process list updated to ${newEntries.size} processes")
  }

  object ProcessInfoNameComparator : Comparator<ProcessInfo?> by nullsFirst(ProcessInfoNonNullComparator()) {
    private class ProcessInfoNonNullComparator : Comparator<ProcessInfo> {
      override fun compare(o1: ProcessInfo, o2: ProcessInfo): Int {
        return if (o1.isPidOnly && o2.isPidOnly) {
          o1.pid.compareTo(o2.pid)
        }
        else if (o1.isPidOnly) {
          1
        }
        else if (o2.isPidOnly) {
          -1
        }
        else {
          o1.safeProcessName.compareTo(o2.safeProcessName)
        }
      }
    }
  }
}