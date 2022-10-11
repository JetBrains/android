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
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.device.monitor.processes.Device
import com.android.tools.idea.device.monitor.processes.DeviceListService
import com.android.tools.idea.device.monitor.processes.DeviceListServiceListener
import com.android.tools.idea.device.monitor.processes.DeviceState
import com.android.tools.idea.device.monitor.processes.ProcessInfo
import com.android.tools.idea.device.monitor.processes.isPidOnly
import com.android.tools.idea.device.monitor.processes.safeProcessName
import com.android.tools.idea.device.monitor.ui.TreeUtil
import com.android.tools.idea.device.monitor.ui.TreeUtil.UpdateChildrenOps
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.DefaultTreeSelectionModel
import javax.swing.tree.MutableTreeNode

/**
 * Implementation of the Device Monitor application logic
 */
@UiThread
class DeviceMonitorController(
  project: Project,
  private val model: DeviceMonitorModel,
  private val view: DeviceMonitorView,
  private val service: DeviceListService
) : Disposable {

  /**
   * The [AndroidCoroutineScope] tied to the lifetime of this instance, i.e. active until [dispose] is called.
   */
  private val uiThreadScope = AndroidCoroutineScope(this, uiThread)

  private val setupJob = CompletableDeferred<Unit>()

  private val serviceListener = ServiceListener()

  private val viewListener = ViewListener()

  init {
    Disposer.register(project, this)
    service.addListener(serviceListener)
    view.addListener(viewListener)
    project.putUserData(KEY, this)
  }

  override fun dispose() {
    view.removeListener(viewListener)
    service.removeListener(serviceListener)
    uiThreadScope.cancel("${javaClass.simpleName} has been disposed")
  }

  private fun getTreeModel(): DefaultTreeModel? {
    return model.treeModel
  }

  private fun getTreeSelectionModel(): DefaultTreeSelectionModel? {
    return model.treeSelectionModel
  }

  fun setup() {
    uiThreadScope.launch {
      view.setup()
      view.startRefresh("Initializing ADB")
      try {
        service.start()
        setupJob.complete(Unit)
        refreshDeviceList(null)
      }
      catch (t: Throwable) {
        view.reportErrorRelatedToService(service, "Error initializing ADB", t)
        setupJob.completeExceptionally(t)
      }
      finally {
        view.stopRefresh()
      }
    }
  }

  private fun reportErrorFindingDevice(message: String) {
    view.reportErrorGeneric(message, IllegalStateException())
  }

  private suspend fun refreshDeviceList(serialNumberToSelect: String?) {
    view.startRefresh("Refreshing list of devices")
    try {
      val devices = service.devices
      model.removeAllDevices()
      devices.forEach { model.addDevice(it) }
      if (devices.isEmpty()) {
        view.showNoDeviceScreen()
      }
      else if (serialNumberToSelect != null) {
        when (val device = model.devices.find { it.serialNumber == serialNumberToSelect }) {
          null -> reportErrorFindingDevice("Unable to find device with serial number $serialNumberToSelect. Please retry.")
          else -> setActiveDevice(device)
        }
      }
    }
    catch (t: Throwable) {
      model.removeAllDevices()
      view.reportErrorRelatedToService(service, "Error refreshing list of devices", t)
    }
    finally {
      view.stopRefresh()
    }
  }

  private fun setNoActiveDevice() {
    model.activeDevice = null
    model.setActiveDeviceTreeModel(null, null, null)
    view.showNoDeviceScreen()
  }

  private fun setActiveDevice(device: Device) {
    ApplicationManagerEx.getApplicationEx().assertIsDispatchThread()

    model.activeDevice = device
    val treeModel = DefaultTreeModel(DeviceTreeNode(device))
    val treeSelectionModel = DefaultTreeSelectionModel()
    model.setActiveDeviceTreeModel(device, treeModel, treeSelectionModel)
    refreshActiveDevice(device)
  }

  private fun deviceStateUpdated(device: Device) {
    ApplicationManagerEx.getApplicationEx().assertIsDispatchThread()

    if (device != model.activeDevice) {
      return
    }

    // Refresh the active device view only if the device state has changed,
    // for example from offline -> online.
    val newState = device.state
    val lastKnownState = model.getActiveDeviceLastKnownState(device)
    if (newState == lastKnownState) {
      return
    }
    model.setActiveDeviceLastKnownState(device)
    refreshActiveDevice(device)
  }

  private fun refreshActiveDevice(device: Device) {
    ApplicationManagerEx.getApplicationEx().assertIsDispatchThread()

    if (device != model.activeDevice) {
      return
    }

    if (device.state != DeviceState.ONLINE) {
      val message = when (device.state) {
        DeviceState.UNAUTHORIZED ->
          "Device is pending authentication: please accept debugging session on the device"
        DeviceState.OFFLINE ->
          "Device is offline or booting: please restart device or wait until boot completes"
        else ->
          String.format("Device is not online (%s)", device.state)
      }
      view.reportMessageRelatedToDevice(device, message)
    }
    else {
      view.showActiveDeviceScreen()
    }
  }

  @UiThread
  private inner class ServiceListener : DeviceListServiceListener {
    override fun serviceRestarted() {
      uiThreadScope.launch {
        refreshDeviceList(null)
      }
    }

    override fun deviceAdded(device: Device) {
      model.addDevice(device)
    }

    override fun deviceRemoved(device: Device) {
      model.removeDevice(device)
    }

    override fun deviceUpdated(device: Device) {
      uiThreadScope.launch {
        model.updateDevice(device)
        deviceStateUpdated(device)
      }
    }

    override fun deviceProcessListUpdated(device: Device) {
      uiThreadScope.launch {
        val newProcessList = service.fetchProcessList(device)
        if (model.activeDevice != device) {
          return@launch
        }
        val deviceNode = DeviceTreeNode.fromNode(model.treeModel?.root) ?: return@launch
        refreshProcessList(deviceNode, newProcessList)
      }
    }
  }

  @UiThread
  private inner class ViewListener : DeviceMonitorViewListener {
    override fun noDeviceSelected() {
      setNoActiveDevice()
    }

    override fun deviceSelected(device: Device) {
      uiThreadScope.launch { setActiveDevice(device) }
    }

    override fun treeNodeExpanding(treeNode: ProcessTreeNode) {
      uiThreadScope.launch {
        loadNodeChildren(treeNode)
      }
    }

    override fun refreshInvoked() {
      uiThreadScope.launch {
        val rootNode = DeviceTreeNode.fromNode(model.treeModel?.root) ?: return@launch
        val processList = service.fetchProcessList(rootNode.device)
        if (model.activeDevice == rootNode.device) {
          refreshProcessList(rootNode, processList)
        }
      }
    }

    override fun killNodesInvoked(nodes: List<ProcessTreeNode>) {
      invokeOnProcessInfo(nodes) { processInfo ->
        service.killProcess(processInfo)
      }
    }

    override fun forceStopNodesInvoked(nodes: List<ProcessTreeNode>) {
      invokeOnProcessInfo(nodes) { processInfo ->
        service.forceStopProcess(processInfo)
      }
    }

    private fun invokeOnProcessInfo(nodes: List<ProcessTreeNode>, block: suspend (ProcessInfo) -> Unit) {
      uiThreadScope.launch {
        val rootNode = DeviceTreeNode.fromNode(model.treeModel?.root) ?: return@launch
        nodes.forEach { node ->
          val processInfoNode = ProcessInfoTreeNode.fromNode(node) ?: return@forEach
          if (model.activeDevice == rootNode.device) {
            block(processInfoNode.processInfo)
          }
        }
      }
    }

    private suspend fun loadNodeChildren(node: ProcessTreeNode) {
      // Ensure node is expanded only once
      if (node.isLoaded) {
        return
      }
      node.isLoaded = true

      // Leaf nodes are not expandable
      val rootNode = node as? DeviceTreeNode ?: return
      val processList = service.fetchProcessList(rootNode.device)
      if (model.activeDevice == rootNode.device) {
        refreshProcessList(rootNode, processList)
      }
    }
  }

  private fun refreshProcessList(deviceNode: DeviceTreeNode, processList: List<ProcessInfo>) {
    ApplicationManagerEx.getApplicationEx().assertIsDispatchThread()

    val treeModel = getTreeModel() ?: return
    val activeDevice = model.activeDevice
    if (activeDevice != deviceNode.device) {
      return
    }

    // Sort new entries according to presentation sort order
    updateChildrenNodes(treeModel, deviceNode, processList.sortedWith(ProcessInfoNameComparator))
  }

  private fun updateChildrenNodes(
    treeModel: DefaultTreeModel,
    parentNode: DeviceTreeNode,
    newEntries: List<ProcessInfo>
  ): List<ProcessTreeNode> {
    val updateChildrenOps: UpdateChildrenOps<ProcessInfoTreeNode, ProcessInfo> =
      object : UpdateChildrenOps<ProcessInfoTreeNode, ProcessInfo> {
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

    val addedNodes = TreeUtil.updateChildrenNodes(treeModel, parentNode, newEntries, updateChildrenOps)
    parentNode.allowsChildren = parentNode.childCount > 0
    view.expandNode(parentNode)
    thisLogger().debug("${parentNode.device}: Process list updated to ${newEntries.size} processes")
    return addedNodes
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

  @TestOnly
  fun hasActiveDevice(): Boolean {
    return model.activeDevice != null
  }

  @TestOnly
  fun selectActiveDevice(serialNumber: String) {
    uiThreadScope.launch {
      // This is called shortly after setup; wait for setup to complete
      setupJob.await()

      when (val device = model.devices.find { it.serialNumber == serialNumber }) {
        null -> refreshDeviceList(serialNumber)
        else -> setActiveDevice(device)
      }
    }
  }

  companion object {
    private val LOGGER = logger<DeviceMonitorController>()
    private val KEY = Key.create<DeviceMonitorController>(
      DeviceMonitorController::class.java.name
    )
    private const val DEVICE_MONITOR_BUSY_MESSAGE = "Device Monitor is busy, please retry later or cancel current operation"

    @JvmStatic
    fun getProjectController(project: Project?): DeviceMonitorController? {
      return project?.getUserData(KEY)
    }
  }
}
