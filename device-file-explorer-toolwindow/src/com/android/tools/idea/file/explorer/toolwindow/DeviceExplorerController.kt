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
package com.android.tools.idea.file.explorer.toolwindow

import com.android.annotations.concurrency.UiThread
import com.android.tools.analytics.UsageTracker.log
import com.android.tools.idea.concurrency.AndroidDispatchers.diskIoThread
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.file.explorer.toolwindow.adbimpl.AdbPathUtil
import com.android.tools.idea.file.explorer.toolwindow.fs.DeviceFileEntry
import com.android.tools.idea.file.explorer.toolwindow.fs.DeviceFileSystem
import com.android.tools.idea.file.explorer.toolwindow.fs.DeviceFileSystemService
import com.android.tools.idea.file.explorer.toolwindow.fs.DeviceState
import com.android.tools.idea.file.explorer.toolwindow.fs.DownloadProgress
import com.android.tools.idea.file.explorer.toolwindow.fs.FileTransferProgress
import com.android.tools.idea.file.explorer.toolwindow.ui.TreeUtil
import com.android.tools.idea.file.explorer.toolwindow.ui.TreeUtil.UpdateChildrenOps
import com.android.utils.FileUtils
import com.google.common.base.Stopwatch
import com.google.common.base.Strings.emptyToNull
import com.google.common.primitives.Ints
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DeviceExplorerEvent
import com.intellij.CommonBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.UIBundle
import com.intellij.util.Alarm
import com.intellij.util.ArrayUtil
import com.intellij.util.ExceptionUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.time.withTimeout
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.awt.datatransfer.StringSelection
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.LinkedList
import java.util.Locale
import java.util.Stack
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicReference
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.DefaultTreeSelectionModel
import javax.swing.tree.MutableTreeNode
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

/**
 * Implementation of the Device File Explorer application logic
 */
