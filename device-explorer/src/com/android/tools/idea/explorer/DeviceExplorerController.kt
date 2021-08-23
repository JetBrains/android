/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.explorer

import com.android.annotations.concurrency.UiThread
import com.android.tools.analytics.UsageTracker.log
import com.android.tools.idea.adb.AdbFileProvider.Companion.fromProject
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.concurrency.catching
import com.android.tools.idea.concurrency.ignoreResult
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.concurrency.transformAsync
import com.android.tools.idea.explorer.adbimpl.AdbPathUtil
import com.android.tools.idea.explorer.fs.DeviceFileEntry
import com.android.tools.idea.explorer.fs.DeviceFileSystem
import com.android.tools.idea.explorer.fs.DeviceFileSystemService
import com.android.tools.idea.explorer.fs.DeviceFileSystemServiceListener
import com.android.tools.idea.explorer.fs.DeviceState
import com.android.tools.idea.explorer.fs.DownloadProgress
import com.android.tools.idea.explorer.fs.FileTransferProgress
import com.android.tools.idea.explorer.ui.TreeUtil
import com.android.tools.idea.explorer.ui.TreeUtil.UpdateChildrenOps
import com.android.utils.FileUtils
import com.google.common.primitives.Ints
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DeviceExplorerEvent
import com.intellij.CommonBundle
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
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.awt.datatransfer.StringSelection
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.LinkedList
import java.util.Locale
import java.util.Stack
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.DefaultTreeSelectionModel
import javax.swing.tree.MutableTreeNode
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

/**
 * Implementation of the Device Explorer application logic
 */
