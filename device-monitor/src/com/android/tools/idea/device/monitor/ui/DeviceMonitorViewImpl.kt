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
package com.android.tools.idea.device.monitor.ui

import com.android.tools.idea.ddms.DeviceNameProperties
import com.android.tools.idea.ddms.DeviceNamePropertiesFetcher
import com.android.tools.idea.device.monitor.DeviceMonitorModel
import com.android.tools.idea.device.monitor.DeviceMonitorModelListener
import com.android.tools.idea.device.monitor.DeviceMonitorProgressListener
import com.android.tools.idea.device.monitor.DeviceMonitorToolWindowFactory
import com.android.tools.idea.device.monitor.DeviceMonitorView
import com.android.tools.idea.device.monitor.DeviceMonitorViewListener
import com.android.tools.idea.device.monitor.DeviceNameRendererFactory
import com.android.tools.idea.device.monitor.ProcessTreeNode
import com.android.tools.idea.device.monitor.processes.Device
import com.android.tools.idea.device.monitor.processes.DeviceListService
import com.android.tools.idea.device.monitor.processes.DeviceRenderer
import com.google.common.util.concurrent.FutureCallback
import com.intellij.icons.AllIcons
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBLoadingPanel
import icons.AndroidIcons
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.util.concurrent.CancellationException
import java.util.function.Consumer
import java.util.function.Predicate
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.DefaultTreeSelectionModel
import javax.swing.tree.ExpandVetoException
import javax.swing.tree.TreePath