@UiThread
class DeviceExplorerController(
  private val project: Project,
  private val model: DeviceExplorerModel,
  private val view: DeviceExplorerView,
  private val service: DeviceFileSystemService<out DeviceFileSystem>,
  private val fileManager: DeviceExplorerFileManager,
  private val fileOpener: FileOpener
) : Disposable {

  private val scope = project.coroutineScope + uiThread
  var showLoadingNodeDelayMillis = 200
    @TestOnly set
  var transferringNodeRepaintMillis = 100
    @TestOnly set

  private val workEstimator = FileTransferWorkEstimator()
  private val transferringNodes: MutableSet<DeviceFileEntryNode> = HashSet()
  private val loadingChildren: MutableSet<DeviceFileEntryNode> = HashSet()
  private val loadingNodesAlarms = Alarm()
  private val transferringNodesAlarms = Alarm()
  private val loadingChildrenAlarms = Alarm()
  private var longRunningOperationTracker: LongRunningOperationTracker? = null

  init {
    Disposer.register(project, this)
    view.addListener(ViewListener())
    project.putUserData(KEY, this)
  }

  override fun dispose() {}

  private fun getTreeModel(): DefaultTreeModel? {
    return model.treeModel
  }

  private fun getTreeSelectionModel(): DefaultTreeSelectionModel? {
    return model.treeSelectionModel
  }

  fun setup() {
    scope.launch {
      view.setup()
      view.startRefresh("Refreshing list of devices")
      try {
        trackServiceChanges()
      } finally {
        view.stopRefresh()
      }
    }
  }

  fun reportErrorFindingDevice(message: String) {
    view.reportErrorGeneric(message, IllegalStateException())
  }

  fun selectActiveDevice(serialNumber: String) {
    scope.launch {
      cancelOrMoveToBackgroundPendingOperations()

      when (val device = model.devices.find { it.deviceSerialNumber == serialNumber }) {
        null -> reportErrorFindingDevice("Unable to find device with serial number $serialNumber. Please retry.")
        else -> setActiveDevice(device)
      }
    }
  }

  private fun setNoActiveDevice() {
    cancelOrMoveToBackgroundPendingOperations()
    model.activeDevice = null
    model.setActiveDeviceTreeModel(null, null, null)
    view.showNoDeviceScreen()
  }

  private suspend fun setActiveDevice(device: DeviceFileSystem) {
    cancelOrMoveToBackgroundPendingOperations()
    model.activeDevice = device
    trackAction(DeviceExplorerEvent.Action.DEVICE_CHANGE)
    updateActiveDeviceState(device, device.deviceState)
  }

  /** Updates the view and tree model to reflect the given device and state. */
  private suspend fun updateActiveDeviceState(device: DeviceFileSystem, state: DeviceState) {
    if (state != DeviceState.ONLINE) {
      val message = when (state) {
        DeviceState.UNAUTHORIZED, DeviceState.OFFLINE ->
          "Device is pending authentication: please accept debugging session on the device"
        else ->
          String.format("Device is not online (%s)", state)
      }
      view.reportMessageRelatedToDevice(device, message)
      model.setActiveDeviceTreeModel(device, null, null)
      return
    }
    try {
      val root = device.rootDirectory()
      val model = DefaultTreeModel(DeviceFileEntryNode(root))
      this.model.setActiveDeviceTreeModel(device, model, DefaultTreeSelectionModel())
    } catch (t: Throwable) {
      model.setActiveDeviceTreeModel(device, null, null)
      view.reportErrorRelatedToDevice(device, "Unable to access root directory of device", t)
    }
  }

  private fun cancelOrMoveToBackgroundPendingOperations() {
    loadingNodesAlarms.cancelAllRequests()
    loadingChildrenAlarms.cancelAllRequests()
    transferringNodesAlarms.cancelAllRequests()
    loadingChildren.clear()
    transferringNodes.clear()
    if (longRunningOperationTracker != null) {
      if (longRunningOperationTracker!!.isBackgroundable) {
        longRunningOperationTracker!!.moveToBackground()
        longRunningOperationTracker = null
      } else {
        longRunningOperationTracker!!.cancel()
      }
    }
  }

  private fun startNodeDownload(node: DeviceFileEntryNode) {
    startNodeTransfer(node, true)
  }

  private fun startNodeUpload(node: DeviceFileEntryNode) {
    startNodeTransfer(node, false)
  }

  private fun startNodeTransfer(node: DeviceFileEntryNode, download: Boolean) {
    view.startTreeBusyIndicator()
    if (download) {
      node.isDownloading = true
    } else {
      node.isUploading = true
    }
    if (transferringNodes.isEmpty()) {
      transferringNodesAlarms.addRequest(::repaintTransferringNodes, transferringNodeRepaintMillis)
    }
    transferringNodes.add(node)
  }

  private fun stopNodeDownload(node: DeviceFileEntryNode) {
    stopNodeTransfer(node, true)
  }

  private fun stopNodeUpload(node: DeviceFileEntryNode) {
    stopNodeTransfer(node, false)
  }

  private fun stopNodeTransfer(node: DeviceFileEntryNode, download: Boolean) {
    view.stopTreeBusyIndicator()
    if (download) {
      node.isDownloading = false
    } else {
      node.isUploading = false
    }
    getTreeModel()?.nodeChanged(node)
    transferringNodes.remove(node)
    if (transferringNodes.isEmpty()) {
      transferringNodesAlarms.cancelAllRequests()
    }
  }

  private fun startLoadChildren(node: DeviceFileEntryNode) {
    view.startTreeBusyIndicator()
    if (loadingChildren.isEmpty()) {
      loadingChildrenAlarms.addRequest(::repaintLoadingChildren, transferringNodeRepaintMillis)
    }
    loadingChildren.add(node)
  }

  private fun stopLoadChildren(node: DeviceFileEntryNode) {
    view.stopTreeBusyIndicator()
    loadingChildren.remove(node)
    if (loadingChildren.isEmpty()) {
      loadingChildrenAlarms.cancelAllRequests()
    }
  }

  @VisibleForTesting
  fun checkLongRunningOperationAllowed(): Boolean {
    return longRunningOperationTracker == null
  }

  private fun registerLongRunningOperation(tracker: LongRunningOperationTracker) {
    if (!checkLongRunningOperationAllowed()) {
      throw Exception(DEVICE_EXPLORER_BUSY_MESSAGE)
    }
    longRunningOperationTracker = tracker
    Disposer.register(tracker) {
      assert(ApplicationManager.getApplication().isDispatchThread)
      if (longRunningOperationTracker === tracker) {
        longRunningOperationTracker = null
      }
    }
  }

  fun hasActiveDevice(): Boolean {
    return model.activeDevice != null
  }


  private fun trackServiceChanges() = scope.launch(uiThread) {
    val devices = mutableSetOf<DeviceFileSystem>()
    service.devices.collect { newDevices ->
      (devices - newDevices).forEach {
        devices.remove(it)
        model.removeDevice(it)
      }

      for (device in newDevices) {
        if (devices.add(device)) {
          model.addDevice(device)
          device.scope.launch(uiThread) {
            device.deviceStateFlow.collect { state ->
              model.updateDevice(device)
              if (device == model.activeDevice) {
                updateActiveDeviceState(device, state)
              }
            }
          }
        }
      }

      if (newDevices.isEmpty()) {
        view.showNoDeviceScreen()
      }
    }
  }

  @UiThread
  private inner class ViewListener : DeviceExplorerViewListener {
    override fun noDeviceSelected() {
      setNoActiveDevice()
    }

    override fun deviceSelected(device: DeviceFileSystem) {
      scope.launch { setActiveDevice(device) }
    }

    override fun openNodesInEditorInvoked(treeNodes: List<DeviceFileEntryNode>) {
      if (treeNodes.isEmpty()) {
        return
      }
      if (!checkLongRunningOperationAllowed()) {
        view.reportErrorRelatedToNode(getCommonParentNode(treeNodes), DEVICE_EXPLORER_BUSY_MESSAGE, RuntimeException())
        return
      }
      scope.launch {
        val device = model.activeDevice
        for (treeNode in treeNodes) {
          if (device == model.activeDevice && !treeNode.entry.isDirectory) {
            if (treeNode.isTransferring) {
              view.reportErrorRelatedToNode(treeNode, "Entry is already downloading or uploading", RuntimeException())
            }
            else if (!treeNode.entry.isSymbolicLinkToDirectory()) {
              downloadAndOpenFile(treeNode)
            }
          }
        }
      }
    }

    private suspend fun openFile(treeNode: DeviceFileEntryNode, localPath: Path) {
      try {
        fileOpener.openFile(localPath)
      } catch (t: Throwable) {
        view.reportErrorRelatedToNode(treeNode, "Unable to open file \"$localPath\" in editor", t)
      }
    }

    private suspend fun downloadAndOpenFile(treeNode: DeviceFileEntryNode) = withContext(uiThread) {
      try {
        val path = downloadFileEntryToDefaultLocation(treeNode)
        DeviceExplorerFilesUtils.findFile(path)
        openFile(treeNode, path)
      }
      catch (t: Throwable) {
        view.reportErrorRelatedToNode(treeNode, "Error opening contents of device file ${getUserFacingNodeName(treeNode)}", t)
      }
    }

    private fun getTreeNodeFromEntry(treeNode: DeviceFileEntryNode, entryFullPath: String): DeviceFileEntryNode? {
      val treeNodeRoot = getTreeNodeRoot(treeNode) as? DeviceFileEntryNode ?: return null
      return findDeviceFileEntryNodeFromPath(treeNodeRoot, entryFullPath)
    }

    private fun getTreeNodeRoot(node: TreeNode): TreeNode {
      var node = node
      while (node.parent != null) node = node.parent
      return node
    }

    private fun findDeviceFileEntryNodeFromPath(root: DeviceFileEntryNode, entryFullPath: String): DeviceFileEntryNode? {
      val pathComponents = AdbPathUtil.getSegments(entryFullPath)
      if (pathComponents.isEmpty()) {
        return root
      }
      var currentNode: DeviceFileEntryNode = root
      for (segment in pathComponents) {
        currentNode = currentNode.findChildEntry(segment) ?: return null
      }
      return currentNode
    }

    private suspend fun downloadFileEntryToDefaultLocation(treeNode: DeviceFileEntryNode): Path {
      val localPath = fileManager.getDefaultLocalPathForEntry(treeNode.entry)
      wrapFileTransfer(
        { tracker: FileTransferOperationTracker -> addDownloadOperationWork(tracker, treeNode) },
        { tracker: FileTransferOperationTracker -> downloadFileEntry(treeNode, localPath, tracker) },
        true)
      return localPath
    }

    override fun saveNodesAsInvoked(treeNodes: List<DeviceFileEntryNode>) {
      if (treeNodes.isEmpty()) {
        return
      }
      val commonParentNode = getCommonParentNode(treeNodes)
      if (!checkLongRunningOperationAllowed()) {
        view.reportErrorRelatedToNode(commonParentNode, DEVICE_EXPLORER_BUSY_MESSAGE, RuntimeException())
        return
      }
      scope.launch {
        try {
          val summary = if (treeNodes.size == 1) saveSingleNodeAs(treeNodes[0]) else saveMultiNodesAs(commonParentNode, treeNodes)
          summary.action = DeviceExplorerEvent.Action.SAVE_AS
          reportSaveNodesAsSummary(commonParentNode, summary)
        }
        catch (t: Throwable) {
          view.reportErrorRelatedToNode(commonParentNode, "Error saving file(s) to local file system", t)
        }
      }
    }

    private fun reportSaveNodesAsSummary(node: DeviceFileEntryNode, summary: FileTransferSummary) {
      reportFileTransferSummary(node, summary, "downloaded", "downloading")
    }

    private suspend fun saveSingleNodeAs(treeNode: DeviceFileEntryNode): FileTransferSummary {
      // When saving a single entry, we should consider whether the entry is a symbolic link to
      // a directory, not just a plain directory.
      return if (treeNode.entry.isDirectory || treeNode.isSymbolicLinkToDirectory) {
        // If single directory, choose the local directory path to download to, then download
        val localDirectory = chooseSaveAsDirectoryPath(treeNode) ?: cancelAndThrow()
        wrapFileTransfer(
          { tracker: FileTransferOperationTracker -> addDownloadOperationWork(tracker, treeNode) },
          { tracker: FileTransferOperationTracker -> downloadSingleDirectory(treeNode, localDirectory, tracker) },
          false)
      }
      else {
        // If single file, choose the local file path to download to, then download
        val localFile = chooseSaveAsFilePath(treeNode) ?: cancelAndThrow()
        wrapFileTransfer(
          { tracker: FileTransferOperationTracker -> addDownloadOperationWork(tracker, treeNode) },
          { tracker: FileTransferOperationTracker -> downloadSingleFile(treeNode, localFile, tracker) },
          false)
      }
    }

    private suspend fun saveMultiNodesAs(
      commonParentNode: DeviceFileEntryNode,
      treeNodes: List<DeviceFileEntryNode>
    ): FileTransferSummary {
      assert(!treeNodes.isEmpty())

      // For downloading multiple entries, choose a local directory path to download to, then download
      // each entry relative to the chosen path
      val localDirectory = chooseSaveAsDirectoryPath(commonParentNode) ?: cancelAndThrow()
      return wrapFileTransfer(
        { tracker: FileTransferOperationTracker -> addDownloadOperationWork(tracker, treeNodes) },
        { tracker: FileTransferOperationTracker ->
          for (treeNode in treeNodes) {
            val nodePath = localDirectory.resolve(treeNode.entry.name)
            downloadSingleNode(treeNode, nodePath, tracker)
          }
        },
        true)
    }

    /**
     * Wrap a file transfer operation (either "SaveAs" or "Upload") so that the operation
     * shows various UI elements related to progress (and resets them when the operation
     * is over).
     *
     * @param prepareTransfer An operation to run before the transfer, typically
     * to estimate the amount of work, used for tracking progress later on
     * @param performTransfer The transfer operation itself
     * @return a [FileTransferSummary] when the whole transfer operation finishes
     * @throws CancellationException if the operation is canceled
     */
    private suspend fun wrapFileTransfer(
      prepareTransfer: suspend (FileTransferOperationTracker) -> Unit,
      performTransfer: suspend (FileTransferOperationTracker) -> Unit,
      backgroundable: Boolean
    ): FileTransferSummary = withContext(uiThread) {
      val tracker = FileTransferOperationTracker(view, backgroundable)
      registerLongRunningOperation(tracker)
      tracker.start()
      tracker.setCalculatingText(0, 0)
      tracker.setIndeterminate(true)
      Disposer.register(this@DeviceExplorerController, tracker)
      view.startTreeBusyIndicator()
      try {
        prepareTransfer(tracker)
        tracker.setIndeterminate(false)
        performTransfer(tracker)
      } finally {
        view.stopTreeBusyIndicator()
        Disposer.dispose(tracker)
      }
      tracker.summary
    }

    suspend fun addUploadOperationWork(tracker: FileTransferOperationTracker, files: List<Path>) =
      files.forEach { addUploadOperationWork(tracker, it) }

    suspend fun addUploadOperationWork(tracker: FileTransferOperationTracker, path: Path) {
      val progress = createFileTransferEstimatorProgress(tracker)
      val estimate = workEstimator.estimateUploadWork(path, progress)
      tracker.addWorkEstimate(estimate)
    }

    suspend fun addDownloadOperationWork(
      tracker: FileTransferOperationTracker,
      entryNodes: List<DeviceFileEntryNode>
    ) {
      entryNodes.forEach { addDownloadOperationWork(tracker, it) }
    }

    suspend fun addDownloadOperationWork(
      tracker: FileTransferOperationTracker,
      entryNode: DeviceFileEntryNode
    ) {
      val progress = createFileTransferEstimatorProgress(tracker)
      val estimate = workEstimator.estimateDownloadWork(entryNode.entry, entryNode.isSymbolicLinkToDirectory, progress)
      tracker.addWorkEstimate(estimate)
    }

    private fun createFileTransferEstimatorProgress(tracker: FileTransferOperationTracker): FileTransferWorkEstimatorProgress {
      return object : FileTransferWorkEstimatorProgress {
        override fun progress(fileCount: Int, directoryCount: Int) {
          tracker.setCalculatingText(fileCount, directoryCount)
        }

        override fun isCancelled(): Boolean {
          return tracker.isCancelled
        }
      }
    }

    private suspend fun downloadSingleNode(
      node: DeviceFileEntryNode,
      localPath: Path,
      tracker: FileTransferOperationTracker
    ) {
      if (node.entry.isDirectory) {
        downloadSingleDirectory(node, localPath, tracker)
      } else {
        downloadSingleFile(node, localPath, tracker)
      }
    }

    private suspend fun downloadSingleFile(
      treeNode: DeviceFileEntryNode,
      localPath: Path,
      tracker: FileTransferOperationTracker
    ) {
      assert(!treeNode.entry.isDirectory)

      // Download single file
      if (treeNode.isTransferring) {
        tracker.addProblem(Exception(String.format("File %s is already downloading or uploading", getUserFacingNodeName(treeNode))))
        return
      }
      try {
        val entrySize = downloadFileEntry(treeNode, localPath, tracker)
        tracker.summary.addFileCount(1)
        tracker.summary.addByteCount(entrySize)
      }
      catch (t: Throwable) {
        tracker.addProblem(Exception("Error saving contents of device file ${getUserFacingNodeName(treeNode)}", t))
      }
    }

    private suspend fun downloadSingleDirectory(
      treeNode: DeviceFileEntryNode,
      localDirectoryPath: Path,
      tracker: FileTransferOperationTracker
    ) {
      assert(treeNode.entry.isDirectory || treeNode.isSymbolicLinkToDirectory)
      if (tracker.isCancelled) {
        cancelAndThrow()
      }
      tracker.processDirectory()

      withContext(diskIoThread) {
        // Ensure directory is created locally
        FileUtils.mkdirs(localDirectoryPath.toFile())
      }
      tracker.summary.addDirectoryCount(1)
      try {
        loadNodeChildren(treeNode)
        for (node in treeNode.childEntryNodes) {
          val nodePath = localDirectoryPath.resolve(node.entry.name)
          downloadSingleNode(node, nodePath, tracker)
        }
      } catch (t: Throwable) {
        tracker.addProblem(t)
      }
    }

    override fun copyNodePathsInvoked(treeNodes: List<DeviceFileEntryNode>) {
      val text = treeNodes.map { it.entry.fullPath }.joinToString("\n")
      CopyPasteManager.getInstance().setContents(StringSelection(text))
      trackAction(DeviceExplorerEvent.Action.COPY_PATH)
    }

    override fun newFileInvoked(parentTreeNode: DeviceFileEntryNode) {
      scope.launch {
        newFileOrDirectory(parentTreeNode,
                           "NewTextFile.txt",
                           UIBundle.message("new.file.dialog.title"),
                           UIBundle.message("create.new.file.enter.new.file.name.prompt.text"),
                           UIBundle.message("create.new.file.file.name.cannot.be.empty.error.message"),
                           { UIBundle.message("create.new.file.could.not.create.file.error.message", it) },
                           { parentTreeNode.entry.createNewFile(it) })
        trackAction(DeviceExplorerEvent.Action.NEW_FILE)
      }
    }

    override fun synchronizeNodesInvoked(nodes: List<DeviceFileEntryNode>) {
      if (nodes.isEmpty()) {
        return
      }

      // Collect directories as well as parent directories of files
      var directoryNodes = nodes
        .mapNotNull {
          when {
            it.isSymbolicLinkToDirectory || it.entry.isDirectory -> it
            else -> DeviceFileEntryNode.fromNode(it.parent)
          }
        }
        .toSet()

      // Add descendant directories that have been expanded/loaded
      directoryNodes = directoryNodes.flatMap { node ->
        val nodesToSynchronize: MutableList<DeviceFileEntryNode> = ArrayList()
        val stack = Stack<DeviceFileEntryNode>() // iterative DFS traversal
        stack.push(node)
        while (!stack.isEmpty()) {
          val currentNode = stack.pop()
          nodesToSynchronize.add(currentNode)
          for (child in currentNode.childEntryNodes) {
            if (child.entry.isDirectory || child.isSymbolicLinkToDirectory) {
              if (child.isLoaded) {
                stack.push(child)
              }
            }
          }
        }
        nodesToSynchronize
      }.toSet()

      scope.launch {
        trackAction(DeviceExplorerEvent.Action.SYNC)
        view.startTreeBusyIndicator()
        try {
          for (node in directoryNodes) {
            node.isLoaded = false
            try {
              loadNodeChildren(node)
            } catch (ignored: Exception) {
              // In case of error, proceed to load children of remaining nodes.
              // TODO: ignoring error to preserve behavior of FutureCallbackExecutor.executeFuturesInSequence.
            }
          }
        } finally {
          view.stopTreeBusyIndicator()
        }
      }
    }

    override fun deleteNodesInvoked(nodes: List<DeviceFileEntryNode>) {
      if (nodes.isEmpty()) {
        return
      }
      if (!checkLongRunningOperationAllowed()) {
        view.reportErrorRelatedToNode(getCommonParentNode(nodes), DEVICE_EXPLORER_BUSY_MESSAGE, RuntimeException())
        return
      }
      val fileEntries = nodes.map {it.entry}.toMutableList()
      val message = createDeleteConfirmationMessage(fileEntries)
      val returnValue = Messages.showOkCancelDialog(
        message,
        UIBundle.message("delete.dialog.title"),
        ApplicationBundle.message("button.delete"),
        CommonBundle.getCancelButtonText(),
        Messages.getQuestionIcon()
      )
      if (returnValue != Messages.OK) {
        return
      }
      fileEntries.sortBy { it.fullPath }

      scope.launch {
        val problems: MutableList<String> = LinkedList()
        for (fileEntry in fileEntries) {
          try {
            withTimeout(FILE_ENTRY_DELETION_TIMEOUT) {
              fileEntry.delete()
            }
          } catch (t: Throwable) {
            LOGGER.info("Error deleting file \"${fileEntry.fullPath}\"", t)
            val problemMessage = emptyToNull(ExceptionUtil.getRootCause(t).message) ?: "Error deleting file"
            problems.add("${fileEntry.fullPath}: $problemMessage")
          }
        }
        if (!problems.isEmpty()) {
          reportDeletionProblem(problems)
        } else {
          trackAction(DeviceExplorerEvent.Action.DELETE)
        }

        // Refresh the parent node(s) to remove the deleted files
        val parentsToRefresh = nodes.mapNotNull { DeviceFileEntryNode.fromNode(it.parent) }.toSet()
        for (parent in parentsToRefresh) {
          parent.isLoaded = false
          try {
            loadNodeChildren(parent)
          } catch (ignored: Exception) {
            // In case of error, proceed to load children of remaining nodes.
          }
        }
      }
    }

    private fun reportDeletionProblem(problems: List<String>) {
      var problems = problems
      if (problems.size == 1) {
        Messages.showMessageDialog(
          """
  Could not erase file or folder:
  ${problems[0]}
  """.trimIndent(),
          UIBundle.message("error.dialog.title"), Messages.getErrorIcon()
        )
        return
      }
      var more = false
      if (problems.size > 10) {
        problems = problems.subList(0, 10)
        more = true
      }
      Messages.showMessageDialog(
        """Could not erase files or folders:
  ${StringUtil.join(problems, ",\n  ")}${if (more) "\n  ..." else ""}""",
        UIBundle.message("error.dialog.title"), Messages.getErrorIcon()
      )
    }

    private fun createDeleteConfirmationMessage(filesToDelete: List<DeviceFileEntry>): String {
      return if (filesToDelete.size == 1) {
        if (filesToDelete[0].isDirectory) {
          UIBundle.message("are.you.sure.you.want.to.delete.selected.folder.confirmation.message", filesToDelete[0].name)
        } else {
          UIBundle.message("are.you.sure.you.want.to.delete.selected.file.confirmation.message", filesToDelete[0].name)
        }
      } else {
        val hasFiles = filesToDelete.any {!it.isDirectory}
        val hasFolders = filesToDelete.any {it.isDirectory}
        if (hasFiles && hasFolders) {
          UIBundle
            .message("are.you.sure.you.want.to.delete.selected.files.and.directories.confirmation.message", filesToDelete.size)
        } else if (hasFolders) {
          UIBundle.message("are.you.sure.you.want.to.delete.selected.folders.confirmation.message", filesToDelete.size)
        } else {
          UIBundle.message("are.you.sure.you.want.to.delete.selected.files.and.files.confirmation.message", filesToDelete.size)
        }
      }
    }

    override fun newDirectoryInvoked(parentTreeNode: DeviceFileEntryNode) {
      scope.launch {
        newFileOrDirectory(parentTreeNode,
                           "NewFolder",
                           UIBundle.message("new.folder.dialog.title"),
                           UIBundle.message("create.new.folder.enter.new.folder.name.prompt.text"),
                           UIBundle.message("create.new.folder.folder.name.cannot.be.empty.error.message"),
                           { UIBundle.message("create.new.folder.could.not.create.folder.error.message", it) },
                           { parentTreeNode.entry.createNewDirectory(it) })
        trackAction(DeviceExplorerEvent.Action.NEW_DIRECTORY)
      }
    }

    private suspend fun newFileOrDirectory(
      parentTreeNode: DeviceFileEntryNode,
      initialName: String,
      title: String,
      prompt: String,
      emptyErrorMessage: String,
      errorMessage: (String) -> String,
      createFunction: suspend (String) -> Unit
    ) {
      var initialName = initialName
      getTreeModel() ?: return
      while (true) {
        val newFileName = Messages.showInputDialog(prompt, title, Messages.getQuestionIcon(), initialName,
                                                   object : InputValidatorEx {
          override fun getErrorText(inputString: String): String? {
            if (StringUtil.isEmpty(inputString.trim { it <= ' ' })) {
              return emptyErrorMessage
            } else if (inputString.contains(AdbPathUtil.FILE_SEPARATOR)) {
              return "Path cannot contain \"/\" characters"
            }
            return null
          }

          override fun checkInput(inputString: String): Boolean {
            return canClose(inputString)
          }

          override fun canClose(inputString: String): Boolean {
            return !StringUtil.isEmpty(inputString.trim { it <= ' ' })
          }
        }) ?: return

        try {
          withTimeout(FILE_ENTRY_CREATION_TIMEOUT) {
            createFunction(newFileName)
          }

          // Refresh the parent node to show the newly created file
          parentTreeNode.isLoaded = false
          loadNodeChildren(parentTreeNode)
          view.expandNode(parentTreeNode)
          return
        } catch (e: Exception) {
          showErrorMessage(errorMessage(newFileName), e)
          initialName = newFileName
        }
      }
    }

    // Execution exceptions contain the actual cause of the error
    private fun actualCause(error: Throwable) =
      if (error is ExecutionException) {
        error.cause ?: error
      } else error

    private fun showErrorMessage(message: String, error: Throwable) {
      // Add error message from exception if we have one
      val message = actualCause(error).message?.let {
        message + """
                :
                $it
                """.trimIndent()
      } ?: message

      // Show error dialog
      Messages.showMessageDialog(message, UIBundle.message("error.dialog.title"), Messages.getErrorIcon())
    }

    override fun uploadFilesInvoked(treeNode: DeviceFileEntryNode) {
      if (!checkLongRunningOperationAllowed()) {
        view.reportErrorRelatedToNode(treeNode, DEVICE_EXPLORER_BUSY_MESSAGE, RuntimeException())
        return
      }
      val descriptor = FileChooserDescriptorFactory.createAllButJarContentsDescriptor()
      val filesRef = AtomicReference<List<VirtualFile>>()
      FileChooser.chooseFiles(descriptor, project, null) { filesRef.set(it) }
      val files = filesRef.get()
      if (files == null || files.isEmpty()) {
        return
      }
      scope.launch { uploadVirtualFilesInvoked(treeNode, files, DeviceExplorerEvent.Action.UPLOAD) }
    }

    override fun uploadFilesInvoked(treeNode: DeviceFileEntryNode, files: List<Path>) {
      if (!checkLongRunningOperationAllowed()) {
        view.reportErrorRelatedToNode(treeNode, DEVICE_EXPLORER_BUSY_MESSAGE, RuntimeException())
        return
      }
      val vfiles = files.mapNotNull { VfsUtil.findFile(it, true) }
      scope.launch { uploadVirtualFilesInvoked(treeNode, vfiles, DeviceExplorerEvent.Action.DROP) }
    }

    private suspend fun uploadVirtualFilesInvoked(
      treeNode: DeviceFileEntryNode,
      files: List<VirtualFile>,
      action: DeviceExplorerEvent.Action
    ) {
      if (!checkLongRunningOperationAllowed()) {
        view.reportErrorRelatedToNode(treeNode, DEVICE_EXPLORER_BUSY_MESSAGE, RuntimeException())
        return
      }
      try {
        val transferSummary = wrapFileTransfer(
          { tracker -> addUploadOperationWork(tracker, files.map { Paths.get(it.path) }) },
          { tracker -> uploadVirtualFiles(treeNode, files, tracker) },
          true)
        transferSummary.action = action
        reportUploadFilesSummary(treeNode, transferSummary)
      } catch (t: Throwable) {
        view.reportErrorRelatedToNode(treeNode, "Error uploading files(s) to device", t)
      }
    }

    private fun reportUploadFilesSummary(treeNode: DeviceFileEntryNode, summary: FileTransferSummary) {
      reportFileTransferSummary(treeNode, summary, "uploaded", "uploading")
    }

    private suspend fun uploadVirtualFiles(
      parentNode: DeviceFileEntryNode,
      files: List<VirtualFile>,
      tracker: FileTransferOperationTracker
    ) {
      // Upload each file
      files.forEach { uploadVirtualFile(parentNode, it, tracker) }

      // Refresh children nodes
      parentNode.isLoaded = false
      loadNodeChildren(parentNode)
    }

    private suspend fun uploadVirtualFile(
      treeNode: DeviceFileEntryNode,
      file: VirtualFile,
      tracker: FileTransferOperationTracker
    ) {
      if (file.isDirectory) {
        uploadDirectory(treeNode, file, tracker)
      } else {
        uploadFile(treeNode, file, tracker)
      }
    }

    private suspend fun uploadDirectory(
      parentNode: DeviceFileEntryNode,
      file: VirtualFile,
      tracker: FileTransferOperationTracker
    ) {
      if (tracker.isCancelled) {
        cancelAndThrow()
      }
      tracker.processDirectory()
      tracker.summary.addDirectoryCount(1)

      // Create directory in destination device
      val parentEntry = parentNode.entry
      val directoryName = file.name

      // Store the exception here on failure, or Unit if there is none
      val createDirectoryResult: Any =
        try {
          parentEntry.createNewDirectory(directoryName)
        } catch (t: Throwable) { t }

      // Refresh node entries
      parentNode.isLoaded = false
      try {
        loadNodeChildren(parentNode)
      } catch (t: Throwable) {
        tracker.addProblem(t)
        return
      }

      // Find node for newly created directory
      val childNode = parentNode.findChildEntry(directoryName)
      if (childNode == null) {
        // Note: This would happen if we didn't filter hidden files in the code below
        //       or if we failed to create the child directory
        tracker.addProblem(when (createDirectoryResult) {
          is Throwable -> createDirectoryResult
          else -> Exception("Error creating directory $directoryName")
        })
        return
      }

      // Upload all files into destination device
      // Note: We ignore hidden files ("." prefix) for now, as the listing service
      //       currently does not list hidden files/directories.
      val childFiles = file.children.filter { !it.name.startsWith(".") }
      uploadVirtualFiles(childNode, childFiles, tracker)
    }

    private suspend fun uploadFile(
      parentNode: DeviceFileEntryNode,
      file: VirtualFile,
      tracker: FileTransferOperationTracker
    ) {
      if (tracker.isCancelled) {
        cancelAndThrow()
      }

      tracker.processFile()
      tracker.setUploadFileText(file, 0, 0)
      val stopwatch = Stopwatch.createStarted()
      val parentEntry = parentNode.entry
      val localPath = Paths.get(file.path)
      val uploadState = UploadFileState()
      try {
        parentEntry.uploadFile(localPath, object : FileTransferProgress {
          private var previousBytes: Long = 0
          override fun progress(currentBytes: Long, totalBytes: Long) {
            // Update progress UI
            tracker.processFileBytes(currentBytes - previousBytes)
            tracker.setUploadFileText(file, currentBytes, totalBytes)
            previousBytes = currentBytes
            if (tracker.isInForeground) {
              // Update Tree UI
              uploadState.byteCount = totalBytes

              // First check if child node already exists
              if (uploadState.childNode == null) {
                val fileName = localPath.fileName.toString()
                uploadState.childNode = parentNode.findChildEntry(fileName)
                if (uploadState.childNode != null) {
                  startNodeUpload(uploadState.childNode!!)
                }
              }

              // If the child node entry is present, simply update its upload status
              uploadState.childNode?.let { childNode ->
                childNode.setTransferProgress(currentBytes, totalBytes)

                // Signal upload is done if we got all the bytes
                if (currentBytes == totalBytes && tracker.isInForeground) {
                  stopNodeUpload(childNode)
                }
                return
              }

              // If we already tried to load the children, reset so we try again
              if (uploadState.loadChildrenJob != null && uploadState.loadChildrenJob!!.isCompleted) {
                uploadState.loadChildrenJob = null
              }

              // Start loading children
              if (currentBytes > 0) {
                if (uploadState.loadChildrenJob == null) {
                  parentNode.isLoaded = false
                  uploadState.loadChildrenJob = scope.launch { loadNodeChildren(parentNode) }
                }
              }
            }
          }

          override fun isCancelled(): Boolean {
            return tracker.isCancelled
          }
        })

        tracker.summary.addFileCount(1)
        tracker.summary.addByteCount(uploadState.byteCount)

      } catch (t: Throwable) {
        tracker.addProblem(t)
      }

      LOGGER.trace("Uploaded file in $stopwatch: ${AdbPathUtil.resolve(parentNode.entry.fullPath, file.name)}")
    }

    private inner class UploadFileState(
      var loadChildrenJob: Job? = null,
      var childNode: DeviceFileEntryNode? = null,
      var byteCount: Long = 0
    )

    private fun reportFileTransferSummary(
      node: DeviceFileEntryNode,
      summary: FileTransferSummary,
      pastParticiple: String,
      presentParticiple: String
    ) {
      val fileString = StringUtil.pluralize("file", summary.fileCount)
      val directoryString = StringUtil.pluralize("directory", summary.directoryCount)
      val byteCountString = StringUtil.pluralize("byte", Ints.saturatedCast(summary.byteCount))
      log(
        AndroidStudioEvent.newBuilder()
          .setKind(AndroidStudioEvent.EventKind.DEVICE_EXPLORER)
          .setDeviceExplorerEvent(
            DeviceExplorerEvent.newBuilder()
              .setAction(summary.action)
              .setTransferFileCount(summary.fileCount)
              .setTransferTotalSize(summary.byteCount.toInt())
              .setTransferTimeMs(summary.durationMillis.toInt())
          )
      )

      // Report success if no errors
      if (summary.problems.isEmpty()) {
        val successMessage = if (summary.directoryCount > 0) {
          String.format(
            Locale.getDefault(),
            "Successfully %s %,d %s and %,d %s for a total size of %,d %s in %s.",
            pastParticiple,
            summary.fileCount,
            fileString,
            summary.directoryCount,
            directoryString,
            summary.byteCount,
            byteCountString,
            StringUtil.formatDuration(summary.durationMillis)
          )
        } else {
          String.format(
            Locale.getDefault(),
            "Successfully %s %,d %s for a total of size of %,d %s in %s.",
            pastParticiple,
            summary.fileCount,
            fileString,
            summary.byteCount,
            byteCountString,
            StringUtil.formatDuration(summary.durationMillis)
          )
        }
        view.reportMessageRelatedToNode(node, successMessage)
        return
      }

      // Report error if there were any
      var problems = summary.problems.mapNotNull { ExceptionUtil.getRootCause(it).message }
      var more = false
      if (problems.size > 10) {
        problems = problems.subList(0, 10)
        more = true
      }
      var message = String.format("There were errors %s files and/or directories", presentParticiple)
      if (summary.fileCount > 0) {
        message += String.format(
          Locale.getDefault(),
          ", although %,d %s %s successfully %s in %s for a total of size of %,d %s",
          summary.fileCount,
          fileString,
          if (summary.fileCount <= 1) "was" else "were",
          pastParticiple,
          StringUtil.formatDuration(summary.durationMillis),
          summary.byteCount,
          byteCountString
        )
      }
      view.reportErrorRelatedToNode(
        node,
        message,
        Exception(
          """
  ${StringUtil.join(problems, ",\n  ")}${if (more) "\n  ..." else ""}"""
        )
      )
    }

    private fun getCommonParentNode(treeNodes: List<DeviceFileEntryNode>): DeviceFileEntryNode {
      val commonPath = TreeUtil.getCommonPath(treeNodes)
      LOGGER.assertTrue(commonPath != null)
      val result = DeviceFileEntryNode.fromNode(commonPath!!.lastPathComponent)
      LOGGER.assertTrue(result != null)
      return result!!
    }

    private suspend fun chooseSaveAsFilePath(treeNode: DeviceFileEntryNode): Path? {
      val entry = treeNode.entry
      val localPath = fileManager.getDefaultLocalPathForEntry(entry)
      val baseDir = withContext(diskIoThread) {
        FileUtils.mkdirs(localPath.parent.toFile())
        VfsUtil.findFileByIoFile(localPath.parent.toFile(), true)
            ?: throw Exception("Unable to locate file \"${localPath.parent}\"")
      }
      val fileWrapper = withContext(uiThread) {
        val descriptor = FileSaverDescriptor("Save As", "")
        val saveFileDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        saveFileDialog.save(baseDir, localPath.fileName.toString()) ?: cancelAndThrow()
      }
      return fileWrapper.file.toPath()
    }

    private suspend fun chooseSaveAsDirectoryPath(treeNode: DeviceFileEntryNode): Path? {
      val entry = treeNode.entry
      val localPath = fileManager.getDefaultLocalPathForEntry(entry)
      val localDir = withContext(diskIoThread) {
        FileUtils.mkdirs(localPath.toFile())
        VfsUtil.findFileByIoFile(localPath.toFile(), true)
        ?: throw Exception("Unable to locate directory \"${localPath.parent}\"")
      }
      val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
      val resultPath = AtomicReference<Path?>(null)
      withContext(uiThread) {
        FileChooser.chooseFiles(descriptor, project, localDir) { files: List<VirtualFile> ->
          if (files.size == 1) {
            val path = Paths.get(files[0].path)
            resultPath.set(path)
          }
        }
      }
      return resultPath.get()
    }

    private suspend fun downloadFileEntry(
      treeNode: DeviceFileEntryNode,
      localPath: Path,
      tracker: FileTransferOperationTracker
    ): Long {
      if (tracker.isCancelled) {
        cancelAndThrow()
      }
      tracker.processFile()
      val entry = treeNode.entry
      val sizeRef = CompletableDeferred<Long>()
      val stopwatch = Stopwatch.createStarted()
      fileManager.downloadFileEntry(entry, localPath, object : DownloadProgress {
        private var previousBytes: Long = 0
        override fun onStarting(entryFullPath: String) {
          val currentNode = getTreeNodeFromEntry(treeNode, entryFullPath)!!
          previousBytes = 0
          startNodeDownload(currentNode)
        }

        override fun onProgress(entryFullPath: String, currentBytes: Long, totalBytes: Long) {
          val currentNode = getTreeNodeFromEntry(treeNode, entryFullPath)!!
          tracker.processFileBytes(currentBytes - previousBytes)
          previousBytes = currentBytes
          tracker.setDownloadFileText(entryFullPath, currentBytes, totalBytes)
          if (tracker.isInForeground) {
            currentNode.setTransferProgress(currentBytes, totalBytes)
          }
        }

        override fun onCompleted(entryFullPath: String) {
          sizeRef.complete(previousBytes)
          if (tracker.isInForeground) {
            val currentNode = getTreeNodeFromEntry(treeNode, entryFullPath)!!
            stopNodeDownload(currentNode)
          }
        }

        override fun isCancelled(): Boolean {
          return tracker.isCancelled
        }
      })
      LOGGER.trace("Downloaded file in $stopwatch: ${entry.fullPath}")
      // downloadFileEntry may complete before onCompleted is called
      return sizeRef.await()
    }

    override fun treeNodeExpanding(node: DeviceFileEntryNode) {
      scope.launch {
        loadNodeChildren(node)
      }
    }

    private suspend fun loadNodeChildren(node: DeviceFileEntryNode) {

      // Track a specific set of directories to analyze user behaviour
      if (node.entry.fullPath.matches(Regex("^/data/data/[^/]+$"))) {
        trackAction(DeviceExplorerEvent.Action.EXPAND_APP_DATA)
      }

      // Ensure node is expanded only once
      if (node.isLoaded) {
        return
      }
      node.isLoaded = true

      // Leaf nodes are not expandable
      if (node.isLeaf) {
        return
      }
      val treeModel = getTreeModel()
      val treeSelectionModel = getTreeSelectionModel()
      if (treeModel == null || treeSelectionModel == null) {
        return
      }
      val fileSystem = model.activeDevice
      if (fileSystem != node.entry.fileSystem) {
        return
      }
      val showLoadingNode = Runnable { showLoadingNode(treeModel, node) }
      loadingNodesAlarms.addRequest(showLoadingNode, showLoadingNodeDelayMillis)
      startLoadChildren(node)
      try {
        val entries = node.entry.entries()
        if (treeModel != getTreeModel()) {
          // We switched to another device, ignore this callback
          return
        }

        // Save selection
        val oldSelections = treeSelectionModel.selectionPaths

        // Collect existing entries that have the "isLinkToDirectory" property set
        val isLinkToDirectory = node.childEntryNodes
          .filter { it.isSymbolicLinkToDirectory }
          .map { it.entry.name }
          .toSet()

        // Sort new entries according to presentation sort order
        val comparator = NodeSorting.CustomComparator<DeviceFileEntry>(
          nameProvider = { it.name },
          isDirectory = {
            it.isDirectory || isLinkToDirectory.contains(it.name)
          })

        val addedNodes = updateChildrenNodes(treeModel, node, entries.sortedWith(comparator))
        loadingNodesAlarms.cancelRequest(showLoadingNode)

        // Restore selection
        restoreTreeSelection(treeSelectionModel, oldSelections, node)
        val symlinkNodes = addedNodes.filter { it.entry.isSymbolicLink }
        querySymbolicLinks(symlinkNodes, treeModel)
      } catch (t: Throwable) {
        val message = emptyToNull(ExceptionUtil.getRootCause(t).message)
                      ?: "Unable to list entries of directory ${getUserFacingNodeName(node)}"
        node.removeAllChildren()
        node.add(ErrorNode(message))
        node.allowsChildren = true
        treeModel.nodeStructureChanged(node)
        throw t
      } finally {
        stopLoadChildren(node)
        loadingNodesAlarms.cancelRequest(showLoadingNode)
      }
    }

    private fun updateChildrenNodes(
      treeModel: DefaultTreeModel,
      parentNode: DeviceFileEntryNode,
      newEntries: List<DeviceFileEntry>
    ): List<DeviceFileEntryNode> {
      val updateChildrenOps: UpdateChildrenOps<DeviceFileEntryNode, DeviceFileEntry> =
        object : UpdateChildrenOps<DeviceFileEntryNode, DeviceFileEntry> {
          override fun getChildNode(parentNode: DeviceFileEntryNode, index: Int): DeviceFileEntryNode? {
            // Some nodes (e.g. "error" or "loading" nodes) are not of the same type,
            // we return null in those cases to that the update algorithm will remove them from
            // the parent node.
            return DeviceFileEntryNode.fromNode(parentNode.getChildAt(index))
          }

          override fun mapEntry(entry: DeviceFileEntry): DeviceFileEntryNode {
            return DeviceFileEntryNode(entry)
          }

          override fun compareNodeWithEntry(
            node: DeviceFileEntryNode,
            entry: DeviceFileEntry
          ): Int {
            return node.entry.name.compareTo(entry.name)
          }

          override fun updateNode(
            node: DeviceFileEntryNode,
            entry: DeviceFileEntry
          ) {
            node.entry = entry
          }
        }
      val addedNodes = TreeUtil.updateChildrenNodes(treeModel, parentNode, newEntries, updateChildrenOps)
      parentNode.allowsChildren = parentNode.childCount > 0
      return addedNodes
    }

    private fun restoreTreeSelection(
      treeSelectionModel: DefaultTreeSelectionModel,
      oldSelections: Array<TreePath>,
      parentNode: DefaultMutableTreeNode
    ) {
      val newSelections: MutableSet<TreePath> = HashSet()
      val parentPath = TreePath(parentNode.path)
      for (oldSelection in oldSelections) {
        restorePathSelection(treeSelectionModel, parentPath, oldSelection, newSelections)
      }
      val newSelectionArray = ArrayUtil.toObjectArray(ArrayList(newSelections), TreePath::class.java)
      treeSelectionModel.addSelectionPaths(newSelectionArray)
    }

    private fun restorePathSelection(
      treeSelectionModel: DefaultTreeSelectionModel,
      parentPath: TreePath,
      oldPath: TreePath,
      selections: MutableSet<TreePath>
    ) {
      if (treeSelectionModel.isPathSelected(oldPath)) {
        return
      }
      if (parentPath == oldPath) {
        return
      }
      if (!parentPath.isDescendant(oldPath)) {
        return
      }
      val node = parentPath.lastPathComponent as TreeNode
      val existingChild = TreeUtil.getChildren(node)
        .filter { x: TreeNode? -> x == oldPath.lastPathComponent }
        .findFirst()
        .orElse(null)
      if (existingChild == null) {
        selections.add(parentPath)
      }
    }

    /**
     * Asynchronously update the tree node UI of the `symlinkNodes` entries if they target
     * a directory, i.e. update tree nodes with a "Folder" and "Expandable arrow" icon.
     */
    private suspend fun querySymbolicLinks(symlinkNodes: List<DeviceFileEntryNode>, treeModel: DefaultTreeModel) {
      // Note: We process (asynchronously) one entry at a time, instead of all of them in parallel,
      //       to avoid flooding the device with too many requests, which would eventually lead
      //       to the device to reject additional requests.
      for (treeNode in symlinkNodes) {
        val isDirectory = try {
          treeNode.entry.isSymbolicLinkToDirectory()
        } catch (t: Throwable) {
          // Log error, but keep going as we may have more symlinkNodes to examine
          LOGGER.info("Error determining if file entry \"${treeNode.entry.name}\" is a link to a directory", t)
          // In case of error, we assume the entry does not target a directory.
          false
        }

        // Stop all processing if tree model has changed, i.e. UI has been switched to another device
        if (model.treeModel != treeModel) {
          return
        }

        // Update tree node appearance
        if (treeNode.isSymbolicLinkToDirectory != isDirectory) {
          val parent = treeNode.parent as MutableTreeNode

          // Remove element from tree at current position (assume tree is sorted)
          val previousIndex = TreeUtil.binarySearch(parent, treeNode, NodeSorting.TreeNodeComparator)
          if (previousIndex >= 0) {
            treeModel.removeNodeFromParent(treeNode)
          }

          // Update node state (is-link-to-directory)
          treeNode.isSymbolicLinkToDirectory = isDirectory

          // Insert node in its new position
          val newIndex = TreeUtil.binarySearch(parent, treeNode, NodeSorting.TreeNodeComparator)
          if (newIndex < 0) {
            treeModel.insertNodeInto(treeNode, parent, -(newIndex + 1))
          }
        }
      }
    }

    private fun getUserFacingNodeName(node: DeviceFileEntryNode): String {
      return if (StringUtil.isEmpty(node.entry.name)) "[root]" else "\"${node.entry.name}\""
    }
  }

  private fun trackAction(action: DeviceExplorerEvent.Action) {
    log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.DEVICE_EXPLORER)
        .setDeviceExplorerEvent(
          DeviceExplorerEvent.newBuilder()
            .setAction(action)
        )
    )
  }

  private fun showLoadingNode(treeModel: DefaultTreeModel, node: DeviceFileEntryNode) {
    node.allowsChildren = true
    val newChild = MyLoadingNode(node.entry)
    treeModel.insertNodeInto(newChild, node, node.childCount)
  }

  private fun repaintTransferringNodes() {
    for (node in transferringNodes) {
      node.incTransferringTick()
      getTreeModel()?.nodeChanged(node)
    }
    transferringNodesAlarms.addRequest(::repaintTransferringNodes, transferringNodeRepaintMillis)
  }

  private fun repaintLoadingChildren() {
    for (child in loadingChildren) {
      if (child.childCount == 0) continue
      val node = child.firstChild
      if (node is MyLoadingNode) {
        node.incTick()
        getTreeModel()?.nodeChanged(node)
      }
    }
    loadingChildrenAlarms.addRequest(::repaintLoadingChildren, transferringNodeRepaintMillis)
  }

  @VisibleForTesting object NodeSorting {
    /**
     * Compare [DeviceFileEntryNode] by directory first, by name second.
     */
    object EntryNodeComparator : Comparator<DeviceFileEntryNode?> by CustomComparator(
      nameProvider = { it.entry.name },
      isDirectory = { it.entry.isDirectory || it.isSymbolicLinkToDirectory }
    )

    /**
     * Compare [TreeNode] as [DeviceFileEntryNode]. Any other type of tree node
     * is considered "less than".
     */
    object TreeNodeComparator : Comparator<TreeNode> {
      override fun compare(o1: TreeNode, o2: TreeNode): Int =
        when {
          o1 is DeviceFileEntryNode && o2 is DeviceFileEntryNode -> EntryNodeComparator.compare(o1, o2)
          o1 is DeviceFileEntryNode -> 1
          o2 is DeviceFileEntryNode -> -1
          else -> 0
        }
    }

    /** Compares strings case-insensitively. */
    object CompareCaseInsensitive : Comparator<String> {
      override fun compare(o1: String, o2: String): Int = StringUtil.compare(o1, o2, true)
    }

    /**
     * Compares nulls first, then directories first, then compares files by their name (case insensitive).
     * Uses the supplied functions to determine what is a directory and what the file names are.
     */
    class CustomComparator<V>(
      nameProvider: (V) -> String,
      isDirectory: (V) -> Boolean
    ) : Comparator<V?> by nullsFirst(compareByDescending<V> {isDirectory(it)}.thenComparing(nameProvider, CompareCaseInsensitive))

  }

  interface FileOpener {
    @UiThread
    suspend fun openFile(localPath: Path)
  }

  companion object {
    private val LOGGER = logger<DeviceExplorerController>()
    private val KEY = Key.create<DeviceExplorerController>(
      DeviceExplorerController::class.java.name
    )
    private const val DEVICE_EXPLORER_BUSY_MESSAGE = "Device Explorer is busy, please retry later or cancel current operation"
    private val FILE_ENTRY_CREATION_TIMEOUT = Duration.ofMillis(10000)
    private val FILE_ENTRY_DELETION_TIMEOUT = Duration.ofMillis(10000)

    @JvmStatic
    fun getProjectController(project: Project?): DeviceExplorerController? {
      return project?.getUserData(KEY)
    }
  }
}
