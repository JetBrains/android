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
package com.android.tools.idea.device.explorer.files.ui

import com.android.tools.idea.device.explorer.files.DeviceExplorerModelListener
import com.android.tools.idea.device.explorer.files.DeviceExplorerViewListener
import com.android.tools.idea.device.explorer.files.DeviceExplorerViewProgressListener
import com.android.tools.idea.device.explorer.files.DeviceFileEntryNode
import com.android.tools.idea.device.explorer.files.DeviceFileExplorerModel
import com.android.tools.idea.device.explorer.files.DeviceFileExplorerView
import com.android.tools.idea.device.explorer.files.fs.DeviceFileSystem
import com.android.tools.idea.device.explorer.files.ui.menu.item.CopyPathMenuItem
import com.android.tools.idea.device.explorer.files.ui.menu.item.DeleteNodesMenuItem
import com.android.tools.idea.device.explorer.files.ui.menu.item.NewDirectoryMenuItem
import com.android.tools.idea.device.explorer.files.ui.menu.item.NewFileMenuItem
import com.android.tools.idea.device.explorer.files.ui.menu.item.OpenMenuItem
import com.android.tools.idea.device.explorer.files.ui.menu.item.SaveAsMenuItem
import com.android.tools.idea.device.explorer.files.ui.menu.item.SynchronizeNodesMenuItem
import com.android.tools.idea.device.explorer.files.ui.menu.item.UploadFilesMenuItem
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.treeStructure.Tree
import icons.AndroidIcons
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.awt.GraphicsEnvironment
import java.awt.datatransfer.DataFlavor
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Arrays
import java.util.Objects
import java.util.concurrent.CancellationException
import java.util.function.Consumer
import java.util.stream.Collectors
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.TransferHandler
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.DefaultTreeSelectionModel
import javax.swing.tree.ExpandVetoException
import javax.swing.tree.TreePath

