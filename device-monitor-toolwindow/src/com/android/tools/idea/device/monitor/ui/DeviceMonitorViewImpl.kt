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
import com.android.tools.idea.device.monitor.ui.menu.item.ForceStopMenuItem
import com.android.tools.idea.device.monitor.ui.menu.item.KillMenuItem
import com.android.tools.idea.device.monitor.ui.menu.item.MenuContext
import com.android.tools.idea.device.monitor.ui.menu.item.RefreshMenuItem
import com.google.common.util.concurrent.FutureCallback
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.treeStructure.Tree
import icons.AndroidIcons
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.util.concurrent.CancellationException
import java.util.function.Consumer
import javax.swing.JComboBox
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
) : DeviceMonitorView, DeviceMonitorActionsListener {
  private val myListeners: MutableList<DeviceMonitorViewListener> = ArrayList()
  private val myProgressListeners: MutableList<DeviceMonitorProgressListener> = ArrayList()
  private val myDeviceRenderer: DeviceRenderer
  private val myPanel = DeviceMonitorPanel()

  @get:TestOnly
  val loadingPanel: JBLoadingPanel

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

  @TestOnly
  fun getDeviceCombo(): JComboBox<Device?> {
    return myPanel.deviceCombo
  }

  @TestOnly
  fun getTree(): Tree {
    return myPanel.tree
  }

  override fun addListener(listener: DeviceMonitorViewListener) {
    myListeners.add(listener)
  }

  override fun removeListener(listener: DeviceMonitorViewListener) {
    myListeners.remove(listener)
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

  override fun reportErrorGeneric(message: String, t: Throwable) {
    reportError(message, t)
  }

  override fun reportMessageRelatedToDevice(fileSystem: Device, message: String) {
    myPanel.showMessageLayer(message, true)
  }

  override fun refreshNodes(treeNodes: List<ProcessTreeNode>) {
    myListeners.forEach(Consumer { x: DeviceMonitorViewListener -> x.refreshInvoked() })
  }

  override fun killNodes(treeNodes: List<ProcessTreeNode>) {
    myListeners.forEach(Consumer { x: DeviceMonitorViewListener -> x.killNodesInvoked(treeNodes) })
  }

  override fun forceStopNodes(treeNodes: List<ProcessTreeNode>) {
    myListeners.forEach(Consumer { x: DeviceMonitorViewListener -> x.forceStopNodesInvoked(treeNodes) })
  }

  override val selectedNodes: List<ProcessTreeNode>?
    get() {
      val paths = myPanel.tree.selectionPaths ?: return null
      val nodes = paths.mapNotNull { path -> ProcessTreeNode.fromNode(path.lastPathComponent) }.toList()
      return nodes.ifEmpty { null }
    }

  private fun setupPanel() {
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
    createToolbar()
    loadingPanel.setLoadingText("Initializing ADB")
    loadingPanel.startLoading()
  }

  private fun createTreePopupMenu() {
    ComponentPopupMenu(myPanel.tree).apply {
      addItem(ForceStopMenuItem(this@DeviceMonitorViewImpl, MenuContext.Popup))
      addItem(KillMenuItem(this@DeviceMonitorViewImpl, MenuContext.Popup))
      install()
    }
  }

  private fun createToolbar() {
    createToolbarSubSection(DefaultActionGroup().apply {
      add(ForceStopMenuItem(this@DeviceMonitorViewImpl, MenuContext.Toolbar).action)
      add(KillMenuItem(this@DeviceMonitorViewImpl, MenuContext.Toolbar).action) }, BorderLayout.WEST)

    createToolbarSubSection(DefaultActionGroup().apply {
      add(RefreshMenuItem(this@DeviceMonitorViewImpl).action) }, BorderLayout.EAST)
  }

  private fun createToolbarSubSection(group: DefaultActionGroup, layoutPosition: String) {
    val actionManager = ActionManager.getInstance()
    val actionToolbar = actionManager.createActionToolbar("Device Monitor Toolbar", group, true).apply {
      targetComponent = myPanel.tree
    }
    myPanel.toolbarPanel.add(actionToolbar.component, layoutPosition)
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

  override fun expandNode(treeNode: ProcessTreeNode) {
    myPanel.tree.expandPath(TreePath(treeNode.path))
  }

  private fun expandTreeNode(node: ProcessTreeNode) {
    myListeners.forEach(Consumer { x: DeviceMonitorViewListener -> x.treeNodeExpanding(node) })
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

    private fun setRootFolder(treeModel: DefaultTreeModel?, treeSelectionModel: DefaultTreeSelectionModel?) {
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
  }

  companion object {
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