class DeviceExplorerController(
  private val myProject: Project,
  private val myModel: DeviceExplorerModel,
  private val myView: DeviceExplorerView,
  private val myService: DeviceFileSystemService<out DeviceFileSystem>,
  private val fileManager: DeviceExplorerFileManager,
  private val myFileOpener: FileOpener,
  edtExecutor: Executor,
  taskExecutor: Executor
) {
  private var myShowLoadingNodeDelayMillis = 200
  private var myTransferringNodeRepaintMillis = 100
  private val myEdtExecutor = FutureCallbackExecutor.wrap(edtExecutor)
  private val myWorkEstimator = FileTransferWorkEstimator(myEdtExecutor, taskExecutor)
  private val myTransferringNodes: MutableSet<DeviceFileEntryNode> = HashSet()
  private val myLoadingChildren: MutableSet<DeviceFileEntryNode> = HashSet()
  private val myLoadingNodesAlarms = Alarm()
  private val myTransferringNodesAlarms = Alarm()
  private val myLoadingChildrenAlarms = Alarm()
  private val mySetupFuture = SettableFuture.create<Unit>()
  private var myLongRunningOperationTracker: LongRunningOperationTracker? = null

  init {
    myService.addListener(ServiceListener())
    myView.addListener(ViewListener())
    myProject.putUserData(KEY, this)
  }

  private fun getTreeModel(): DefaultTreeModel? {
    return myModel.treeModel
  }

  private fun getTreeSelectionModel(): DefaultTreeSelectionModel? {
    return myModel.treeSelectionModel
  }

  fun setup() {
    myView.setup()
    myView.startRefresh("Initializing ADB")
    val future = myService.start { getAdbFile() }
    myEdtExecutor.addListener(future) { myView.stopRefresh() }
    myEdtExecutor.addCallback(future, object : FutureCallback<Unit> {
      override fun onSuccess(result: Unit?) {
        mySetupFuture.set(Unit)
        refreshDeviceList(null)
      }

      override fun onFailure(t: Throwable) {
        mySetupFuture.setException(t)
        myView.reportErrorRelatedToService(myService, "Error initializing ADB", t)
      }
    })
  }

  fun restartService() {
    myView.startRefresh("Restarting ADB")
    val future = myService.restart { getAdbFile() }
    myEdtExecutor.addListener(future) { myView.stopRefresh() }
    myEdtExecutor.addCallback(future, object : FutureCallback<Unit> {
      override fun onSuccess(result: Unit?) {
        // A successful restart invokes {@link ServiceListener#serviceRestarted()} which
        // eventually refreshes the list of devices
      }

      override fun onFailure(t: Throwable) {
        myView.reportErrorRelatedToService(myService, "Error restarting ADB", t)
      }
    })
  }

  private fun getAdbFile(): File? {
    val provider = fromProject(myProject)
    return provider?.adbFile
  }

  fun reportErrorFindingDevice(message: String) {
    myView.reportErrorGeneric(message, IllegalStateException())
  }

  fun selectActiveDevice(serialNumber: String) {
    if (mySetupFuture.isDone) {
      selectTheDevice(serialNumber)
    } else {
      myEdtExecutor.transform(mySetupFuture) { selectTheDevice(serialNumber) }
    }
  }

  private fun selectTheDevice(serialNumber: String) {
    assert(mySetupFuture.isDone)
    for (device in myModel.devices) {
      if (serialNumber == device.deviceSerialNumber) {
        setActiveDevice(device)
        return
      }
    }
    refreshDeviceList(serialNumber)
  }

  private fun refreshDeviceList(serialNumberToSelect: String?) {
    cancelOrMoveToBackgroundPendingOperations()
    myView.startRefresh("Refreshing list of devices")
    val futureDevices = myService.devices
    myEdtExecutor.addListener(futureDevices) { myView.stopRefresh() }
    myEdtExecutor.addCallback(futureDevices, object : FutureCallback<List<DeviceFileSystem>> {
      override fun onSuccess(result: List<DeviceFileSystem>?) {
        checkNotNull(result)
        myModel.removeAllDevices()
        result.forEach { myModel.addDevice(it) }
        if (result.isEmpty()) {
          myView.showNoDeviceScreen()
        } else if (serialNumberToSelect != null) {
          when (val device = myModel.devices.find {it.deviceSerialNumber == serialNumberToSelect}) {
            null -> reportErrorFindingDevice("Unable to find device with serial number $serialNumberToSelect. Please retry.")
            else -> setActiveDevice(device)
          }
        }
      }

      override fun onFailure(t: Throwable) {
        myModel.removeAllDevices()
        myView.reportErrorRelatedToService(myService, "Error refreshing list of devices", t)
      }
    })
  }

  private fun setNoActiveDevice() {
    cancelOrMoveToBackgroundPendingOperations()
    myModel.activeDevice = null
    myModel.setActiveDeviceTreeModel(null, null, null)
    myView.showNoDeviceScreen()
  }

  private fun setActiveDevice(device: DeviceFileSystem) {
    cancelOrMoveToBackgroundPendingOperations()
    myModel.activeDevice = device
    trackAction(DeviceExplorerEvent.Action.DEVICE_CHANGE)
    refreshActiveDevice(device)
  }

  private fun deviceStateUpdated(device: DeviceFileSystem) {
    if (device != myModel.activeDevice) {
      return
    }

    // Refresh the active device view only if the device state has changed,
    // for example from offline -> online.
    val newState = device.deviceState
    val lastKnownState = myModel.getActiveDeviceLastKnownState(device)
    if (newState == lastKnownState) {
      return
    }
    myModel.setActiveDeviceLastKnownState(device)
    refreshActiveDevice(device)
  }

  private fun refreshActiveDevice(device: DeviceFileSystem) {
    if (device != myModel.activeDevice) {
      return
    }
    if (device.deviceState != DeviceState.ONLINE) {
      val message = when (device.deviceState) {
        DeviceState.UNAUTHORIZED, DeviceState.OFFLINE ->
          "Device is pending authentication: please accept debugging session on the device"
        else ->
          String.format("Device is not online (%s)", device.deviceState)
      }
      myView.reportMessageRelatedToDevice(device, message)
      myModel.setActiveDeviceTreeModel(device, null, null)
      return
    }
    val futureRoot = device.rootDirectory
    myEdtExecutor.addCallback(futureRoot, object : FutureCallback<DeviceFileEntry> {
      override fun onSuccess(result: DeviceFileEntry?) {
        assert(result != null)
        val rootNode = DeviceFileEntryNode(result!!)
        val model = DefaultTreeModel(rootNode)
        myModel.setActiveDeviceTreeModel(device, model, DefaultTreeSelectionModel())
      }

      override fun onFailure(t: Throwable) {
        myModel.setActiveDeviceTreeModel(device, null, null)
        myView.reportErrorRelatedToDevice(device, "Unable to access root directory of device", t)
      }
    })
  }

  private fun cancelOrMoveToBackgroundPendingOperations() {
    myLoadingNodesAlarms.cancelAllRequests()
    myLoadingChildrenAlarms.cancelAllRequests()
    myTransferringNodesAlarms.cancelAllRequests()
    myLoadingChildren.clear()
    myTransferringNodes.clear()
    if (myLongRunningOperationTracker != null) {
      if (myLongRunningOperationTracker!!.isBackgroundable) {
        myLongRunningOperationTracker!!.moveToBackground()
        myLongRunningOperationTracker = null
      } else {
        myLongRunningOperationTracker!!.cancel()
      }
    }
  }

  private fun <T> executeFuturesInSequence(
    iterator: Iterator<T>,
    taskFactory: (T) -> ListenableFuture<Unit>
  ): ListenableFuture<Unit> {
    return myEdtExecutor.executeFuturesInSequence(iterator, taskFactory)
  }

  private fun startNodeDownload(node: DeviceFileEntryNode) {
    startNodeTransfer(node, true)
  }

  private fun startNodeUpload(node: DeviceFileEntryNode) {
    startNodeTransfer(node, false)
  }

  private fun startNodeTransfer(node: DeviceFileEntryNode, download: Boolean) {
    myView.startTreeBusyIndicator()
    if (download) {
      node.isDownloading = true
    } else {
      node.isUploading = true
    }
    if (myTransferringNodes.isEmpty()) {
      myTransferringNodesAlarms.addRequest(MyTransferringNodesRepaint(), myTransferringNodeRepaintMillis)
    }
    myTransferringNodes.add(node)
  }

  private fun stopNodeDownload(node: DeviceFileEntryNode) {
    stopNodeTransfer(node, true)
  }

  private fun stopNodeUpload(node: DeviceFileEntryNode) {
    stopNodeTransfer(node, false)
  }

  private fun stopNodeTransfer(node: DeviceFileEntryNode, download: Boolean) {
    myView.stopTreeBusyIndicator()
    if (download) {
      node.isDownloading = false
    } else {
      node.isUploading = false
    }
    getTreeModel()?.nodeChanged(node)
    myTransferringNodes.remove(node)
    if (myTransferringNodes.isEmpty()) {
      myTransferringNodesAlarms.cancelAllRequests()
    }
  }

  private fun startLoadChildren(node: DeviceFileEntryNode) {
    myView.startTreeBusyIndicator()
    if (myLoadingChildren.isEmpty()) {
      myLoadingChildrenAlarms.addRequest(MyLoadingChildrenRepaint(), myTransferringNodeRepaintMillis)
    }
    myLoadingChildren.add(node)
  }

  private fun stopLoadChildren(node: DeviceFileEntryNode) {
    myView.stopTreeBusyIndicator()
    myLoadingChildren.remove(node)
    if (myLoadingChildren.isEmpty()) {
      myLoadingChildrenAlarms.cancelAllRequests()
    }
  }

  private fun checkLongRunningOperationAllowed(): Boolean {
    return myLongRunningOperationTracker == null
  }

  @Throws(Exception::class)
  private fun registerLongRunningOperation(tracker: LongRunningOperationTracker) {
    if (!checkLongRunningOperationAllowed()) {
      throw Exception(DEVICE_EXPLORER_BUSY_MESSAGE)
    }
    myLongRunningOperationTracker = tracker
    Disposer.register(tracker) {
      assert(ApplicationManager.getApplication().isDispatchThread)
      if (myLongRunningOperationTracker === tracker) {
        myLongRunningOperationTracker = null
      }
    }
  }

  fun hasActiveDevice(): Boolean {
    return myModel.activeDevice != null
  }

  @TestOnly
  fun setShowLoadingNodeDelayMillis(showLoadingNodeDelayMillis: Int) {
    myShowLoadingNodeDelayMillis = showLoadingNodeDelayMillis
  }

  @TestOnly
  fun setTransferringNodeRepaintMillis(transferringNodeRepaintMillis: Int) {
    myTransferringNodeRepaintMillis = transferringNodeRepaintMillis
  }

  private inner class ServiceListener : DeviceFileSystemServiceListener {
    override fun serviceRestarted() {
      refreshDeviceList(null)
    }

    override fun deviceAdded(device: DeviceFileSystem) {
      myModel.addDevice(device)
    }

    override fun deviceRemoved(device: DeviceFileSystem) {
      myModel.removeDevice(device)
    }

    override fun deviceUpdated(device: DeviceFileSystem) {
      myModel.updateDevice(device)
      deviceStateUpdated(device)
    }
  }

  private inner class ViewListener : DeviceExplorerViewListener {
    override fun noDeviceSelected() {
      setNoActiveDevice()
    }

    override fun deviceSelected(device: DeviceFileSystem) {
      setActiveDevice(device)
    }

    override fun openNodesInEditorInvoked(treeNodes: List<DeviceFileEntryNode>) {
      if (treeNodes.isEmpty()) {
        return
      }
      if (!checkLongRunningOperationAllowed()) {
        myView.reportErrorRelatedToNode(getCommonParentNode(treeNodes), DEVICE_EXPLORER_BUSY_MESSAGE, RuntimeException())
        return
      }
      val device = myModel.activeDevice
      myEdtExecutor.executeFuturesInSequence(treeNodes.iterator()) { treeNode: DeviceFileEntryNode ->
        if (device != myModel.activeDevice || treeNode.entry.isDirectory) {
          Futures.immediateFuture(Unit)
        } else if (treeNode.isTransferring) {
          myView.reportErrorRelatedToNode(treeNode, "Entry is already downloading or uploading", RuntimeException())
          Futures.immediateFuture(Unit)
        } else {
          treeNode.entry.isSymbolicLinkToDirectory.transformAsync(myEdtExecutor) { isSymlinkToDir ->
            if (isSymlinkToDir) {
              Futures.immediateFuture(Unit)
            } else {
              downloadAndOpenFile(treeNode)
            }
          }
        }
      }
    }

    private fun downloadAndOpenFile(treeNode: DeviceFileEntryNode): ListenableFuture<Unit> =
      downloadFileEntryToDefaultLocation(treeNode).transformAsync(myEdtExecutor) { path ->
        DeviceExplorerFilesUtils.findFile(myProject, myEdtExecutor, path).transform(myEdtExecutor) {
          myFileOpener.openFile(it)
        }
      }.catching(myEdtExecutor, Throwable::class.java) { t: Throwable ->
        val message = String.format("Error opening contents of device file %s", getUserFacingNodeName(treeNode))
        myView.reportErrorRelatedToNode(treeNode, message, t)
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

    @UiThread
    private fun downloadFileEntryToDefaultLocation(treeNode: DeviceFileEntryNode): ListenableFuture<Path> {
      val localPath: Path = try {
        fileManager.getDefaultLocalPathForEntry(treeNode.entry)
      } catch (t: Throwable) {
        return Futures.immediateFailedFuture(t)
      }
      val futureSave = wrapFileTransfer(
        { tracker: FileTransferOperationTracker -> addDownloadOperationWork(tracker, treeNode) },
        { tracker: FileTransferOperationTracker -> downloadFileEntry(treeNode, localPath, tracker).ignoreResult() },
        true
      )
      return myEdtExecutor.transform(futureSave) { localPath }
    }

    override fun saveNodesAsInvoked(treeNodes: List<DeviceFileEntryNode>) {
      if (treeNodes.isEmpty()) {
        return
      }
      val commonParentNode = getCommonParentNode(treeNodes)
      if (!checkLongRunningOperationAllowed()) {
        myView.reportErrorRelatedToNode(commonParentNode, DEVICE_EXPLORER_BUSY_MESSAGE, RuntimeException())
        return
      }
      val futureSummary = if (treeNodes.size == 1) saveSingleNodeAs(treeNodes[0]) else saveMultiNodesAs(commonParentNode, treeNodes)
      myEdtExecutor.addCallback(futureSummary, object : FutureCallback<FileTransferSummary?> {
        override fun onSuccess(result: FileTransferSummary?) {
          assert(result != null)
          result!!.action = DeviceExplorerEvent.Action.SAVE_AS
          reportSaveNodesAsSummary(commonParentNode, result)
        }

        override fun onFailure(t: Throwable) {
          myView.reportErrorRelatedToNode(commonParentNode, "Error saving file(s) to local file system", t)
        }
      })
    }

    private fun reportSaveNodesAsSummary(node: DeviceFileEntryNode, summary: FileTransferSummary) {
      reportFileTransferSummary(node, summary, "downloaded", "downloading")
    }

    private fun saveSingleNodeAs(treeNode: DeviceFileEntryNode): ListenableFuture<FileTransferSummary> {
      // When saving a single entry, we should consider whether the entry is a symbolic link to
      // a directory, not just a plain directory.
      return if (treeNode.entry.isDirectory || treeNode.isSymbolicLinkToDirectory) {
        // If single directory, choose the local directory path to download to, then download
        val localDirectory: Path? = try {
          chooseSaveAsDirectoryPath(treeNode)
        } catch (e: Exception) {
          return Futures.immediateFailedFuture(e)
        }
        if (localDirectory == null) {
          // User cancelled operation
          Futures.immediateFailedFuture(CancellationException())
        } else wrapFileTransfer(
          { tracker: FileTransferOperationTracker -> addDownloadOperationWork(tracker, treeNode) },
          { tracker: FileTransferOperationTracker -> downloadSingleDirectory(treeNode, localDirectory, tracker) },
          false
        )
      } else {
        // If single file, choose the local file path to download to, then download
        val localFile: Path? = try {
          chooseSaveAsFilePath(treeNode)
        } catch (e: Exception) {
          return Futures.immediateFailedFuture(e)
        }
        if (localFile == null) {
          // User cancelled operation
          Futures.immediateFailedFuture(CancellationException())
        } else wrapFileTransfer(
          { tracker: FileTransferOperationTracker -> addDownloadOperationWork(tracker, treeNode) },
          { tracker: FileTransferOperationTracker -> downloadSingleFile(treeNode, localFile, tracker) },
          false
        )
      }
    }

    private fun saveMultiNodesAs(
      commonParentNode: DeviceFileEntryNode,
      treeNodes: List<DeviceFileEntryNode>
    ): ListenableFuture<FileTransferSummary> {
      assert(!treeNodes.isEmpty())

      // For downloading multiple entries, choose a local directory path to download to, then download
      // each entry relative to the chosen path
      val localDirectory: Path? = try {
        chooseSaveAsDirectoryPath(commonParentNode)
      } catch (e: Exception) {
        return Futures.immediateFailedFuture(e)
      }
      return if (localDirectory == null) {
        // User cancelled operation
        Futures.immediateFailedFuture(CancellationException())
      } else wrapFileTransfer(
        { tracker: FileTransferOperationTracker -> addDownloadOperationWork(tracker, treeNodes) },
        { tracker: FileTransferOperationTracker ->
          executeFuturesInSequence(treeNodes.iterator()) { treeNode: DeviceFileEntryNode ->
            val nodePath = localDirectory.resolve(treeNode.entry.name)
            downloadSingleNode(treeNode, nodePath, tracker)
          }
        },
        true
      )
    }

    /**
     * Wrap a file transfer operation (either "SaveAs" or "Upload") so that the operation
     * shows various UI elements related to progress (and resets them when the operation
     * is over).
     *
     * @param prepareTransfer An operation to run before the transfer, typically
     * to estimate the amount of work, used for tracking progress
     * later on
     * @param performTransfer The transfer operation itself
     * @return A [ListenableFuture]&lt;[FileTransferSummary]&gt; that completes
     * when the whole transfer operation finishes. In case of cancellation, the future
     * completes with a [CancellationException].
     */
    @UiThread
    private fun wrapFileTransfer(
      prepareTransfer: (FileTransferOperationTracker) -> ListenableFuture<Unit>,
      performTransfer: (FileTransferOperationTracker) -> ListenableFuture<Unit>,
      backgroundable: Boolean
    ): ListenableFuture<FileTransferSummary> {
      val tracker = FileTransferOperationTracker(myView, backgroundable)
      try {
        registerLongRunningOperation(tracker)
      } catch (e: Exception) {
        return Futures.immediateFailedFuture(e)
      }
      tracker.start()
      tracker.setCalculatingText(0, 0)
      tracker.setIndeterminate(true)
      Disposer.register(myProject, tracker)
      myView.startTreeBusyIndicator()
      val futureTransfer = prepareTransfer(tracker).transformAsync(myEdtExecutor) {
        tracker.setIndeterminate(false)
        performTransfer(tracker)
      }
      myEdtExecutor.addListener(futureTransfer) { myView.stopTreeBusyIndicator() }
      myEdtExecutor.addListener(futureTransfer) { Disposer.dispose(tracker) }
      return futureTransfer.transform(myEdtExecutor) { tracker.summary }
    }

    fun addUploadOperationWork(
      tracker: FileTransferOperationTracker,
      files: List<Path>
    ): ListenableFuture<Unit> {
      return executeFuturesInSequence(files.iterator()) { addUploadOperationWork(tracker, it) }
    }

    fun addUploadOperationWork(
      tracker: FileTransferOperationTracker,
      path: Path
    ): ListenableFuture<Unit> {
      val progress = createFileTransferEstimatorProgress(tracker)
      return myWorkEstimator.estimateUploadWork(path, progress).transform(myEdtExecutor) {
        tracker.addWorkEstimate(it)
      }
    }

    fun addDownloadOperationWork(
      tracker: FileTransferOperationTracker,
      entryNodes: List<DeviceFileEntryNode>
    ): ListenableFuture<Unit> {
      return executeFuturesInSequence(entryNodes.iterator()) { addDownloadOperationWork(tracker, it) }
    }

    fun addDownloadOperationWork(
      tracker: FileTransferOperationTracker,
      entryNode: DeviceFileEntryNode
    ): ListenableFuture<Unit> {
      val progress = createFileTransferEstimatorProgress(tracker)
      return myWorkEstimator.estimateDownloadWork(entryNode.entry, entryNode.isSymbolicLinkToDirectory, progress)
        .transform(myEdtExecutor) { tracker.addWorkEstimate(it) }
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

    private fun downloadSingleNode(
      node: DeviceFileEntryNode,
      localPath: Path,
      tracker: FileTransferOperationTracker
    ): ListenableFuture<Unit> =
      if (node.entry.isDirectory) {
        downloadSingleDirectory(node, localPath, tracker)
      } else {
        downloadSingleFile(node, localPath, tracker)
      }

    private fun downloadSingleFile(
      treeNode: DeviceFileEntryNode,
      localPath: Path,
      tracker: FileTransferOperationTracker
    ): ListenableFuture<Unit> {
      assert(!treeNode.entry.isDirectory)

      // Download single file
      if (treeNode.isTransferring) {
        tracker.addProblem(Exception(String.format("File %s is already downloading or uploading", getUserFacingNodeName(treeNode))))
        return Futures.immediateFuture(null)
      }
      val futureEntrySize = downloadFileEntry(treeNode, localPath, tracker)
      val futureResult = SettableFuture.create<Unit>()
      myEdtExecutor.addConsumer(futureEntrySize) { byteCount: Long?, throwable: Throwable? ->
        if (throwable != null) {
          tracker.addProblem(
            Exception(
              String.format("Error saving contents of device file %s", getUserFacingNodeName(treeNode)),
              throwable
            )
          )
        } else {
          tracker.summary.addFileCount(1)
          tracker.summary.addByteCount(byteCount!!)
        }
        futureResult.set(Unit)
      }
      return futureResult
    }

    private fun downloadSingleDirectory(
      treeNode: DeviceFileEntryNode,
      localDirectoryPath: Path,
      tracker: FileTransferOperationTracker
    ): ListenableFuture<Unit> {
      assert(treeNode.entry.isDirectory || treeNode.isSymbolicLinkToDirectory)
      if (tracker.isCancelled) {
        return Futures.immediateCancelledFuture()
      }
      tracker.processDirectory()

      // Ensure directory is created locally
      try {
        FileUtils.mkdirs(localDirectoryPath.toFile())
      } catch (e: Exception) {
        return Futures.immediateFailedFuture(e)
      }
      tracker.summary.addDirectoryCount(1)
      val futureResult = SettableFuture.create<Unit>()
      val futureLoadChildren = loadNodeChildren(treeNode)
      myEdtExecutor.addCallback(futureLoadChildren, object : FutureCallback<Unit> {
        override fun onSuccess(result: Unit?) {
          val futureDownloadChildren = executeFuturesInSequence(treeNode.childEntryNodes.iterator()) { node: DeviceFileEntryNode ->
            val nodePath = localDirectoryPath.resolve(node.entry.name)
            downloadSingleNode(node, nodePath, tracker)
          }
          myEdtExecutor.addConsumer(futureDownloadChildren) { _: Unit?, throwable: Throwable? ->
            if (throwable != null) {
              tracker.addProblem(throwable)
            }
            futureResult.set(Unit)
          }
        }

        override fun onFailure(t: Throwable) {
          tracker.addProblem(t)
          futureResult.set(Unit)
        }
      })
      return futureResult
    }

    override fun copyNodePathsInvoked(treeNodes: List<DeviceFileEntryNode>) {
      val text = treeNodes.map { it.entry.fullPath }.joinToString("\n")
      CopyPasteManager.getInstance().setContents(StringSelection(text))
      trackAction(DeviceExplorerEvent.Action.COPY_PATH)
    }

    override fun newFileInvoked(parentTreeNode: DeviceFileEntryNode) {
      newFileOrDirectory(parentTreeNode,
                         "NewTextFile.txt",
                         UIBundle.message("new.file.dialog.title"),
                         UIBundle.message("create.new.file.enter.new.file.name.prompt.text"),
                         UIBundle.message("create.new.file.file.name.cannot.be.empty.error.message"),
                         { UIBundle.message("create.new.file.could.not.create.file.error.message", it) },
                         { parentTreeNode.entry.createNewFile(it) })
      trackAction(DeviceExplorerEvent.Action.NEW_FILE)
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

      trackAction(DeviceExplorerEvent.Action.SYNC)
      myView.startTreeBusyIndicator()
      val futuresRefresh =
        executeFuturesInSequence(directoryNodes.iterator()) {
          it.isLoaded = false
          loadNodeChildren(it)
        }
      myEdtExecutor.addListener(futuresRefresh) { myView.stopTreeBusyIndicator() }
    }

    override fun deleteNodesInvoked(nodes: List<DeviceFileEntryNode>) {
      if (nodes.isEmpty()) {
        return
      }
      if (!checkLongRunningOperationAllowed()) {
        myView.reportErrorRelatedToNode(getCommonParentNode(nodes), DEVICE_EXPLORER_BUSY_MESSAGE, RuntimeException())
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
      val problems: MutableList<String> = LinkedList()
      for (fileEntry in fileEntries) {
        val futureDelete = fileEntry.delete()
        try {
          futureDelete.get(FILE_ENTRY_DELETION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        } catch (t: Throwable) {
          LOGGER.info(String.format("Error deleting file \"%s\"", fileEntry.fullPath), t)
          var problemMessage = ExceptionUtil.getRootCause(t).message
          if (StringUtil.isEmpty(problemMessage)) {
            problemMessage = "Error deleting file"
          }
          problemMessage = String.format("%s: %s", fileEntry.fullPath, problemMessage)
          problems.add(problemMessage)
        }
      }
      if (!problems.isEmpty()) {
        reportDeletionProblem(problems)
      } else {
        trackAction(DeviceExplorerEvent.Action.DELETE)
      }

      // Refresh the parent node(s) to remove the deleted files
      val parentsToRefresh = nodes.mapNotNull { DeviceFileEntryNode.fromNode(it.parent) }.toSet()
      executeFuturesInSequence(parentsToRefresh.iterator()) {
        it.isLoaded = false
        loadNodeChildren(it)
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
      newFileOrDirectory(parentTreeNode,
                         "NewFolder",
                         UIBundle.message("new.folder.dialog.title"),
                         UIBundle.message("create.new.folder.enter.new.folder.name.prompt.text"),
                         UIBundle.message("create.new.folder.folder.name.cannot.be.empty.error.message"),
                         { UIBundle.message("create.new.folder.could.not.create.folder.error.message", it) },
                         { parentTreeNode.entry.createNewDirectory(it) })
      trackAction(DeviceExplorerEvent.Action.NEW_DIRECTORY)
    }

    private fun newFileOrDirectory(
      parentTreeNode: DeviceFileEntryNode,
      initialName: String,
      title: String,
      prompt: String,
      emptyErrorMessage: String,
      errorMessage: (String) -> String,
      createFunction: (String) -> ListenableFuture<Unit>
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
        val futureResult = createFunction(newFileName)
        try {
          futureResult.get(FILE_ENTRY_CREATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)

          // Refresh the parent node to show the newly created file
          parentTreeNode.isLoaded = false
          val futureLoad = loadNodeChildren(parentTreeNode)
          myEdtExecutor.addListener(futureLoad) { myView.expandNode(parentTreeNode) }
        } catch (e: ExecutionException) {
          showErrorMessage(errorMessage(newFileName), e)
          initialName = newFileName
          continue  // Try again
        } catch (e: InterruptedException) {
          showErrorMessage(errorMessage(newFileName), e)
          initialName = newFileName
          continue
        } catch (e: TimeoutException) {
          showErrorMessage(errorMessage(newFileName), e)
          initialName = newFileName
          continue
        }
        return
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
        myView.reportErrorRelatedToNode(treeNode, DEVICE_EXPLORER_BUSY_MESSAGE, RuntimeException())
        return
      }
      val descriptor = FileChooserDescriptorFactory.createAllButJarContentsDescriptor()
      val filesRef = AtomicReference<List<VirtualFile>>()
      FileChooser.chooseFiles(descriptor, myProject, null) { filesRef.set(it) }
      val files = filesRef.get()
      if (files == null || files.isEmpty()) {
        return
      }
      uploadVirtualFilesInvoked(treeNode, files, DeviceExplorerEvent.Action.UPLOAD)
    }

    override fun uploadFilesInvoked(treeNode: DeviceFileEntryNode, files: List<Path>) {
      if (!checkLongRunningOperationAllowed()) {
        myView.reportErrorRelatedToNode(treeNode, DEVICE_EXPLORER_BUSY_MESSAGE, RuntimeException())
        return
      }
      val vfiles = files.mapNotNull { VfsUtil.findFile(it, true) }
      uploadVirtualFilesInvoked(treeNode, vfiles, DeviceExplorerEvent.Action.DROP)
    }

    private fun uploadVirtualFilesInvoked(
      treeNode: DeviceFileEntryNode,
      files: List<VirtualFile>,
      action: DeviceExplorerEvent.Action
    ) {
      if (!checkLongRunningOperationAllowed()) {
        myView.reportErrorRelatedToNode(treeNode, DEVICE_EXPLORER_BUSY_MESSAGE, RuntimeException())
        return
      }
      val futureSummary = wrapFileTransfer(
        { tracker: FileTransferOperationTracker ->
          val paths = files.map {Paths.get(it.path)}
          addUploadOperationWork(tracker, paths)
        },
        { tracker: FileTransferOperationTracker -> uploadVirtualFiles(treeNode, files, tracker) },
        true
      )
      myEdtExecutor.addCallback(futureSummary, object : FutureCallback<FileTransferSummary?> {
        override fun onSuccess(result: FileTransferSummary?) {
          checkNotNull(result)
          result.action = action
          reportUploadFilesSummary(treeNode, result)
        }

        override fun onFailure(t: Throwable) {
          myView.reportErrorRelatedToNode(treeNode, "Error uploading files(s) to device", t)
        }
      })
    }

    private fun reportUploadFilesSummary(treeNode: DeviceFileEntryNode, summary: FileTransferSummary) {
      reportFileTransferSummary(treeNode, summary, "uploaded", "uploading")
    }

    private fun uploadVirtualFiles(
      parentNode: DeviceFileEntryNode,
      files: List<VirtualFile>,
      tracker: FileTransferOperationTracker
    ): ListenableFuture<Unit> {
      // Upload each file
      val futureUploadFiles = executeFuturesInSequence(files.iterator()) {
        uploadVirtualFile(parentNode, it, tracker)
      }

      // Refresh children nodes
      return myEdtExecutor.transformAsync(futureUploadFiles) {
        parentNode.isLoaded = false
        loadNodeChildren(parentNode)
      }
    }

    private fun uploadVirtualFile(
      treeNode: DeviceFileEntryNode,
      file: VirtualFile,
      tracker: FileTransferOperationTracker
    ): ListenableFuture<Unit> {
      return if (file.isDirectory) {
        uploadDirectory(treeNode, file, tracker)
      } else {
        uploadFile(treeNode, file, tracker)
      }
    }

    private fun uploadDirectory(
      parentNode: DeviceFileEntryNode,
      file: VirtualFile,
      tracker: FileTransferOperationTracker
    ): ListenableFuture<Unit> {
      if (tracker.isCancelled) {
        return Futures.immediateCancelledFuture()
      }
      tracker.processDirectory()
      tracker.summary.addDirectoryCount(1)
      val futureResult = SettableFuture.create<Unit>()

      // Create directory in destination device
      val parentEntry = parentNode.entry
      val directoryName = file.name
      val futureDirectory = parentEntry.createNewDirectory(directoryName)
      myEdtExecutor.addConsumer(futureDirectory) { _: Unit?, createDirectoryError: Throwable? ->
        // Refresh node entries
        parentNode.isLoaded = false
        val futureLoadChildren = loadNodeChildren(parentNode)
        myEdtExecutor.addCallback(futureLoadChildren, object : FutureCallback<Unit> {
          override fun onSuccess(result: Unit?) {
            // Find node for newly created directory
            val childNode = parentNode.findChildEntry(directoryName)
            if (childNode == null) {
              // Note: This would happen if we didn't filter hidden files in the code below
              //       or if we failed to create the child directory
              if (createDirectoryError != null) {
                tracker.addProblem(createDirectoryError)
              } else {
                tracker.addProblem(Exception(String.format("Error creating directory \"%s\"", directoryName)))
              }
              futureResult.set(Unit)
              return
            }

            // Upload all files into destination device
            // Note: We ignore hidden files ("." prefix) for now, as the listing service
            //       currently does not list hidden files/directories.
            val childFiles = file.children.filter { !it.name.startsWith(".") }
            val futureFileUploads = uploadVirtualFiles(childNode, childFiles, tracker)
            myEdtExecutor.addListener(futureFileUploads) { futureResult.set(Unit) }
          }

          override fun onFailure(t: Throwable) {
            tracker.addProblem(t)
            futureResult.set(Unit)
          }
        })
      }
      return futureResult
    }

    private fun uploadFile(
      parentNode: DeviceFileEntryNode,
      file: VirtualFile,
      tracker: FileTransferOperationTracker
    ): ListenableFuture<Unit> {
      if (tracker.isCancelled) {
        return Futures.immediateCancelledFuture()
      }
      tracker.processFile()
      tracker.setUploadFileText(file, 0, 0)
      val futureResult = SettableFuture.create<Unit>()
      logFuture(
        futureResult
      ) { millis: Long? ->
        String.format(
          Locale.US,
          "Uploaded file in %,d msec: %s",
          millis,
          AdbPathUtil.resolve(parentNode.entry.fullPath, file.name)
        )
      }
      val parentEntry = parentNode.entry
      val localPath = Paths.get(file.path)
      val uploadState = UploadFileState()
      val futureUpload = parentEntry.uploadFile(localPath, object : FileTransferProgress {
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
            if (uploadState.childNode != null) {
              uploadState.childNode!!.setTransferProgress(currentBytes, totalBytes)
              return
            }

            // If we already tried to load the children, reset so we try again
            if (uploadState.loadChildrenFuture != null && uploadState.loadChildrenFuture!!.isDone) {
              uploadState.loadChildrenFuture = null
            }

            // Start loading children
            if (currentBytes > 0) {
              if (uploadState.loadChildrenFuture == null) {
                parentNode.isLoaded = false
                uploadState.loadChildrenFuture = loadNodeChildren(parentNode)
              }
            }
          }
        }

        override fun isCancelled(): Boolean {
          return tracker.isCancelled
        }
      })
      myEdtExecutor.addConsumer(futureUpload) { _: Unit?, throwable: Throwable? ->
        // Complete this method
        futureResult.set(Unit)

        // Update summary
        if (throwable != null) {
          tracker.addProblem(throwable)
        } else {
          tracker.summary.addFileCount(1)
          tracker.summary.addByteCount(uploadState.byteCount)
        }

        // Signal upload is done
        if (uploadState.childNode != null && tracker.isInForeground) {
          stopNodeUpload(uploadState.childNode!!)
        }
      }
      return futureResult
    }

    private inner class UploadFileState(
      var loadChildrenFuture: ListenableFuture<Unit>? = null,
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
        myView.reportMessageRelatedToNode(node, successMessage)
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
      myView.reportErrorRelatedToNode(
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

    private fun <V> logFuture(future: ListenableFuture<V>, message: (Long) -> String) {
      val startNano = System.nanoTime()
      myEdtExecutor.addListener(future) {
        val endNano = System.nanoTime()
        LOGGER.trace(message((endNano - startNano) / 1000000))
      }
    }

    @Throws(Exception::class)
    private fun chooseSaveAsFilePath(treeNode: DeviceFileEntryNode): Path? {
      val entry = treeNode.entry
      val localPath = fileManager.getDefaultLocalPathForEntry(entry)
      FileUtils.mkdirs(localPath.parent.toFile())
      val baseDir = VfsUtil.findFileByIoFile(localPath.parent.toFile(), true)
        ?: throw Exception("Unable to locate file \"${localPath.parent}\"")
      val descriptor = FileSaverDescriptor("Save As", "")
      val saveFileDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, myProject)
      val fileWrapper = saveFileDialog.save(baseDir, localPath.fileName.toString()) ?: throw CancellationException()
      return fileWrapper.file.toPath()
    }

    @Throws(Exception::class)
    private fun chooseSaveAsDirectoryPath(treeNode: DeviceFileEntryNode): Path? {
      val entry = treeNode.entry
      val localPath = fileManager.getDefaultLocalPathForEntry(entry)
      FileUtils.mkdirs(localPath.toFile())
      val localDir = VfsUtil.findFileByIoFile(localPath.toFile(), true)
        ?: throw Exception("Unable to locate directory \"${localPath.parent}\"")
      val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
      val result = AtomicReference<Path>()
      FileChooser.chooseFiles(descriptor, myProject, localDir) { files: List<VirtualFile> ->
        if (files.size == 1) {
          val path = Paths.get(files[0].path)
          result.set(path)
        }
      }
      return result.get()
    }

    private fun downloadFileEntry(
      treeNode: DeviceFileEntryNode,
      localPath: Path,
      tracker: FileTransferOperationTracker
    ): ListenableFuture<Long> {
      if (tracker.isCancelled) {
        return Futures.immediateCancelledFuture()
      }
      tracker.processFile()
      val entry = treeNode.entry
      val sizeRef = AtomicReference(0L)
      val futureDownload = fileManager.downloadFileEntry(entry, localPath, object : DownloadProgress {
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
          sizeRef.set(sizeRef.get() + previousBytes)
          if (tracker.isInForeground) {
            val currentNode = getTreeNodeFromEntry(treeNode, entryFullPath)!!
            stopNodeDownload(currentNode)
          }
        }

        override fun isCancelled(): Boolean {
          return tracker.isCancelled
        }
      })
      logFuture(futureDownload) { millis -> String.format(Locale.US, "Downloaded file in %,d msec: %s", millis, entry.fullPath) }
      return myEdtExecutor.transform(futureDownload) { sizeRef.get() }
    }

    override fun treeNodeExpanding(node: DeviceFileEntryNode) {
      loadNodeChildren(node)
    }

    private fun loadNodeChildren(node: DeviceFileEntryNode): ListenableFuture<Unit> {

      // Track a specific set of directories to analyze user behaviour
      if (node.entry.fullPath.matches(Regex("^/data/data/[^/]+$"))) {
        trackAction(DeviceExplorerEvent.Action.EXPAND_APP_DATA)
      }

      // Ensure node is expanded only once
      if (node.isLoaded) {
        return Futures.immediateFuture(null)
      }
      node.isLoaded = true

      // Leaf nodes are not expandable
      if (node.isLeaf) {
        return Futures.immediateFuture(null)
      }
      val treeModel = getTreeModel()
      val treeSelectionModel = getTreeSelectionModel()
      if (treeModel == null || treeSelectionModel == null) {
        return Futures.immediateFuture(null)
      }
      val fileSystem = myModel.activeDevice
      if (fileSystem != node.entry.fileSystem) {
        return Futures.immediateFuture(null)
      }
      val showLoadingNode = ShowLoadingNodeRequest(treeModel, node)
      myLoadingNodesAlarms.addRequest(showLoadingNode, myShowLoadingNodeDelayMillis)
      startLoadChildren(node)
      val futureEntries = node.entry.entries
      myEdtExecutor.addCallback(futureEntries, object : FutureCallback<List<DeviceFileEntry>> {
        override fun onSuccess(result: List<DeviceFileEntry>?) {
          checkNotNull(result)
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
            isDirectory = { it.isDirectory || isLinkToDirectory.contains(it.name)
          })

          val addedNodes = updateChildrenNodes(treeModel, node, result.sortedWith(comparator))

          // Restore selection
          restoreTreeSelection(treeSelectionModel, oldSelections, node)
          val symlinkNodes = addedNodes.filter { it.entry.isSymbolicLink }
          querySymbolicLinks(symlinkNodes, treeModel)
        }

        override fun onFailure(t: Throwable) {
          var message = ExceptionUtil.getRootCause(t).message
          if (StringUtil.isEmpty(message)) {
            message = String.format("Unable to list entries of directory %s", getUserFacingNodeName(node))
          }
          node.removeAllChildren()
          node.add(ErrorNode(message!!))
          node.allowsChildren = true
          treeModel.nodeStructureChanged(node)
        }
      })
      myEdtExecutor.addListener(futureEntries) {
        stopLoadChildren(node)
        myLoadingNodesAlarms.cancelRequest(showLoadingNode)
      }
      return futureEntries.ignoreResult()
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
    private fun querySymbolicLinks(symlinkNodes: List<DeviceFileEntryNode>, treeModel: DefaultTreeModel) {
      // Note: We process (asynchronously) one entry at a time, instead of all of them in parallel,
      //       to avoid flooding the device with too many requests, which would eventually lead
      //       to the device to reject additional requests.
      executeFuturesInSequence(symlinkNodes.iterator()) { treeNode: DeviceFileEntryNode ->
        val futureIsLinkToDirectory = treeNode.entry.isSymbolicLinkToDirectory
        myEdtExecutor.addConsumer(futureIsLinkToDirectory) { result: Boolean?, throwable: Throwable? ->
          // Log error, but keep going as we may have more symlinkNodes to examine
          if (throwable != null) {
            LOGGER.info(
              String.format(
                "Error determining if file entry \"%s\" is a link to a directory",
                treeNode.entry.name
              ),
              throwable
            )
          }

          // Stop all processing if tree model has changed, i.e. UI has been switched to another device
          if (myModel.treeModel != treeModel) {
            return@addConsumer
          }

          // Update tree node appearance (in case of "null"" result, we assume the entry
          // does not target a directory).
          val isDirectory = result != null && result
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
        futureIsLinkToDirectory.ignoreResult()
      }
    }

    private fun getUserFacingNodeName(node: DeviceFileEntryNode): String {
      return if (StringUtil.isEmpty(node.entry.name)) "[root]" else "\"" + node.entry.name + "\""
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

  private class ShowLoadingNodeRequest(private val myTreeModel: DefaultTreeModel, private val myNode: DeviceFileEntryNode) : Runnable {
    override fun run() {
      myNode.allowsChildren = true
      myNode.add(MyLoadingNode(myNode.entry))
      myTreeModel.nodeStructureChanged(myNode)
    }
  }

  private inner class MyTransferringNodesRepaint : Runnable {
    override fun run() {
      for (node in myTransferringNodes) {
        node.incTransferringTick()
        getTreeModel()?.nodeChanged(node)
      }
      myTransferringNodesAlarms.addRequest(MyTransferringNodesRepaint(), myTransferringNodeRepaintMillis)
    }
  }

  private inner class MyLoadingChildrenRepaint : Runnable {
    override fun run() {
      for (child in myLoadingChildren) {
        if (child.childCount == 0) continue
        val node = child.firstChild
        if (node is MyLoadingNode) {
          node.incTick()
          getTreeModel()?.nodeChanged(node)
        }
      }
      myLoadingChildrenAlarms.addRequest(MyLoadingChildrenRepaint(), myTransferringNodeRepaintMillis)
    }
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
    fun openFile(localPath: Path)

    @UiThread
    fun openFile(virtualFile: VirtualFile)
  }

  companion object {
    private val LOGGER = logger<DeviceExplorerController>()
    private val KEY = Key.create<DeviceExplorerController>(
      DeviceExplorerController::class.java.name
    )
    private const val DEVICE_EXPLORER_BUSY_MESSAGE = "Device Explorer is busy, please retry later or cancel current operation"
    private const val FILE_ENTRY_CREATION_TIMEOUT_MILLIS: Long = 10000
    private const val FILE_ENTRY_DELETION_TIMEOUT_MILLIS: Long = 10000

    @JvmStatic
    fun getProjectController(project: Project?): DeviceExplorerController? {
      return project?.getUserData(KEY)
    }
  }
}