class DeviceFileExplorerViewImpl(
  project: Project,
  model: DeviceFileExplorerModel,
  private val toolWindowID: String
) : DeviceFileExplorerView {

  private val listeners: MutableList<DeviceExplorerViewListener> = ArrayList()
  private val progressListeners: MutableList<DeviceExplorerViewProgressListener> = ArrayList()
  private val panel = DeviceExplorerPanel()
  private val loadingPanel = JBLoadingPanel(BorderLayout(), project)
  private var treePopupMenu: ComponentPopupMenu? = null
  private var treeLoadingCount = 0
  private var fileExplorerActionListener = DeviceFileExplorerActionListenerImpl()

  init {
    model.addListener(ModelListener())
    panel.setCancelActionListener { _ ->
      progressListeners.forEach(Consumer { it.cancellationRequested() })
    }
  }

  override fun getComponent(): JComponent {
    return loadingPanel
  }

  override fun addListener(listener: DeviceExplorerViewListener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: DeviceExplorerViewListener) {
    listeners.remove(listener)
  }

  override fun addProgressListener(listener: DeviceExplorerViewProgressListener) {
    progressListeners.add(listener)
  }

  override fun removeProgressListener(listener: DeviceExplorerViewProgressListener) {
    progressListeners.remove(listener)
  }

  override fun setup() {
    setupPanel()
  }

  override fun reportErrorRelatedToDevice(fileSystem: DeviceFileSystem, message: String, t: Throwable) {
    val messageToReport =  if (t.message != null) "$message: ${t.message}" else message

    // If there is an error related to a device, show the error "layer", hiding the other
    // controls, until the user takes some action to fix the issue.
    panel.showErrorMessageLayer(messageToReport)
  }

  override fun reportErrorRelatedToNode(node: DeviceFileEntryNode, message: String, t: Throwable) {
    reportError(message, t, toolWindowID)
  }

  override fun reportMessageRelatedToDevice(fileSystem: DeviceFileSystem, message: String) {
    panel.showMessageLayer(message)
  }

  override fun reportMessageRelatedToNode(node: DeviceFileEntryNode, message: String) {
    reportMessage(message, toolWindowID)
  }

  override fun showNoDeviceScreen() {
    panel.showMessageLayer("Connect a device via USB cable or run an Android Virtual Device", AndroidIcons.Explorer.DevicesLineup)
  }

  override fun startTreeBusyIndicator() {
    incrementTreeLoading()
  }

  override fun stopTreeBusyIndicator() {
    decrementTreeLoading()
  }

  override fun expandNode(treeNode: DeviceFileEntryNode) {
    panel.tree.expandPath(TreePath(treeNode.path))
  }

  override fun startProgress() {
    panel.progressPanel.start()
  }

  override fun setProgressIndeterminate(indeterminate: Boolean) {
    panel.progressPanel.setIndeterminate(indeterminate)
  }

  override fun setProgressValue(fraction: Double) {
    panel.progressPanel.setProgress(fraction)
  }

  override fun setProgressOkColor() {
    panel.progressPanel.setOkStatusColor()
  }

  override fun setProgressWarningColor() {
    panel.progressPanel.setWarningStatusColor()
  }

  override fun setProgressErrorColor() {
    panel.progressPanel.setErrorStatusColor()
  }

  override fun setProgressText(text: String) {
    panel.progressPanel.setText(text)
  }

  override fun stopProgress() {
    panel.progressPanel.stop()
  }

  @TestOnly
  fun getFileTree(): JTree = panel.tree

  @TestOnly
  fun getFileTreeActionGroup(): ActionGroup? {
    return treePopupMenu?.actionGroup
  }

  @TestOnly
  fun getLoadingPanel(): JBLoadingPanel {
    return loadingPanel
  }

  @TestOnly
  fun getDeviceExplorerPanel(): DeviceExplorerPanel = panel

  private fun reportMessage(message: String, toolWindowID: String) {
    val notification = Notification(toolWindowID, toolWindowID, message, NotificationType.INFORMATION)
    ApplicationManager.getApplication().invokeLater {
      Notifications.Bus.notify(notification)
    }
  }

  private fun reportError(message: String, t: Throwable, toolWindowID: String) {
    if (t is CancellationException) {
      return
    }

    val messageToReport = if (t.message != null) "$message: ${t.message}" else message
    val notification = Notification(toolWindowID, toolWindowID, messageToReport, NotificationType.WARNING)
    ApplicationManager.getApplication().invokeLater {
      Notifications.Bus.notify(notification)
    }
  }

  private fun setupPanel() {
    loadingPanel.add(panel.component, BorderLayout.CENTER)
    val tree: Tree = panel.tree
    tree.addTreeWillExpandListener(getTreeWillExpandListener())
    tree.addMouseListener(getMouseListener(tree))
    tree.addKeyListener(getKeyListener(tree))
    tree.transferHandler = getTransferHandler(tree)
    tree.dragEnabled = !GraphicsEnvironment.isHeadless()
    createTreePopupMenu()
  }

  private fun getTreeWillExpandListener() = object : TreeWillExpandListener {
    @Throws(ExpandVetoException::class)
    override fun treeWillExpand(event: TreeExpansionEvent) {
      val node = DeviceFileEntryNode.fromNode(event.path.lastPathComponent)
      node?.let {
        expandTreeNode(it)
      }
    }

    @Throws(ExpandVetoException::class)
    override fun treeWillCollapse(event: TreeExpansionEvent) {}
  }

  private fun getMouseListener(tree: Tree) = object : MouseAdapter() {
    override fun mousePressed(e: MouseEvent) {
      // Double-click on a file should result in a file open
      if (e.clickCount == 2) {
        val selRow = tree.getRowForLocation(e.x, e.y)
        val selPath = tree.getPathForLocation(e.x, e.y)
        if (selRow != -1 && selPath != null) {
          openSelectedNodes(listOf(selPath))
        }
      }
    }
  }

  private fun getKeyListener(tree: Tree) = object : KeyAdapter() {
    override fun keyPressed(e: KeyEvent) {
      if (KeyEvent.VK_ENTER == e.keyCode) {
        val paths = tree.selectionPaths
        if (paths != null) {
          openSelectedNodes(listOf(*paths))
        }
      }
    }
  }

  private fun getTransferHandler(tree: Tree) = object : TransferHandler() {
    override fun importData(support: TransferSupport): Boolean {
      val t = support.transferable
      val files = FileCopyPasteUtil.getFiles(t) ?: return false
      val point = support.dropLocation.dropPoint
      val treePath = tree.getPathForLocation(point.getX().toInt(), point.getY().toInt()) ?: return false
      val node = DeviceFileEntryNode.fromNode(treePath.lastPathComponent)
      return if (node != null && node.entry.isDirectory) {
        listeners.forEach(Consumer { it.uploadFilesInvoked(node, files) })
        true
      }
      else {
        false
      }
    }

    override fun canImport(comp: JComponent, transferFlavors: Array<DataFlavor>): Boolean =
      FileCopyPasteUtil.isFileListFlavorAvailable(transferFlavors)
  }

  private fun createTreePopupMenu() {
    treePopupMenu = ComponentPopupMenu(panel.tree).apply {
      val fileMenu = addPopup("New")
      fileMenu.addItem(NewFileMenuItem(fileExplorerActionListener))
      fileMenu.addItem(NewDirectoryMenuItem(fileExplorerActionListener))
      addSeparator()
      addItem(OpenMenuItem(fileExplorerActionListener))
      addItem(SaveAsMenuItem(fileExplorerActionListener))
      addItem(UploadFilesMenuItem(fileExplorerActionListener))
      addItem(DeleteNodesMenuItem(fileExplorerActionListener))
      addSeparator()
      addItem(SynchronizeNodesMenuItem(fileExplorerActionListener))
      addItem(CopyPathMenuItem(fileExplorerActionListener))
      install()
    }
  }

  private fun openSelectedNodes(paths: List<TreePath>) {
    val nodes = paths.stream()
      .map { x: TreePath ->
        DeviceFileEntryNode.fromNode(x.lastPathComponent)
      }
      .filter { obj: DeviceFileEntryNode? ->
        Objects.nonNull(obj)
      }
      .collect(Collectors.toList<DeviceFileEntryNode>())
    fileExplorerActionListener.openNodes(nodes)
  }

  private fun expandTreeNode(node: DeviceFileEntryNode) {
    listeners.forEach(
      Consumer { it.treeNodeExpanding(node) })
  }

  private fun incrementTreeLoading() {
    if (treeLoadingCount == 0) {
      panel.tree.setPaintBusy(true)
    }
    treeLoadingCount++
  }

  private fun decrementTreeLoading() {
    treeLoadingCount--
    if (treeLoadingCount == 0) {
      panel.tree.setPaintBusy(false)
    }
  }

  private inner class ModelListener : DeviceExplorerModelListener {
    override fun treeModelChanged(newTreeModel: DefaultTreeModel?, newTreeSelectionModel: DefaultTreeSelectionModel?) {
      setRootFolder(newTreeModel, newTreeSelectionModel)
    }

    fun setRootFolder(model: DefaultTreeModel?, treeSelectionModel: DefaultTreeSelectionModel?) {
      val tree: Tree = panel.tree
      tree.model = model
      tree.selectionModel = treeSelectionModel
      if (model != null) {
        panel.showTree()
        val rootNode = DeviceFileEntryNode.fromNode(model.root)
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

  private inner class DeviceFileExplorerActionListenerImpl :  DeviceFileExplorerActionListener {
    override val selectedNodes: List<DeviceFileEntryNode>?
      get() {
        val paths: Array<TreePath>? = panel.tree.selectionPaths

        paths?.let {
          val nodes = Arrays.stream(paths)
            .map { path: TreePath ->
              DeviceFileEntryNode.fromNode(path.lastPathComponent)
            }
            .filter { obj: DeviceFileEntryNode? ->
              Objects.nonNull(obj)
            }
            .collect(Collectors.toList<DeviceFileEntryNode>())

          return if (nodes.isEmpty()) null else nodes
        }

        return null
      }

    override fun copyNodePaths(nodes: List<DeviceFileEntryNode>) {
      listeners.forEach(Consumer { it.copyNodePathsInvoked(nodes) })
    }

    override fun openNodes(nodes: List<DeviceFileEntryNode>) {
      listeners.forEach(Consumer { it.openNodesInEditorInvoked(nodes) })
    }

    override fun saveNodesAs(nodes: List<DeviceFileEntryNode>) {
      listeners.forEach(Consumer { it.saveNodesAsInvoked(nodes) })
    }

    override fun deleteNodes(nodes: List<DeviceFileEntryNode>) {
      listeners.forEach(Consumer { it.deleteNodesInvoked(nodes) })
    }

    override fun synchronizeNodes(nodes: List<DeviceFileEntryNode>) {
      listeners.forEach(Consumer { it.synchronizeNodesInvoked(nodes) })
    }

    override fun newFile(node: DeviceFileEntryNode) {
      listeners.forEach(Consumer { it.newFileInvoked(node) })
    }

    override fun newDirectory(node: DeviceFileEntryNode) {
      listeners.forEach(Consumer { it.newDirectoryInvoked(node) })
    }

    override fun uploadFile(node: DeviceFileEntryNode) {
      listeners.forEach(Consumer { it.uploadFilesInvoked(node) })
    }

  }
}