class DeviceMonitorViewImpl(
  project: Project,
  rendererFactory: DeviceNameRendererFactory,
  model: DeviceMonitorModel
) : DeviceMonitorView {
  private val myListeners: MutableList<DeviceMonitorViewListener> = ArrayList()
  private val myProgressListeners: MutableList<DeviceMonitorProgressListener> = ArrayList()
  private val myDeviceRenderer: DeviceRenderer
  private val myPanel: DeviceMonitorPanel = DeviceMonitorPanel()

  @get:TestOnly
  val loadingPanel: JBLoadingPanel
  private var myTreePopupMenu: ComponentPopupMenu? = null
  private var myTreeLoadingCount = 0

  init {
    model.addListener(ModelListener())
    myDeviceRenderer = rendererFactory.create(DeviceNamePropertiesFetcher(project, object : FutureCallback<DeviceNameProperties> {
      override fun onSuccess(result: DeviceNameProperties) {
        myPanel.deviceCombo.updateUI()
      }

      override fun onFailure(t: Throwable) {
        thisLogger().warn("Error retrieving device name properties", t)
      }
    }))
    myPanel.setCancelActionListener {
      myProgressListeners.forEach { it.cancellationRequested() }
    }
    loadingPanel = JBLoadingPanel(BorderLayout(), project)
  }

  val component: JComponent
    get() = loadingPanel

  override fun addListener(listener: DeviceMonitorViewListener) {
    myListeners.add(listener)
  }

  override fun removeListener(listener: DeviceMonitorViewListener) {
    myListeners.remove(listener)
  }

  override fun addProgressListener(listener: DeviceMonitorProgressListener) {
    myProgressListeners.add(listener)
  }

  override fun removeProgressListener(listener: DeviceMonitorProgressListener) {
    myProgressListeners.remove(listener)
  }

  override fun setup() {
    setupPanel()
  }

  override fun reportErrorRelatedToService(service: DeviceListService, message: String, t: Throwable) {
    var errorMessage = message
    if (t.message != null) {
      errorMessage += ": " + t.message
    }

    // If the file system service (i.e. ADB under the hood) had an error, there are no devices
    // to show until the user takes an action, so we show the error "layer", hiding the other
    // controls.
    myPanel.showErrorMessageLayer(errorMessage, false)
  }

  override fun reportErrorRelatedToDevice(fileSystem: Device, message: String, t: Throwable) {
    var errorMessage = message
    if (t.message != null) {
      errorMessage += ": " + t.message
    }

    // If there is an error related to a device, show the error "layer", hiding the other
    // controls, until the user takes some action to fix the issue.
    myPanel.showErrorMessageLayer(errorMessage, true)
  }

  override fun reportErrorRelatedToNode(node: ProcessTreeNode, message: String, t: Throwable) {
    reportError(message, t)
  }

  override fun reportErrorGeneric(message: String, t: Throwable) {
    reportError(message, t)
  }

  override fun reportMessageRelatedToDevice(fileSystem: Device, message: String) {
    myPanel.showMessageLayer(message, true)
  }

  override fun reportMessageRelatedToNode(node: ProcessTreeNode, message: String) {
    reportMessage(message)
  }

  private fun setupPanel() {
    myPanel.component.border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
    loadingPanel.add(myPanel.component, BorderLayout.CENTER)
    myPanel.deviceCombo.renderer = myDeviceRenderer.nameRenderer
    myPanel.deviceCombo.addActionListener {
      val sel = myPanel.deviceCombo.selectedItem
      if (sel is Device) {
        myListeners.forEach(Consumer { x: DeviceMonitorViewListener -> x.deviceSelected(sel) })
      }
      else {
        myListeners.forEach(Consumer { obj: DeviceMonitorViewListener -> obj.noDeviceSelected() })
      }
    }
    val tree = myPanel.tree
    tree.addTreeWillExpandListener(object : TreeWillExpandListener {
      @Throws(ExpandVetoException::class)
      override fun treeWillExpand(event: TreeExpansionEvent) {
        val node = ProcessTreeNode.fromNode(event.path.lastPathComponent)
        node?.let { expandTreeNode(it) }
      }

      @Throws(ExpandVetoException::class)
      override fun treeWillCollapse(event: TreeExpansionEvent) {
      }
    })
    createTreePopupMenu()
    loadingPanel.setLoadingText("Initializing ADB")
    loadingPanel.startLoading()
  }

  private fun createTreePopupMenu() {
    myTreePopupMenu = ComponentPopupMenu(myPanel.tree).apply {
      addItem(KillMenuItem())
      addItem(ForceStopMenuItem())
      addSeparator()
      addItem(RefreshMenuItem())
      install()
    }
  }

  private fun refreshNodes(treeNodes: List<ProcessTreeNode>) {
    myListeners.forEach(Consumer { x: DeviceMonitorViewListener -> x.refreshInvoked() })
  }

  private fun killNodes(treeNodes: List<ProcessTreeNode>) {
    myListeners.forEach(Consumer { x: DeviceMonitorViewListener -> x.killNodesInvoked(treeNodes) })
  }

  private fun forceStopNodes(treeNodes: List<ProcessTreeNode>) {
    myListeners.forEach(Consumer { x: DeviceMonitorViewListener -> x.forceStopNodesInvoked(treeNodes) })
  }

  override fun startRefresh(text: String) {
    myPanel.showMessageLayer("", false)
    loadingPanel.setLoadingText(text)
    loadingPanel.startLoading()
  }

  override fun stopRefresh() {
    loadingPanel.stopLoading()
  }

  override fun showNoDeviceScreen() {
    myPanel.showMessageLayer(
      "Connect a device via USB cable or run an Android Virtual Device",
      AndroidIcons.DeviceExplorer.DevicesLineup,
      false
    )
  }

  override fun showActiveDeviceScreen() {
    myPanel.showTree()
  }

  fun setRootFolder(treeModel: DefaultTreeModel?, treeSelectionModel: DefaultTreeSelectionModel?) {
    val tree = myPanel.tree
    tree.model = treeModel
    tree.selectionModel = treeSelectionModel
    if (treeModel != null) {
      myPanel.showTree()
      val rootNode = ProcessTreeNode.fromNode(treeModel.root)
      if (rootNode != null) {
        tree.isRootVisible = false
        expandTreeNode(rootNode)
      }
      else {
        // Show root, since it contains an error message (ErrorNode)
        tree.isRootVisible = true
      }
    }
  }

  override fun startTreeBusyIndicator() {
    incrementTreeLoading()
  }

  override fun stopTreeBusyIndicator() {
    decrementTreeLoading()
  }

  override fun expandNode(treeNode: ProcessTreeNode) {
    myPanel.tree.expandPath(TreePath(treeNode.path))
  }

  override fun startProgress() {
    myPanel.progressPanel.start()
  }

  override fun setProgressIndeterminate(indeterminate: Boolean) {
    myPanel.progressPanel.setIndeterminate(indeterminate)
  }

  override fun setProgressValue(fraction: Double) {
    myPanel.progressPanel.setProgress(fraction)
  }

  override fun setProgressOkColor() {
    myPanel.progressPanel.setOkStatusColor()
  }

  override fun setProgressWarningColor() {
    myPanel.progressPanel.setWarningStatusColor()
  }

  override fun setProgressErrorColor() {
    myPanel.progressPanel.setErrorStatusColor()
  }

  override fun setProgressText(text: String) {
    myPanel.progressPanel.setText(text)
  }

  override fun stopProgress() {
    myPanel.progressPanel.stop()
  }

  private fun expandTreeNode(node: ProcessTreeNode) {
    myListeners.forEach(Consumer { x: DeviceMonitorViewListener -> x.treeNodeExpanding(node) })
  }

  private fun incrementTreeLoading() {
    if (myTreeLoadingCount == 0) {
      myPanel.tree.setPaintBusy(true)
    }
    myTreeLoadingCount++
  }

  private fun decrementTreeLoading() {
    myTreeLoadingCount--
    if (myTreeLoadingCount == 0) {
      myPanel.tree.setPaintBusy(false)
    }
  }

  private inner class ModelListener : DeviceMonitorModelListener {
    override fun allDevicesRemoved() {
      myPanel.deviceCombo.removeAllItems()
    }

    override fun deviceAdded(device: Device) {
      myPanel.deviceCombo.addItem(device)
    }

    override fun deviceRemoved(device: Device) {
      myPanel.deviceCombo.removeItem(device)
    }

    override fun deviceUpdated(device: Device) {
      if (myPanel.deviceCombo.selectedItem === device) {
        myPanel.deviceCombo.repaint()
      }
    }

    override fun activeDeviceChanged(newActiveDevice: Device?) {
      if (newActiveDevice != null && newActiveDevice != myPanel.deviceCombo.selectedItem) {
        myPanel.deviceCombo.selectedItem = newActiveDevice
      }
    }

    override fun treeModelChanged(newTreeModel: DefaultTreeModel?, newTreeSelectionModel: DefaultTreeSelectionModel?) {
      setRootFolder(newTreeModel, newTreeSelectionModel)
    }
  }

  /**
   * A popup menu item that works for both single and multi-element selections.
   */
  private abstract inner class TreeMenuItem : PopupMenuItem {
    override val text: String
      get() {
        var nodes = selectedNodes
        if (nodes == null) {
          nodes = emptyList<ProcessTreeNode>()
        }
        return getText(nodes)
      }

    abstract fun getText(nodes: List<ProcessTreeNode>): String

    override val icon: Icon?
      get() {
        return null
      }

    override val isEnabled: Boolean
      get() {
        val nodes = selectedNodes ?: return false
        return isEnabled(nodes)
      }

    open fun isEnabled(nodes: List<ProcessTreeNode>): Boolean {
      return nodes.stream().anyMatch(Predicate { node: ProcessTreeNode -> this.isEnabled(node) })
    }

    override val isVisible: Boolean
      get() {
        val nodes = selectedNodes ?: return false
        return isVisible(nodes)
      }

    open fun isVisible(nodes: List<ProcessTreeNode>): Boolean {
      return nodes.stream().anyMatch { node: ProcessTreeNode -> this.isVisible(node) }
    }

    override fun run() {
      var nodes = selectedNodes ?: return
      nodes = nodes.filter { node: ProcessTreeNode -> this.isEnabled(node) }.toList()
      if (nodes.isNotEmpty()) {
        run(nodes)
      }
    }

    private val selectedNodes: List<ProcessTreeNode>?
      get() {
        val paths = myPanel.tree.selectionPaths ?: return null
        val nodes = paths.mapNotNull { path -> ProcessTreeNode.fromNode(path.lastPathComponent) }.toList()
        return nodes.ifEmpty { null }
      }

    open fun isVisible(node: ProcessTreeNode): Boolean {
      return true
    }

    fun isEnabled(node: ProcessTreeNode): Boolean {
      return isVisible(node)
    }

    abstract fun run(nodes: List<ProcessTreeNode>)
  }

  /**
   * A [TreeMenuItem] that is active only for single element selections
   */
  private abstract inner class SingleSelectionTreeMenuItem : TreeMenuItem() {
    override fun isEnabled(nodes: List<ProcessTreeNode>): Boolean {
      return super.isEnabled(nodes) && nodes.size == 1
    }

    override fun isVisible(nodes: List<ProcessTreeNode>): Boolean {
      return super.isVisible(nodes) && nodes.size == 1
    }

    override fun run(nodes: List<ProcessTreeNode>) {
      if (nodes.size == 1) {
        run(nodes[0])
      }
    }

    abstract fun run(node: ProcessTreeNode)
  }

  private inner class RefreshMenuItem : TreeMenuItem() {
    override fun getText(nodes: List<ProcessTreeNode>): String {
      return "Refresh"
    }

    override val icon: Icon
      get() {
        return AllIcons.Actions.Refresh
      }

    override val shortcutId: String
      get() {
        // Re-use existing shortcut, see platform/platform-resources/src/keymaps/$default.xml
        return "Refresh"
      }

    override fun isVisible(node: ProcessTreeNode): Boolean {
      return true
    }

    override fun run(nodes: List<ProcessTreeNode>) {
      refreshNodes(nodes)
    }
  }

  private inner class KillMenuItem : TreeMenuItem() {
    override fun getText(nodes: List<ProcessTreeNode>): String {
      return "Kill process"
    }

    override val icon: Icon
      get() {
        return AllIcons.Debugger.KillProcess
      }

    override val shortcutId: String
      get() {
        // Re-use existing shortcut, see platform/platform-resources/src/keymaps/$default.xml
        return "KillProcess"
      }

    override fun isVisible(node: ProcessTreeNode): Boolean {
      return true
    }

    override fun run(nodes: List<ProcessTreeNode>) {
      killNodes(nodes)
    }
  }

  private inner class ForceStopMenuItem : TreeMenuItem() {
    override fun getText(nodes: List<ProcessTreeNode>): String {
      return "Force stop process"
    }

    override val icon: Icon
      get() {
        return AllIcons.Debugger.KillProcess
      }

    override val shortcutId: String
      get() {
        // Re-use existing shortcut, see platform/platform-resources/src/keymaps/$default.xml
        return "ForceStopProcess"
      }

    override fun isVisible(node: ProcessTreeNode): Boolean {
      return true
    }

    override fun run(nodes: List<ProcessTreeNode>) {
      forceStopNodes(nodes)
    }
  }

  companion object {
    private fun reportMessage(message: String) {
      val notification = Notification(
        DeviceMonitorToolWindowFactory.TOOL_WINDOW_ID,
        DeviceMonitorToolWindowFactory.TOOL_WINDOW_ID,
        message,
        NotificationType.INFORMATION
      )
      ApplicationManager.getApplication().invokeLater { Notifications.Bus.notify(notification) }
    }

    private fun reportError(message: String, t: Throwable) {
      if (t is CancellationException) {
        return
      }
      var errorMessage = message
      if (t.message != null) {
        errorMessage += ": " + t.message
      }
      val notification = Notification(
        DeviceMonitorToolWindowFactory.TOOL_WINDOW_ID,
        DeviceMonitorToolWindowFactory.TOOL_WINDOW_ID,
        errorMessage,
        NotificationType.WARNING
      )
      ApplicationManager.getApplication().invokeLater { Notifications.Bus.notify(notification) }
    }
  }
}
