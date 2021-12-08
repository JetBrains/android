/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.explorer.mocks.MockDeviceFileSystemService.addDevice
import com.android.tools.idea.explorer.mocks.MockDeviceFileSystem.root
import com.android.tools.idea.explorer.mocks.MockDeviceFileEntry.addDirectory
import com.android.tools.idea.explorer.mocks.MockDeviceFileEntry.addDirLink
import com.android.tools.idea.explorer.mocks.MockDeviceFileEntry.addFile
import com.android.tools.idea.explorer.mocks.MockDeviceFileEntry.addFileLink
import com.android.tools.idea.explorer.DeviceExplorerController.Companion.getProjectController
import com.android.tools.idea.explorer.DeviceExplorerController.setup
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.explorer.fs.DeviceFileSystemService.start
import com.android.tools.idea.explorer.DeviceExplorerController.restartService
import com.android.tools.idea.explorer.fs.DeviceFileSystemService.restart
import com.android.tools.idea.explorer.fs.DeviceFileSystemService.devices
import com.android.tools.idea.explorer.mocks.MockDeviceFileSystem.rootDirectoryError
import com.android.tools.idea.explorer.DeviceExplorerController.setShowLoadingNodeDelayMillis
import com.android.tools.idea.explorer.DeviceExplorerController.setTransferringNodeRepaintMillis
import com.android.tools.idea.explorer.mocks.MockDeviceFileEntry.getEntriesTimeoutMillis
import com.android.tools.idea.explorer.mocks.MockDeviceFileEntry.getEntriesError
import com.android.tools.idea.explorer.mocks.MockDeviceExplorerFileManager.openFileInEditorTracker
import com.android.tools.idea.explorer.mocks.MockDeviceFileSystem.downloadError
import com.android.tools.idea.explorer.mocks.MockDeviceExplorerFileManager.downloadFileEntryTracker
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFutureException
import com.android.tools.idea.explorer.mocks.MockDeviceExplorerFileManager.downloadFileEntryCompletionTracker
import com.android.tools.idea.explorer.mocks.MockDeviceFileSystemService.listeners
import com.android.tools.idea.explorer.fs.DeviceFileSystemServiceListener.deviceUpdated
import com.android.tools.idea.explorer.DeviceExplorerController.selectActiveDevice
import com.android.tools.idea.explorer.mocks.MockDeviceFileSystem.deviceSerialNumber
import com.android.tools.idea.explorer.mocks.MockDeviceFileSystemService.removeDevice
import com.intellij.testFramework.replaceService
import com.android.tools.idea.explorer.mocks.MockDeviceFileSystem.downloadChunkSize
import com.android.tools.idea.explorer.mocks.MockDeviceFileSystem.downloadChunkIntervalMillis
import com.android.tools.idea.explorer.mocks.MockDeviceFileEntry.name
import com.android.tools.idea.explorer.fs.DeviceFileEntry.name
import com.android.tools.idea.explorer.fs.DeviceFileEntry.isFile
import com.android.tools.idea.explorer.fs.DeviceFileEntry.isDirectory
import com.android.tools.idea.explorer.mocks.MockDeviceFileEntry.fullPath
import com.android.tools.idea.explorer.mocks.MockDeviceFileEntry.deleteError
import com.android.tools.idea.explorer.mocks.MockDeviceFileSystem.uploadChunkSize
import com.android.tools.idea.explorer.mocks.MockDeviceFileSystem.uploadChunkIntervalMillis
import com.android.tools.idea.explorer.mocks.MockDeviceFileSystem.uploadError
import com.android.tools.idea.explorer.mocks.MockDeviceFileEntry.mockEntries
import com.android.tools.idea.explorer.mocks.MockDeviceExplorerFileManager.openFileInEditorError
import com.android.tools.idea.explorer.DeviceExplorerController.Companion.makeFileOpener
import com.android.tools.idea.explorer.mocks.MockDeviceFileEntry.isDirectory
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFutures
import com.android.tools.idea.explorer.fs.DeviceFileSystem.name
import com.android.tools.idea.explorer.DeviceExplorerController.hasActiveDevice
import com.android.tools.idea.explorer.mocks.MockDeviceFileEntry.size
import com.android.tools.idea.explorer.mocks.MockDeviceFileEntry.parent
import com.android.tools.idea.explorer.DeviceExplorerModel
import com.android.tools.idea.explorer.mocks.MockDeviceExplorerView
import com.android.tools.idea.explorer.mocks.MockDeviceFileSystemService
import com.android.tools.idea.explorer.mocks.MockDeviceExplorerFileManager
import com.android.tools.idea.explorer.mocks.MockDeviceFileSystem
import com.android.tools.idea.explorer.mocks.MockDeviceFileEntry
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestInputDialog
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import kotlin.Throws
import com.intellij.util.concurrency.EdtExecutorService
import com.android.tools.idea.explorer.fs.DeviceFileSystem
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.DefaultTreeSelectionModel
import com.android.tools.idea.explorer.DeviceExplorerControllerTest.MockDeviceFileSystemRendererFactory
import org.mockito.Mockito
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.ide.ClipboardSynchronizer
import com.android.tools.idea.explorer.DeviceExplorerController
import java.lang.InterruptedException
import com.android.tools.idea.explorer.fs.DeviceFileSystemService
import org.mockito.ArgumentMatchers
import java.lang.RuntimeException
import javax.swing.tree.ExpandVetoException
import com.android.tools.idea.explorer.MyLoadingNode
import javax.swing.event.TreeModelEvent
import com.android.tools.idea.explorer.DeviceExplorerFileManager
import com.android.tools.idea.explorer.DeviceExplorerViewListener
import com.android.tools.idea.explorer.DeviceFileEntryNode
import java.lang.Runnable
import com.android.tools.idea.explorer.DeviceExplorerControllerTest
import com.android.tools.idea.explorer.fs.DeviceFileEntry
import com.android.tools.idea.explorer.fs.DeviceFileSystemServiceListener
import javax.swing.event.ListDataEvent
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.FileSaverDialog
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.PathChooserDialog
import com.intellij.ui.UIBundle
import java.awt.datatransfer.Transferable
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.DataFlavor
import com.android.tools.idea.adb.AdbShellCommandException
import javax.swing.TransferHandler
import javax.swing.TransferHandler.TransferSupport
import java.util.function.IntFunction
import java.io.IOException
import com.android.tools.idea.explorer.DeviceExplorerController.NodeSorting.CustomComparator
import com.google.common.truth.Truth
import java.util.function.BiConsumer
import com.android.tools.idea.explorer.DeviceExplorerView
import com.android.tools.idea.explorer.DeviceFileSystemRendererFactory
import com.android.tools.idea.ddms.DeviceNamePropertiesProvider
import com.android.tools.idea.explorer.fs.DeviceFileSystemRenderer
import com.android.tools.idea.explorer.mocks.MockDeviceFileSystemRenderer
import com.android.tools.idea.explorer.ui.TreeUtil
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.intellij.util.Consumer
import com.intellij.util.ui.tree.TreeModelAdapter
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.ide.PooledThreadExecutor
import java.awt.Component
import java.awt.Point
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.io.File
import java.lang.Exception
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.Enumeration
import java.util.Objects
import java.util.Stack
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors
import java.util.stream.IntStream
import java.util.stream.Stream
import javax.swing.JComponent
import javax.swing.RepaintManager
import javax.swing.event.ListDataListener
import javax.swing.tree.TreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

class DeviceExplorerControllerTest : AndroidTestCase() {
  private var myModel: DeviceExplorerModel? = null
  private var myMockView: MockDeviceExplorerView? = null
  private var myMockService: MockDeviceFileSystemService? = null
  private var myMockFileManager: MockDeviceExplorerFileManager? = null
  private var myDownloadLocationSupplier: Supplier<Path>? = null
  private var myDevice1: MockDeviceFileSystem? = null
  private var myFoo: MockDeviceFileEntry? = null
  private var myFooFile1: MockDeviceFileEntry? = null
  private var myFooFile2: MockDeviceFileEntry? = null
  private var myFooLink1: MockDeviceFileEntry? = null
  private var myFile1: MockDeviceFileEntry? = null
  private var myFile2: MockDeviceFileEntry? = null
  private var myDevice2: MockDeviceFileSystem? = null
  private var myMockRepaintManager: RepaintManager? = null
  private var myFooDir: MockDeviceFileEntry? = null
  private var myFooDirLink: MockDeviceFileEntry? = null
  private var myInitialTestDialog: TestDialog? = null
  private var myInitialTestInputDialog: TestInputDialog? = null
  private var myEdtExecutor: FutureCallbackExecutor? = null
  private var myTaskExecutor: FutureCallbackExecutor? = null
  private var myTearingDown = false
  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    myEdtExecutor = FutureCallbackExecutor.wrap(EdtExecutorService.getInstance())
    myTaskExecutor = FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE)
    myModel = object : DeviceExplorerModel() {
      override fun setActiveDeviceTreeModel(
        device: DeviceFileSystem?,
        treeModel: DefaultTreeModel?,
        treeSelectionModel: DefaultTreeSelectionModel?
      ) {
        if (!myTearingDown) {
          // We notify the mock view before everything else to avoid having a dependency
          // on the order of registration of listeners registered with {@code DeviceExplorerModel.addListener()}
          assert(myMockView != null)
          myMockView!!.deviceTreeModelUpdated(device, treeModel, treeSelectionModel)
        }
        super.setActiveDeviceTreeModel(device, treeModel, treeSelectionModel)
      }
    }
    myMockService = MockDeviceFileSystemService(project, myEdtExecutor!!, myTaskExecutor!!)
    myMockView = MockDeviceExplorerView(project, MockDeviceFileSystemRendererFactory(), myModel)
    val downloadPath = FileUtil.createTempDirectory("device-explorer-temp", "", true)
    myDownloadLocationSupplier = Mockito.mock(Supplier::class.java)
    Mockito.`when`(myDownloadLocationSupplier.get()).thenReturn(downloadPath.toPath())
    myMockFileManager = MockDeviceExplorerFileManager(project, myDownloadLocationSupplier)
    myDevice1 = myMockService!!.addDevice("TestDevice-1")
    myFoo = myDevice1!!.root.addDirectory("Foo")
    myFooDirLink = myDevice1!!.root.addDirLink("fooDirLink", "fooDir")
    myFooFile1 = myFoo!!.addFile("fooFile1.txt")
    myFooFile2 = myFoo!!.addFile("fooFile2.txt")
    myFooLink1 = myFoo!!.addFileLink("fooLink1.txt", "fooFile1.txt")
    myFooDir = myFoo!!.addDirectory("fooDir")
    myFooDir!!.addFile("fooDirFile1.txt")
    myFooDir!!.addFile("fooDirFile2.txt")
    myFile1 = myDevice1!!.root.addFile("file1.txt")
    myFile2 = myDevice1!!.root.addFile("file2.txt")
    myDevice1!!.root.addFile("file3.txt")
    myDevice2 = myMockService!!.addDevice("TestDevice-2")
    myDevice2!!.root.addDirectory("Foo2")
    myDevice2!!.root.addFile("foo2File1.txt")
    myDevice2!!.root.addFile("foo2File2.txt")
  }

  @Throws(Exception::class)
  override fun tearDown() {
    try {
      myTearingDown = true
      RepaintManager.setCurrentManager(null)
      myMockRepaintManager = null
      if (myInitialTestDialog != null) {
        TestDialogManager.setTestDialog(myInitialTestDialog)
      }
      if (myInitialTestInputDialog != null) {
        TestDialogManager.setTestInputDialog(myInitialTestInputDialog)
      }
      if (myMockFileManager != null) {
        Disposer.dispose(myMockFileManager!!)
        myMockFileManager = null
      }
      myFooLink1 = null
      myFooFile1 = null
      myFooFile2 = null
      myFooDir = null
      myFooDirLink = null
      myFoo = null
      myFile1 = null
      myFile2 = null
      if (myMockService != null) {
        myMockService = null
      }
      myTaskExecutor = null
      myEdtExecutor = null
      ClipboardSynchronizer.getInstance().resetContent()
    } finally {
      myMockService = null
      myMockView = null
      myModel = null
      myDevice1 = null
      myDevice2 = null
      super.tearDown()
    }
  }

  private fun injectRepaintManagerMock() {
    val current = RepaintManager.currentManager(null)!!
    myMockRepaintManager = Mockito.spy(current)
    RepaintManager.setCurrentManager(myMockRepaintManager)
  }

  @Throws(Exception::class)
  fun testControllerIsSetAsProjectKey() {
    // Prepare
    val controller = createController()

    // Assert
    assertEquals(controller, getProjectController(project))
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun testStartController() {
    // Prepare
    val controller = createController()

    // Act
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())

    // Assert
    checkMockViewInitialState(controller, myDevice1)
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun testStartControllerFailure() {
    // Prepare
    val setupErrorMessage = "<Unique error message>"
    val service = Mockito.mock(DeviceFileSystemService::class.java)
    Mockito.`when`<ListenableFuture<*>>(service.start(ArgumentMatchers.any<Supplier<*>>()))
      .thenReturn(Futures.immediateFailedFuture<Any>(RuntimeException(setupErrorMessage)))
    val controller = createController(
      myMockView,
      service,
      myMockFileManager,
      DeviceExplorerController.FileOpener { localPath: Path -> myMockFileManager!!.openFile(localPath) })

    // Act
    controller.setup()
    val errorMessage = pumpEventsAndWaitForFuture(myMockView!!.reportErrorRelatedToServiceTracker.consume())

    // Assert
    assertNotNull(errorMessage)
    assertTrue(errorMessage.contains(setupErrorMessage))
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun testStartControllerUnexpectedFailure() {
    // Prepare
    val service = Mockito.mock(DeviceFileSystemService::class.java)
    Mockito.`when`<ListenableFuture<*>>(service.start(ArgumentMatchers.any<Supplier<*>>()))
      .thenReturn(Futures.immediateFailedFuture<Any>(RuntimeException()))
    val controller = createController(
      myMockView,
      service,
      myMockFileManager,
      DeviceExplorerController.FileOpener { localPath: Path -> myMockFileManager!!.openFile(localPath) })

    // Act
    controller.setup()
    val errorMessage = pumpEventsAndWaitForFuture(myMockView!!.reportErrorRelatedToServiceTracker.consume())

    // Assert
    assertNotNull(errorMessage)
    assertTrue(errorMessage.contains("Error initializing ADB"))
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun testRestartController() {
    // Prepare
    val controller = createController()
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)

    // Act
    controller.restartService()
    pumpEventsAndWaitForFuture(myMockView!!.allDevicesRemovedTracker.consume())

    // Assert
    checkMockViewInitialState(controller, myDevice1)
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun testRestartControllerFailure() {
    // Prepare
    val setupErrorMessage = "<Unique error message>"
    val service = Mockito.mock(DeviceFileSystemService::class.java)
    Mockito.`when`<ListenableFuture<*>>(service.start(ArgumentMatchers.any<Supplier<*>>())).thenReturn(Futures.immediateFuture<Any>(null))
    Mockito.`when`<ListenableFuture<*>>(service.restart(ArgumentMatchers.any<Supplier<*>>()))
      .thenReturn(Futures.immediateFailedFuture<Any>(RuntimeException(setupErrorMessage)))
    Mockito.`when`<ListenableFuture<*>>(service.devices).thenReturn(Futures.immediateFuture(ArrayList<Any>()))
    val controller = createController(
      myMockView,
      service,
      myMockFileManager,
      DeviceExplorerController.FileOpener { localPath: Path -> myMockFileManager!!.openFile(localPath) })

    // Act
    controller.setup()
    controller.restartService()
    val errorMessage = pumpEventsAndWaitForFuture(myMockView!!.reportErrorRelatedToServiceTracker.consume())

    // Assert
    assertNotNull(errorMessage)
    assertTrue(errorMessage.contains(setupErrorMessage))
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun testGetDevicesFailure() {
    // Prepare
    val setupErrorMessage = "<Unique error message>"
    val service = Mockito.mock(DeviceFileSystemService::class.java)
    Mockito.`when`<ListenableFuture<*>>(service.start(ArgumentMatchers.any<Supplier<*>>()))
      .thenReturn(Futures.immediateFuture<Any>(null))
    Mockito.`when`<ListenableFuture<*>>(service.devices).thenReturn(Futures.immediateFailedFuture<Any>(RuntimeException(setupErrorMessage)))
    val controller = createController(
      myMockView,
      service,
      myMockFileManager,
      DeviceExplorerController.FileOpener { localPath: Path -> myMockFileManager!!.openFile(localPath) })

    // Act
    controller.setup()
    val errorMessage = pumpEventsAndWaitForFuture(myMockView!!.reportErrorRelatedToServiceTracker.consume())

    // Assert
    assertNotNull(errorMessage)
    assertTrue(errorMessage.contains(errorMessage))
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun testGetRootDirectoryFailure() {
    // Prepare
    val setupErrorMessage = "<Unique error message>"
    myDevice1!!.rootDirectoryError = RuntimeException(setupErrorMessage)
    val controller = createController()

    // Act
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewComboBox(controller)
    val errorMessage = pumpEventsAndWaitForFuture(myMockView!!.reportErrorRelatedToDeviceTracker.consume())

    // Assert
    assertNotNull(errorMessage)
    assertTrue(errorMessage.contains(setupErrorMessage))
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class, ExpandVetoException::class)
  fun testExpandChildren() {
    // Prepare
    val controller = createController()

    // Act
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)

    // Set timers to ensure the "loading..." animation code is hit
    controller.setShowLoadingNodeDelayMillis(10)
    controller.setTransferringNodeRepaintMillis(10)
    myFoo!!.getEntriesTimeoutMillis = 1000

    // Listen to node expansion effect (structure changed event)
    val fooPath = getFileEntryPath(myFoo!!)
    val futureNodeExpanded = createNodeExpandedFuture(myFoo!!)
    val futureTreeNodesChanged = SettableFuture.create<MyLoadingNode>()
    myMockView!!.tree.model.addTreeModelListener(object : TreeModelAdapter() {
      override fun treeNodesChanged(event: TreeModelEvent) {
        if (fooPath.lastPathComponent == event.treePath.lastPathComponent) {
          val children = event.children
          if (children != null && children.size == 1) {
            val child = children[0]
            if (child is MyLoadingNode) {
              futureTreeNodesChanged.set(child)
            }
          }
        }
      }
    })

    // Expand node
    myMockView!!.tree.expandPath(fooPath)

    // Wait for tree node to be expanded
    val myLoadingNode = pumpEventsAndWaitForFuture(futureTreeNodesChanged)
    val nodeExpandedPath = pumpEventsAndWaitForFuture(futureNodeExpanded)

    // Assert
    assertTrue(myLoadingNode.tick > 1)
    assertEquals(fooPath.lastPathComponent, nodeExpandedPath.lastPathComponent)
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun testExpandChildrenFailure() {
    // Prepare
    val controller = createController()
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)
    val errorMessage = "<Expected test error>"
    myFoo!!.getEntriesError = RuntimeException(errorMessage)
    val nodeExpandedFuture = createNodeExpandedFuture(myFoo!!)

    // Act
    expandEntry(myFoo!!)
    pumpEventsAndWaitForFuture(nodeExpandedFuture)

    // Assert
    val fooNode = getFileEntryPath(myFoo!!).lastPathComponent
    assertNotNull(fooNode)
    assertInstanceOf(fooNode, TreeNode::class.java)
    assertTrue((fooNode as TreeNode).childCount == 1)
    val errorNode: Any = fooNode.getChildAt(0)
    assertNotNull(errorNode)
    assertInstanceOf(errorNode, ErrorNode::class.java)
    assertEquals(errorMessage, (errorNode as ErrorNode).text)
  }

  @Throws(Exception::class)
  fun testOpenNodeInEditorDoesNothingForSymlinkToDirectory() {
    val mockFileManager: DeviceExplorerFileManager? = Mockito.spy(myMockFileManager)
    val controller = createController(
      myMockView,
      myMockService,
      mockFileManager,
      DeviceExplorerController.FileOpener { localPath: Path -> myMockFileManager!!.openFile(localPath) })
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)
    val listener = myMockView!!.listeners[0]
    val fooDirPath = getFileEntryPath(myFooDirLink!!)
    myMockView!!.tree.selectionPath = fooDirPath
    val node = fooDirPath.lastPathComponent as DeviceFileEntryNode
    val nodes: MutableList<DeviceFileEntryNode> = ArrayList()
    nodes.add(node)
    listener.openNodesInEditorInvoked(nodes)
    Mockito.verifyNoMoreInteractions(mockFileManager)
  }

  @Throws(Exception::class)
  fun testDownloadFileWithEnterKey() {
    downloadFile(myFile1) {

      // Send a VK_ENTER key event
      fireEnterKey(myMockView!!.tree)
      pumpEventsAndWaitForFuture(myMockView!!.openNodesInEditorInvokedTracker.consume())
    }
    pumpEventsAndWaitForFuture(myMockFileManager!!.openFileInEditorTracker.consume())
  }

  @Throws(Exception::class)
  fun testDownloadFileWithMouseClick() {
    downloadFile(myFile1) {
      val path = getFileEntryPath(myFile1!!)
      val pathBounds = myMockView!!.tree.getPathBounds(path)!!

      // Fire double-click event
      fireDoubleClick(myMockView!!.tree, pathBounds.x, pathBounds.y)
      pumpEventsAndWaitForFuture(myMockView!!.openNodesInEditorInvokedTracker.consume())
    }
    pumpEventsAndWaitForFuture(myMockFileManager!!.openFileInEditorTracker.consume())
  }

  @Throws(Exception::class)
  fun testDownloadFileLocationWithMouseClick() {
    // This saves in the default location for test
    downloadFile(myFile1) {
      val path = getFileEntryPath(myFile1!!)
      val pathBounds = myMockView!!.tree.getPathBounds(path)!!

      // Fire double-click event
      fireDoubleClick(myMockView!!.tree, pathBounds.x, pathBounds.y)
      pumpEventsAndWaitForFuture(myMockView!!.openNodesInEditorInvokedTracker.consume())
    }
    var downloadPath = pumpEventsAndWaitForFuture(myMockFileManager!!.openFileInEditorTracker.consume())
    assertTrue(
      FileUtil.toSystemIndependentName(downloadPath.toString())
        .endsWith("device-explorer-temp/TestDevice-1/file1.txt")
    )

    // Change the setting to an alternate directory, ensure that changing during runtime works
    val changedPath = FileUtil.createTempDirectory("device-explorer-temp-2", "", true).toPath()
    Mockito.`when`(myDownloadLocationSupplier!!.get()).thenReturn(changedPath)

    // Now try the alternate location
    downloadFile(myFile1) {
      val path = getFileEntryPath(myFile1!!)
      val pathBounds = myMockView!!.tree.getPathBounds(path)!!

      // Fire double-click event
      fireDoubleClick(myMockView!!.tree, pathBounds.x, pathBounds.y)
      pumpEventsAndWaitForFuture(myMockView!!.openNodesInEditorInvokedTracker.consume())
    }
    downloadPath = pumpEventsAndWaitForFuture(myMockFileManager!!.openFileInEditorTracker.consume())
    assertTrue(
      FileUtil.toSystemIndependentName(downloadPath.toString())
        .endsWith("device-explorer-temp-2/TestDevice-1/file1.txt")
    )
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun testDownloadFileFailure() {
    // Prepare
    val controller = createController()

    // Act
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)
    val errorMessage = "<Expected test error>"
    myDevice1!!.downloadError = RuntimeException(errorMessage)

    // Select node
    myMockView!!.tree.selectionPath = getFileEntryPath(myFile1!!)

    // Send a VK_ENTER key event
    fireEnterKey(myMockView!!.tree)
    pumpEventsAndWaitForFuture(myMockView!!.openNodesInEditorInvokedTracker.consume())
    pumpEventsAndWaitForFuture(myMockFileManager!!.downloadFileEntryTracker.consume())
    val t = pumpEventsAndWaitForFutureException(myMockFileManager!!.downloadFileEntryCompletionTracker.consume())
    val loadingError = pumpEventsAndWaitForFuture(myMockView!!.reportErrorRelatedToNodeTracker.consume())

    // Assert
    assertNotNull(t)
    assertNotNull(loadingError)
    assertTrue(loadingError.contains(errorMessage))
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun testChangeActiveDevice() {
    // Prepare
    val controller = createController()

    // Act
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)
    myMockView!!.deviceCombo.selectedItem = myDevice2

    // Assert
    checkMockViewActiveDevice(myDevice2)
  }

  @Throws(Exception::class)
  fun testChangeActiveDeviceDuringFileDownload() {
    // Prepare
    val controller = createController()
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)

    // Start file download.
    downloadFile(myFile1) {

      // Send a VK_ENTER key event.
      fireEnterKey(myMockView!!.tree)
      pumpEventsAndWaitForFuture(myMockFileManager!!.openFileInEditorTracker.consume())
    }
    myMockView!!.startTreeBusyIndicatorTacker.clear()
    myMockView!!.stopTreeBusyIndicatorTacker.clear()
    myMockView!!.reportMessageRelatedToNodeTracker.clear()

    // Change selected device.
    myMockView!!.deviceCombo.selectedItem = myDevice2

    // Check that the view shows the second device.
    checkMockViewActiveDevice(myDevice2)
    // Check that the download from the first device finished successfully.
    pumpEventsAndWaitForFuture(myMockView!!.openNodesInEditorInvokedTracker.consume())
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun testUpdateActiveDeviceState() {
    // Prepare
    val controller = createController()
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)
    val model: TreeModel? = myModel!!.treeModel

    // Act
    injectRepaintManagerMock()
    Arrays.stream(myMockService!!.listeners).forEach { l: DeviceFileSystemServiceListener ->
      l.deviceUpdated(
        myDevice1!!
      )
    }
    pumpEventsAndWaitForFuture(myMockView!!.deviceUpdatedTracker.consume())

    // Assert
    // Check there was no update to the underlying model, and that only
    // the combo box UI has been invalidated.
    assertEquals(myDevice1, myModel!!.activeDevice)
    assertEquals(model, myModel!!.treeModel)
    Mockito.verify(myMockRepaintManager).addDirtyRegion(
      myMockView!!.deviceCombo,
      0,
      0,
      myMockView!!.deviceCombo.width,
      myMockView!!.deviceCombo.height
    )
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun testSetActiveDeviceFromSerialNumber() {
    // Prepare
    val controller = createController()

    // Act
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)
    controller.selectActiveDevice(myDevice2!!.deviceSerialNumber)

    // Assert
    checkMockViewActiveDevice(myDevice2)
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun testSetActiveDeviceFromSerialNumberNotFound() {
    // Prepare
    val controller = createController()

    // Act
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)
    controller.selectActiveDevice("Nonexistent")
    val selectError = pumpEventsAndWaitForFuture(myMockView!!.reportErrorGenericTracker.consume())

    // Assert
    assertNotNull(selectError)
    assertTrue(selectError.contains("Unable to find device with serial number"))
    checkMockViewActiveDevice(myDevice1)
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun testAddDevice() {
    // Prepare
    val controller = createController()

    // Act
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)
    val futureItemAdded = SettableFuture.create<Void>()
    myMockView!!.deviceCombo.model.addListDataListener(object : ListDataListener {
      override fun intervalAdded(e: ListDataEvent) {
        futureItemAdded.set(null)
      }

      override fun intervalRemoved(e: ListDataEvent) {}
      override fun contentsChanged(e: ListDataEvent) {}
    })
    val newFileSystem: DeviceFileSystem = myMockService!!.addDevice("TestDevice-3")
    val addedFileSystem = pumpEventsAndWaitForFuture(myMockView!!.deviceAddedTracker.consume())
    pumpEventsAndWaitForFuture(futureItemAdded)

    // Assert
    assertEquals(newFileSystem, addedFileSystem)
    assertEquals(3, myMockView!!.deviceCombo.itemCount)
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun testRemoveActiveDevice() {
    // Prepare
    val controller = createController()

    // Act
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)
    val futureItemRemoved = SettableFuture.create<Void>()
    myMockView!!.deviceCombo.model.addListDataListener(object : ListDataListener {
      override fun intervalAdded(e: ListDataEvent) {}
      override fun intervalRemoved(e: ListDataEvent) {
        futureItemRemoved.set(null)
      }

      override fun contentsChanged(e: ListDataEvent) {}
    })
    assertTrue(myMockService!!.removeDevice(myDevice1!!))
    pumpEventsAndWaitForFuture(myMockView!!.deviceRemovedTracker.consume())
    pumpEventsAndWaitForFuture(futureItemRemoved)

    // Assert
    assertEquals(1, myMockView!!.deviceCombo.itemCount)
    checkMockViewActiveDevice(myDevice2)
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun testFileSystemTree_ContextMenu_Items_Present() {
    // Prepare
    val controller = createController()

    // Act
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)

    // Assert
    val actionGroup = myMockView!!.fileTreeActionGroup
    assertEquals(9, actionGroup.getChildren(null).size)
    val subGroup = getSubGroup(actionGroup, "New")
    assertNotNull(subGroup)
    assertEquals(2, subGroup!!.getChildren(null).size)

    // Act: Call "update" on each action, just to make sure the code is covered
    myMockView!!.tree.selectionPath = getFileEntryPath(myFile1!!)
    val actions = Arrays.asList(*actionGroup.getChildren(null))
    val e = createContentMenuItemEvent()
    actions.forEach(java.util.function.Consumer { x: AnAction -> x.update(e) })
  }

  @Throws(Exception::class)
  fun testFileSystemTree_ContextMenu_Open_Works() {
    downloadFile(myFile1) {
      val actionGroup = myMockView!!.fileTreeActionGroup
      val action = getActionByText(actionGroup, "Open")
      assertNotNull(action)
      val e = createContentMenuItemEvent()
      action!!.update(e)
      // Assert
      assertTrue(e.presentation.isVisible)
      assertTrue(e.presentation.isEnabled)

      // Act
      action.actionPerformed(e)

      // Assert
      pumpEventsAndWaitForFuture(myMockView!!.openNodesInEditorInvokedTracker.consume())
    }
    pumpEventsAndWaitForFuture(myMockFileManager!!.openFileInEditorTracker.consume())
  }

  @Throws(Exception::class)
  fun testFileSystemTree_ContextMenu_SaveFileAs_Works() {
    val tempFile = FileUtil.createTempFile("foo", "bar")
    downloadFile(myFile1) {

      // Prepare
      // The "Save As" dialog does not work in headless mode, so we register a custom
      // component that simply returns the tempFile we created above.
      val factory: FileChooserFactoryImpl = object : FileChooserFactoryImpl() {
        override fun createSaveFileDialog(descriptor: FileSaverDescriptor, project: Project?): FileSaverDialog {
          return@downloadFile object : FileSaverDialog {
            override fun save(baseDir: VirtualFile?, filename: String?): VirtualFileWrapper? {
              return@downloadFile VirtualFileWrapper(tempFile)
            }

            override fun save(baseDir: Path?, filename: String?): VirtualFileWrapper? {
              return@downloadFile VirtualFileWrapper(tempFile)
            }
          }
        }
      }
      ApplicationManager.getApplication().replaceService(FileChooserFactory::class.java, factory, testRootDisposable)

      // Invoke "Save As..." content menu
      val actionGroup = myMockView!!.fileTreeActionGroup
      val action = getActionByText(actionGroup, "Save As...")
      assertNotNull(action)
      val e = createContentMenuItemEvent()
      action!!.update(e)

      // Assert
      assertTrue(e.presentation.isVisible)
      assertTrue(e.presentation.isEnabled)

      // Act
      action.actionPerformed(e)

      // Assert
      pumpEventsAndWaitForFuture(myMockView!!.saveNodesAsTracker.consume())
    }

    // Assert
    assertTrue(tempFile.exists())
    assertEquals(200000, tempFile.length())
  }

  @Throws(Exception::class)
  fun testFileSystemTree_ContextMenu_SaveDirectoryAs_Works() {
    // Prepare
    val controller = createController()
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)

    // Act
    // Select node
    val file1Path = getFileEntryPath(myFoo!!)
    myMockView!!.tree.selectionPath = file1Path
    val actionGroup = myMockView!!.fileTreeActionGroup
    val action = getActionByText(actionGroup, "Save As...")
    assertNotNull(action)
    val e = createContentMenuItemEvent()
    action!!.update(e)

    // Assert
    assertTrue(e.presentation.isVisible)
    assertTrue(e.presentation.isEnabled)

    // Prepare
    // The "Choose file" dialog does not work in headless mode, so we register a custom
    // component that simply returns the tempFile we created above.
    val tempDirectory = FileUtil.createTempDirectory("saveAsDir", "")
    myDevice1!!.downloadChunkSize = 1000 // download chunks of 1000 bytes at a time
    myDevice1!!.downloadChunkIntervalMillis = 10 // wait 10 millis between each 1000 bytes chunk
    val factory: FileChooserFactoryImpl = object : FileChooserFactoryImpl() {
      override fun createPathChooser(
        descriptor: FileChooserDescriptor,
        project: Project?,
        parent: Component?
      ): PathChooserDialog {
        return PathChooserDialog { toSelect: VirtualFile?, callback: com.intellij.util.Consumer<in List<VirtualFile?>?> ->
          val list = listOf(
            VirtualFileWrapper(tempDirectory).virtualFile
          )
          callback.consume(list)
        }
      }
    }
    ApplicationManager.getApplication().replaceService(FileChooserFactory::class.java, factory, testRootDisposable)

    // Act
    myMockView!!.startTreeBusyIndicatorTacker.clear()
    myMockView!!.stopTreeBusyIndicatorTacker.clear()
    myMockView!!.reportMessageRelatedToNodeTracker.clear()
    action.actionPerformed(e)

    // Assert
    pumpEventsAndWaitForFuture(myMockView!!.startTreeBusyIndicatorTacker.consume())
    pumpEventsAndWaitForFuture(myMockView!!.stopTreeBusyIndicatorTacker.consume())
    val summaryMessage = pumpEventsAndWaitForFuture(myMockView!!.reportMessageRelatedToNodeTracker.consume())
    assertNotNull(summaryMessage)
    println("SaveAs message: $summaryMessage")
    assertTrue(summaryMessage.contains("Successfully downloaded"))
    val files = tempDirectory.listFiles()
    assertNotNull(files)
    val createdFiles = Arrays.asList(*files)
    assertEquals(4, createdFiles.size)
    assertTrue(createdFiles.stream().anyMatch { x: File -> x.name == myFooFile1!!.name })
    assertTrue(createdFiles.stream().anyMatch { x: File -> x.name == myFooFile2!!.name })
    assertTrue(createdFiles.stream().anyMatch { x: File -> x.name == myFooLink1!!.name })
    assertTrue(createdFiles.stream().anyMatch { x: File -> x.name == myFooDir!!.name })
  }

  @Throws(Exception::class)
  fun testFileSystemTree_ContextMenu_SaveMultipleFilesAs_Works() {
    // Prepare
    val controller = createController()
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)

    // Act
    // Select nodes
    expandEntry(myFoo!!)
    myMockView!!.tree.selectionPath = getFileEntryPath(myFooFile1!!)
    myMockView!!.tree.addSelectionPath(getFileEntryPath(myFooFile2!!))
    val actionGroup = myMockView!!.fileTreeActionGroup
    val action = getActionByText(actionGroup, "Save To...")
    assertNotNull(action)
    val e = createContentMenuItemEvent()
    action!!.update(e)

    // Assert
    assertTrue(e.presentation.isVisible)
    assertTrue(e.presentation.isEnabled)

    // Prepare
    // The "Choose file" dialog does not work in headless mode, so we register a custom
    // component that simply returns the tempFile we created above.
    val tempDirectory = FileUtil.createTempDirectory("saveAsDir", "")
    myDevice1!!.downloadChunkSize = 1000 // download chunks of 1000 bytes at a time
    myDevice1!!.downloadChunkIntervalMillis = 10 // wait 10 millis between each 1000 bytes chunk
    val factory: FileChooserFactoryImpl = object : FileChooserFactoryImpl() {
      override fun createPathChooser(
        descriptor: FileChooserDescriptor,
        project: Project?,
        parent: Component?
      ): PathChooserDialog {
        return PathChooserDialog { toSelect: VirtualFile?, callback: com.intellij.util.Consumer<in List<VirtualFile?>?> ->
          val list = listOf(
            VirtualFileWrapper(tempDirectory).virtualFile
          )
          callback.consume(list)
        }
      }
    }
    ApplicationManager.getApplication().replaceService(FileChooserFactory::class.java, factory, testRootDisposable)

    // Act
    myMockView!!.startTreeBusyIndicatorTacker.clear()
    myMockView!!.stopTreeBusyIndicatorTacker.clear()
    myMockView!!.reportMessageRelatedToNodeTracker.clear()
    action.actionPerformed(e)

    // Assert
    pumpEventsAndWaitForFuture(myMockView!!.startTreeBusyIndicatorTacker.consume())
    pumpEventsAndWaitForFuture(myMockView!!.stopTreeBusyIndicatorTacker.consume())
    val summaryMessage = pumpEventsAndWaitForFuture(myMockView!!.reportMessageRelatedToNodeTracker.consume())
    assertNotNull(summaryMessage)
    println("SaveAs message: $summaryMessage")
    assertTrue(summaryMessage.contains("Successfully downloaded"))
    val files = tempDirectory.listFiles()
    assertNotNull(files)
    val createdFiles = Arrays.asList(*files)
    assertEquals(2, createdFiles.size)
    assertTrue(createdFiles.stream().anyMatch { x: File -> x.name == myFooFile1!!.name })
    assertTrue(createdFiles.stream().anyMatch { x: File -> x.name == myFooFile2!!.name })
  }

  @Throws(Exception::class)
  fun testFileSystemTree_ContextMenu_SaveDirectoryAs_ShowsProblems() {
    // Prepare
    val controller = createController()
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)

    // Act
    // Select node
    val file1Path = getFileEntryPath(myFoo!!)
    myMockView!!.tree.selectionPath = file1Path
    val actionGroup = myMockView!!.fileTreeActionGroup
    val action = getActionByText(actionGroup, "Save As...")
    assertNotNull(action)
    val e = createContentMenuItemEvent()
    action!!.update(e)

    // Assert
    assertTrue(e.presentation.isVisible)
    assertTrue(e.presentation.isEnabled)

    // Prepare
    // The "Choose file" dialog does not work in headless mode, so we register a custom
    // component that simply returns the tempFile we created above.
    val tempDirectory = FileUtil.createTempDirectory("saveAsDir", "")
    myDevice1!!.downloadChunkSize = 1000 // download chunks of 1000 bytes at a time
    myDevice1!!.downloadChunkIntervalMillis = 10 // wait 10 millis between each 1000 bytes chunk
    val downloadErrorMessage = "[test] Error downloading file"
    myDevice1!!.downloadError = Exception(downloadErrorMessage)
    val factory: FileChooserFactoryImpl = object : FileChooserFactoryImpl() {
      override fun createPathChooser(
        descriptor: FileChooserDescriptor,
        project: Project?,
        parent: Component?
      ): PathChooserDialog {
        return PathChooserDialog { toSelect: VirtualFile?, callback: com.intellij.util.Consumer<in List<VirtualFile?>?> ->
          val list = listOf(
            VirtualFileWrapper(tempDirectory).virtualFile
          )
          callback.consume(list)
        }
      }
    }
    ApplicationManager.getApplication().replaceService(FileChooserFactory::class.java, factory, testRootDisposable)

    // Act
    myMockView!!.startTreeBusyIndicatorTacker.clear()
    myMockView!!.stopTreeBusyIndicatorTacker.clear()
    myMockView!!.reportMessageRelatedToNodeTracker.clear()
    action.actionPerformed(e)

    // Assert
    pumpEventsAndWaitForFuture(myMockView!!.startTreeBusyIndicatorTacker.consume())
    pumpEventsAndWaitForFuture(myMockView!!.stopTreeBusyIndicatorTacker.consume())
    val summaryMessage = pumpEventsAndWaitForFuture(myMockView!!.reportErrorRelatedToNodeTracker.consume())
    assertNotNull(summaryMessage)
    println("SaveAs message: $summaryMessage")
    assertTrue(summaryMessage.contains("There were errors"))
    assertTrue(summaryMessage.contains(downloadErrorMessage))

    // Note: Even though downloading files failed, empty directories are still created during
    //       a directory tree download, in this case we should have exactly one.
    val files = tempDirectory.listFiles()
    assertNotNull(files)
    val createdFiles = Arrays.asList(*files)
    assertEquals(1, createdFiles.size)
    assertTrue(createdFiles.stream().anyMatch { x: File -> x.name == myFooDir!!.name })
  }

  @Throws(Exception::class)
  fun testFileSystemTree_ContextMenu_New_IsHiddenForFiles() {
    // Prepare
    val controller = createController()
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)

    // Act
    myMockView!!.tree.selectionPath = getFileEntryPath(myFile1!!)

    // Assert
    checkContextMenuItemVisible("New/File", false)
    checkContextMenuItemVisible("New/Directory", false)
  }

  @Throws(Exception::class)
  fun testFileSystemTree_ContextMenu_New_IsVisibleForDirectories() {
    // Prepare
    val controller = createController()
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)

    // Act
    expandEntry(myFoo!!)
    myMockView!!.tree.selectionPath = getFileEntryPath(myFooDir!!)

    // Assert
    checkContextMenuItemVisible("New/File", true)
    checkContextMenuItemVisible("New/Directory", true)
  }

  @Throws(Exception::class)
  fun testFileSystemTree_ContextMenu_NewFile_Works() {
    // Prepare
    val controller = createController()
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)
    expandEntry(myFoo!!)
    val fooDirPath = getFileEntryPath(myFooDir!!)
    myMockView!!.tree.selectionPath = fooDirPath
    val fooDirExpandedFuture = createNodeExpandedFuture(
      myFooDir!!
    )
    val newFileName = "foobar.txt"
    replaceTestInputDialog(newFileName)

    // Act
    val action = getContextMenuAction("New/File")
    val e = createContentMenuItemEvent()
    action.update(e)
    action.actionPerformed(e)

    // Assert
    pumpEventsAndWaitForFuture(fooDirExpandedFuture)

    // Look for the new file entry in the tree view
    val newChild = enumerationAsList<Any>((fooDirPath.lastPathComponent as DeviceFileEntryNode).children())
      .stream()
      .filter { x: Any? -> x is DeviceFileEntryNode }
      .map { x: Any? -> x as DeviceFileEntryNode? }
      .filter { x: DeviceFileEntryNode? -> newFileName == x!!.entry.name && x.entry.isFile }
      .findFirst()
      .orElse(null)
    assertNotNull(newChild)
  }

  @Throws(Exception::class)
  fun testFileSystemTree_ContextMenu_NewDirectory_Works() {
    // Prepare
    val controller = createController()
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)
    expandEntry(myFoo!!)
    val fooDirPath = getFileEntryPath(myFooDir!!)
    myMockView!!.tree.selectionPath = fooDirPath
    val fooDirExpandedFuture = createNodeExpandedFuture(
      myFooDir!!
    )
    val newDirectoryName = "foobar.txt"
    replaceTestInputDialog(newDirectoryName)

    // Act
    val action = getContextMenuAction("New/Directory")
    val e = createContentMenuItemEvent()
    action.update(e)
    action.actionPerformed(e)

    // Assert
    pumpEventsAndWaitForFuture(fooDirExpandedFuture)

    // Look for the new file entry in the tree view
    val newChild = enumerationAsList<Any>((fooDirPath.lastPathComponent as DeviceFileEntryNode).children())
      .stream()
      .filter { x: Any? -> x is DeviceFileEntryNode }
      .map { x: Any? -> x as DeviceFileEntryNode? }
      .filter { x: DeviceFileEntryNode? -> newDirectoryName == x!!.entry.name && x.entry.isDirectory }
      .findFirst()
      .orElse(null)
    assertNotNull(newChild)
  }

  @Throws(Exception::class)
  fun testFileSystemTree_ContextMenu_NewDirectory_ExistingPath_Fails() {
    // Prepare
    val controller = createController()
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)
    val fooPath = getFileEntryPath(myFoo!!)
    myMockView!!.tree.selectionPath = fooPath
    val newDirectoryName = myFooDir!!.name // Existing name to create conflict
    replaceTestInputDialog(newDirectoryName)
    val futureMessageDialog = SettableFuture.create<String>()
    replaceTestDialog { s: String? ->
      futureMessageDialog.set(s)

      // Simulate a "Cancel" dialog in the "New Folder Name" dialog, since the controller
      // shows the "New Folder Name" dialog as long as an error is detected when
      // creating the new folder.
      replaceTestInputDialog(null)
      0
    }

    // Act
    val action = getContextMenuAction("New/Directory")
    val e = createContentMenuItemEvent()
    action.update(e)
    action.actionPerformed(e)

    // Assert
    val message = pumpEventsAndWaitForFuture(futureMessageDialog)
    assertNotNull(message)
    assertTrue(message.contains(UIBundle.message("create.new.folder.could.not.create.folder.error.message", newDirectoryName)))

    // Ensure entry does not exist in tree view
    val newChild = enumerationAsList<Any>((fooPath.lastPathComponent as DeviceFileEntryNode).children())
      .stream()
      .filter { x: Any? -> x is DeviceFileEntryNode }
      .map { x: Any? -> x as DeviceFileEntryNode? }
      .filter { x: DeviceFileEntryNode? -> newDirectoryName == x!!.entry.name && x.entry.isDirectory }
      .findFirst()
      .orElse(null)
    assertNull(newChild)
  }

  @Throws(Exception::class)
  fun testFileSystemTree_ContextMenu_CopyPath_Works() {
    // Prepare
    val controller = createController()

    // Act
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)
    myMockView!!.tree.selectionPath = getFileEntryPath(myFile1!!)
    val actionGroup = myMockView!!.fileTreeActionGroup
    val action = getActionByText(actionGroup, "Copy Path")
    assertNotNull(action)
    val e = createContentMenuItemEvent()
    action!!.update(e)

    // Assert
    assertTrue(e.presentation.isVisible)
    assertTrue(e.presentation.isEnabled)

    // Act
    action.actionPerformed(e)
    pumpEventsAndWaitForFuture(myMockView!!.copyNodePathsTracker.consume())

    // Assert
    val contents = CopyPasteManager.getInstance().contents
    assertNotNull(contents)
    assertEquals("/" + myFile1!!.name, contents!!.getTransferData(DataFlavor.stringFlavor))
  }

  @Throws(Exception::class)
  fun testFileSystemTree_ContextMenu_CopyPaths_Works() {
    // Prepare
    val controller = createController()

    // Act
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)
    myMockView!!.tree.selectionPath = getFileEntryPath(myFile1!!)
    myMockView!!.tree.addSelectionPath(getFileEntryPath(myFile2!!))
    val actionGroup = myMockView!!.fileTreeActionGroup
    val action = getActionByText(actionGroup, "Copy Paths")
    assertNotNull(action)
    val e = createContentMenuItemEvent()
    action!!.update(e)

    // Assert
    assertTrue(e.presentation.isVisible)
    assertTrue(e.presentation.isEnabled)

    // Act
    action.actionPerformed(e)
    pumpEventsAndWaitForFuture(myMockView!!.copyNodePathsTracker.consume())

    // Assert
    val contents = CopyPasteManager.getInstance().contents
    assertNotNull(contents)
    assertEquals(
      """
  ${myFile1!!.fullPath}
  ${myFile2!!.fullPath}
  """.trimIndent(), contents!!.getTransferData(DataFlavor.stringFlavor)
    )
  }

  @Throws(Exception::class)
  fun testFileSystemTree_ContextMenu_Delete_Works() {
    // Prepare
    val controller = createController()

    // Act
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)
    expandEntry(myFoo!!)
    myMockView!!.tree.selectionPath = getFileEntryPath(myFooFile1!!)
    myMockView!!.tree.addSelectionPath(getFileEntryPath(myFooFile2!!))
    val futureTreeChanged = createNodeExpandedFuture(myFoo!!)
    val actionGroup = myMockView!!.fileTreeActionGroup
    val action = getActionByText(actionGroup, "Delete...")
    assertNotNull(action)
    val e = createContentMenuItemEvent()
    action!!.update(e)
    replaceTestDialog { s: String? -> 0 } // "OK" button

    // Assert
    assertTrue(e.presentation.isVisible)
    assertTrue(e.presentation.isEnabled)

    // Act
    action.actionPerformed(e)
    pumpEventsAndWaitForFuture(myMockView!!.deleteNodesTracker.consume())
    pumpEventsAndWaitForFuture(futureTreeChanged)

    // Assert
    val fooNode = DeviceFileEntryNode.fromNode(getFileEntryPath(myFoo!!).lastPathComponent)
    assertNotNull(fooNode)
    assertEquals(2, fooNode!!.childCount)
  }

  @Throws(Exception::class)
  fun testFileSystemTree_ContextMenu_Delete_ShowProblems() {
    // Prepare
    val controller = createController()

    // Act
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)
    expandEntry(myFoo!!)
    myFooFile1!!.deleteError = AdbShellCommandException("Error deleting file")
    myMockView!!.tree.selectionPath = getFileEntryPath(myFooFile1!!)
    myMockView!!.tree.addSelectionPath(getFileEntryPath(myFooFile2!!))
    val futureTreeChanged = createNodeExpandedFuture(myFoo!!)
    val actionGroup = myMockView!!.fileTreeActionGroup
    val action = getActionByText(actionGroup, "Delete...")
    assertNotNull(action)
    val e = createContentMenuItemEvent()
    action!!.update(e)
    val showProblemsFuture = SettableFuture.create<Void>()
    replaceTestDialog { s: String ->
      if (s.contains("Could not erase")) {
        showProblemsFuture.set(null)
      }
      0 // "OK" button
    }

    // Assert
    assertTrue(e.presentation.isVisible)
    assertTrue(e.presentation.isEnabled)

    // Act
    action.actionPerformed(e)
    pumpEventsAndWaitForFuture(myMockView!!.deleteNodesTracker.consume())
    pumpEventsAndWaitForFuture(showProblemsFuture)
    pumpEventsAndWaitForFuture(futureTreeChanged)

    // Assert
    // One entry has been deleted
    val fooNode = DeviceFileEntryNode.fromNode(getFileEntryPath(myFoo!!).lastPathComponent)
    assertNotNull(fooNode)
    assertEquals(3, fooNode!!.childCount)
  }

  @Throws(Exception::class)
  fun testFileSystemTree_ContextMenu_Upload_SingleFile_Works() {
    // Prepare
    val controller = createController()
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)
    expandEntry(myFoo!!)
    myMockView!!.tree.selectionPath = getFileEntryPath(myFoo!!)
    val futureTreeChanged = createNodeExpandedFuture(myFoo!!)
    val actionGroup = myMockView!!.fileTreeActionGroup
    val action = getActionByText(actionGroup, "Upload...")
    assertNotNull(action)
    val e = createContentMenuItemEvent()
    action!!.update(e)

    // The "Choose file" dialog does not work in headless mode, so we register a custom
    // component that simply returns the tempFile we created above.
    val tempFile = FileUtil.createTempFile("foo", "bar.txt")
    Files.write(tempFile.toPath(), ByteArray(10000))
    myDevice1!!.uploadChunkSize = 500
    myDevice1!!.uploadChunkIntervalMillis = 20
    val factory: FileChooserFactoryImpl = object : FileChooserFactoryImpl() {
      override fun createPathChooser(
        descriptor: FileChooserDescriptor,
        project: Project?,
        parent: Component?
      ): PathChooserDialog {
        return PathChooserDialog { toSelect: VirtualFile?, callback: com.intellij.util.Consumer<in List<VirtualFile?>?> ->
          val files = Stream.of(tempFile)
            .map { x: File? ->
              VirtualFileWrapper(
                x!!
              ).virtualFile
            }
            .collect(Collectors.toList())
          callback.consume(files)
        }
      }
    }
    ApplicationManager.getApplication().replaceService(FileChooserFactory::class.java, factory, testRootDisposable)

    // Assert
    assertTrue(e.presentation.isVisible)
    assertTrue(e.presentation.isEnabled)

    // Act
    myMockView!!.startTreeBusyIndicatorTacker.clear()
    myMockView!!.stopTreeBusyIndicatorTacker.clear()
    action.actionPerformed(e)
    pumpEventsAndWaitForFuture(myMockView!!.startTreeBusyIndicatorTacker.consume())
    pumpEventsAndWaitForFuture(myMockView!!.uploadFilesTracker.consume())
    pumpEventsAndWaitForFuture(futureTreeChanged)
    pumpEventsAndWaitForFuture(myMockView!!.stopTreeBusyIndicatorTacker.consume())
    pumpEventsAndWaitForFuture(myMockView!!.reportMessageRelatedToNodeTracker.consume())

    // Assert
    // One node has been added
    val fooNode = DeviceFileEntryNode.fromNode(getFileEntryPath(myFoo!!).lastPathComponent)
    assertNotNull(fooNode)
    assertEquals(5, fooNode!!.childCount)
  }

  @Throws(Exception::class)
  fun testFileSystemTree_DropFile_SingleFile_Works() {
    // Prepare
    val controller = createController()
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)
    expandEntry(myFoo!!)
    val path = getFileEntryPath(myFoo!!)
    val bounds = myMockView!!.tree.ui.getPathBounds(myMockView!!.tree, path)
    myMockView!!.tree.selectionPath = path
    val futureTreeChanged = createNodeExpandedFuture(myFoo!!)
    val tempFile = FileUtil.createTempFile("foo", "bar.txt")
    Files.write(tempFile.toPath(), ByteArray(10000))
    myDevice1!!.uploadChunkSize = 500
    myDevice1!!.uploadChunkIntervalMillis = 20
    val handler = myMockView!!.tree.transferHandler
    assertFalse(handler.canImport(myMockView!!.tree, arrayOf(DataFlavor.stringFlavor)))
    assertTrue(handler.canImport(myMockView!!.tree, arrayOf(DataFlavor.javaFileListFlavor)))
    val transferable: Transferable = object : Transferable {
      override fun getTransferDataFlavors(): Array<DataFlavor> {
        return arrayOf(DataFlavor.javaFileListFlavor)
      }

      override fun isDataFlavorSupported(flavor: DataFlavor): Boolean {
        return flavor.equals(DataFlavor.javaFileListFlavor)
      }

      override fun getTransferData(flavor: DataFlavor): Any {
        return Arrays.asList(tempFile)
      }
    }
    val support = Mockito.mock(TransferSupport::class.java)
    val location = Mockito.mock(TransferHandler.DropLocation::class.java)
    Mockito.`when`(location.dropPoint).thenReturn(Point(bounds.centerX.toInt(), bounds.centerY.toInt()))
    Mockito.`when`(support.transferable).thenReturn(transferable)
    Mockito.`when`(support.dropLocation).thenReturn(location)

    // Act
    myMockView!!.startTreeBusyIndicatorTacker.clear()
    myMockView!!.stopTreeBusyIndicatorTacker.clear()
    assertTrue(handler.importData(support))
    pumpEventsAndWaitForFuture(myMockView!!.startTreeBusyIndicatorTacker.consume())
    pumpEventsAndWaitForFuture(myMockView!!.uploadFilesTracker.consume())
    pumpEventsAndWaitForFuture(futureTreeChanged)
    pumpEventsAndWaitForFuture(myMockView!!.stopTreeBusyIndicatorTacker.consume())
    pumpEventsAndWaitForFuture(myMockView!!.reportMessageRelatedToNodeTracker.consume())

    // Assert
    // One node has been added
    val fooNode = DeviceFileEntryNode.fromNode(getFileEntryPath(myFoo!!).lastPathComponent)
    assertNotNull(fooNode)
    assertEquals(5, fooNode!!.childCount)
  }

  @Throws(Exception::class)
  fun testFileSystemTree_ContextMenu_Upload_DirectoryAndFile_Works() {
    // Prepare
    val controller = createController()

    // Act
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)
    expandEntry(myFoo!!)
    myMockView!!.tree.selectionPath = getFileEntryPath(myFoo!!)
    val futureTreeChanged = createNodeExpandedFuture(myFoo!!)
    val actionGroup = myMockView!!.fileTreeActionGroup
    val action = getActionByText(actionGroup, "Upload...")
    assertNotNull(action)
    val e = createContentMenuItemEvent()
    action!!.update(e)
    val tempFile = FileUtil.createTempFile("foo", "bar.txt")
    Files.write(tempFile.toPath(), ByteArray(10000))
    val tempDirectory = FileUtil.createTempDirectory("foo", "dir")
    val foobar2File = FileUtil.createTempFile(tempDirectory, "foobar2", ".txt")
    Files.write(foobar2File.toPath(), ByteArray(10000))
    val foobar3File = FileUtil.createTempFile(tempDirectory, "foobar3", ".txt")
    Files.write(foobar3File.toPath(), ByteArray(10000))
    val foobar4File = FileUtil.createTempFile(tempDirectory, "foobar4", ".txt")
    Files.write(foobar4File.toPath(), ByteArray(10000))

    // The "Choose file" dialog does not work in headless mode, so we register a custom
    // component that simply returns the tempFile we created above.
    val factory: FileChooserFactoryImpl = object : FileChooserFactoryImpl() {
      override fun createPathChooser(
        descriptor: FileChooserDescriptor,
        project: Project?,
        parent: Component?
      ): PathChooserDialog {
        return PathChooserDialog { toSelect: VirtualFile?, callback: com.intellij.util.Consumer<in List<VirtualFile?>?> ->
          val files = Stream.of(tempFile, tempDirectory)
            .map { x: File? ->
              VirtualFileWrapper(
                x!!
              ).virtualFile
            }
            .collect(Collectors.toList())
          callback.consume(files)
        }
      }
    }
    ApplicationManager.getApplication().replaceService(FileChooserFactory::class.java, factory, testRootDisposable)

    // Assert
    assertTrue(e.presentation.isVisible)
    assertTrue(e.presentation.isEnabled)

    // Act
    myMockView!!.startTreeBusyIndicatorTacker.clear()
    myMockView!!.stopTreeBusyIndicatorTacker.clear()
    action.actionPerformed(e)
    pumpEventsAndWaitForFuture(myMockView!!.startTreeBusyIndicatorTacker.consume())
    pumpEventsAndWaitForFuture(myMockView!!.uploadFilesTracker.consume())
    pumpEventsAndWaitForFuture(futureTreeChanged)
    pumpEventsAndWaitForFuture(myMockView!!.stopTreeBusyIndicatorTacker.consume())
    pumpEventsAndWaitForFuture(myMockView!!.reportMessageRelatedToNodeTracker.consume())

    // Assert
    // Two nodes have been added
    val fooNode = DeviceFileEntryNode.fromNode(getFileEntryPath(myFoo!!).lastPathComponent)
    assertNotNull(fooNode)
    assertEquals(6, fooNode!!.childCount)
  }

  @Throws(Exception::class)
  fun testFileSystemTree_ContextMenu_Upload_ShowsProblems() {
    // Prepare
    val controller = createController()

    // Act
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)
    expandEntry(myFoo!!)
    myMockView!!.tree.selectionPath = getFileEntryPath(myFoo!!)
    val futureTreeChanged = createNodeExpandedFuture(myFoo!!)
    val actionGroup = myMockView!!.fileTreeActionGroup
    val action = getActionByText(actionGroup, "Upload...")
    assertNotNull(action)
    val e = createContentMenuItemEvent()
    action!!.update(e)

    // Create 15 temporary files, so that we hit the "limit # of problems to display to 10" code path
    val tempFiles = IntStream.range(0, 15)
      .mapToObj { i: Int ->
        try {
          return@mapToObj FileUtil.createTempFile("foo", ".txt")
        } catch (ignored: IOException) {
          return@mapToObj null
        }
      }
      .filter { obj: File? -> Objects.nonNull(obj) }
      .collect(Collectors.toList())

    // The "Choose file" dialog does not work in headless mode, so we register a custom
    // component that simply returns the tempFile we created above.
    val factory: FileChooserFactoryImpl = object : FileChooserFactoryImpl() {
      override fun createPathChooser(
        descriptor: FileChooserDescriptor,
        project: Project?,
        parent: Component?
      ): PathChooserDialog {
        return PathChooserDialog { toSelect: VirtualFile?, callback: com.intellij.util.Consumer<in List<VirtualFile?>?> ->
          val files = tempFiles.stream()
            .map { x: File? ->
              VirtualFileWrapper(
                x!!
              ).virtualFile
            }
            .collect(Collectors.toList())
          callback.consume(files)
        }
      }
    }
    ApplicationManager.getApplication().replaceService(FileChooserFactory::class.java, factory, testRootDisposable)

    // Ensure file upload fails
    myDevice1!!.uploadError = AdbShellCommandException("Permission error")

    // Assert
    assertTrue(e.presentation.isVisible)
    assertTrue(e.presentation.isEnabled)

    // Act
    myMockView!!.startTreeBusyIndicatorTacker.clear()
    myMockView!!.stopTreeBusyIndicatorTacker.clear()
    action.actionPerformed(e)
    pumpEventsAndWaitForFuture(myMockView!!.startTreeBusyIndicatorTacker.consume())
    pumpEventsAndWaitForFuture(myMockView!!.uploadFilesTracker.consume())
    pumpEventsAndWaitForFuture(futureTreeChanged)
    pumpEventsAndWaitForFuture(myMockView!!.stopTreeBusyIndicatorTacker.consume())
    pumpEventsAndWaitForFuture(myMockView!!.reportErrorRelatedToNodeTracker.consume())

    // Assert
    // No node has been added
    val fooNode = DeviceFileEntryNode.fromNode(getFileEntryPath(myFoo!!).lastPathComponent)
    assertNotNull(fooNode)
    assertEquals(4, fooNode!!.childCount)
  }

  @Throws(Exception::class)
  fun testFileSystemTree_ContextMenu_Synchronize_Works() {
    // Prepare
    val controller = createController()
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)

    // Expand 2 directories
    expandEntry(myFoo!!)
    expandEntry(myFooDir!!)

    // Select 2 nodes, but do not select the "myFooDir" subdirectory, as synchronizing
    // its parent ("myFoo") show implicitly expand all its children too.
    myMockView!!.tree.selectionPath = getFileEntryPath(myFoo!!)
    myMockView!!.tree.addSelectionPath(getFileEntryPath(myFooFile2!!))
    val actionGroup = myMockView!!.fileTreeActionGroup
    val action = getActionByText(actionGroup, "Synchronize")
    assertNotNull(action)
    val e = createContentMenuItemEvent()
    action!!.update(e)
    assertTrue(e.presentation.isVisible)
    assertTrue(e.presentation.isEnabled)

    // Add 1 files in each expanded directory, check the tree does not show them yet
    myFoo!!.addFile("NewFile.txt")
    myFooDir!!.addFile("NewFile.txt")
    assertEquals(
      myFoo!!.mockEntries.size - 1,
      DeviceFileEntryNode.fromNode(getFileEntryPath(myFoo!!).lastPathComponent)!!.childCount
    )
    assertEquals(
      myFooDir!!.mockEntries.size - 1,
      DeviceFileEntryNode.fromNode(getFileEntryPath(myFooDir!!).lastPathComponent)!!.childCount
    )
    val futureMyFooChanged = createNodeExpandedFuture(myFoo!!)
    val futureMyFooDirChanged = createNodeExpandedFuture(
      myFooDir!!
    )

    // Act
    action.actionPerformed(e)

    // Assert
    pumpEventsAndWaitForFuture(myMockView!!.synchronizeNodesTracker.consume())
    pumpEventsAndWaitForFuture(futureMyFooChanged)
    pumpEventsAndWaitForFuture(futureMyFooDirChanged)
    assertEquals(
      myFoo!!.mockEntries.size,
      DeviceFileEntryNode.fromNode(getFileEntryPath(myFoo!!).lastPathComponent)!!.childCount
    )
    assertEquals(
      myFooDir!!.mockEntries.size,
      DeviceFileEntryNode.fromNode(getFileEntryPath(myFooDir!!).lastPathComponent)!!.childCount
    )
  }

  fun testTreeNodeOrder() {
    val comparator = CustomComparator({ s: String? -> s!! }) { s: String -> s.startsWith("D") }
    val l: List<String?> = ArrayList(Arrays.asList(null, "Dir3", "B1", "AbC", "abD", null, "Dir1", "DiR2"))
    l.sort(comparator)
    Truth.assertThat(l)
      .containsExactly(null, null, "Dir1", "DiR2", "Dir3", "AbC", "abD", "B1")
      .inOrder()
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun testOpenFileInEditorFailure() {
    // Prepare
    val controller = createController()

    // Act
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)
    val errorMessage = "<Expected test error>"
    myMockFileManager!!.openFileInEditorError = RuntimeException(errorMessage)

    // Select node
    myMockView!!.tree.selectionPath = getFileEntryPath(myFile1!!)

    // Send a VK_ENTER key event
    fireEnterKey(myMockView!!.tree)
    pumpEventsAndWaitForFuture(myMockView!!.openNodesInEditorInvokedTracker.consume())
    pumpEventsAndWaitForFuture(myMockFileManager!!.downloadFileEntryTracker.consume())
    pumpEventsAndWaitForFuture(myMockFileManager!!.downloadFileEntryCompletionTracker.consume())
    val loadingError = pumpEventsAndWaitForFuture(myMockView!!.reportErrorRelatedToNodeTracker.consume())

    // Assert
    assertNotNull(loadingError)
    assertTrue(loadingError.contains(errorMessage))
  }

  @Throws(ExecutionException::class, InterruptedException::class, TimeoutException::class)
  fun testCustomFileOpenerIsCalled() {
    // Prepare
    val openPath = arrayOf<Path?>(null)
    val fileOpener = makeFileOpener { localPath: Path? -> openPath[0] = localPath }
    val controller = createController(fileOpener)

    // Act
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)

    // Select node
    myMockView!!.tree.selectionPath = getFileEntryPath(myFile1!!)

    // Send a VK_ENTER key event
    fireEnterKey(myMockView!!.tree)
    pumpEventsAndWaitForFuture(myMockView!!.openNodesInEditorInvokedTracker.consume())
    pumpEventsAndWaitForFuture(myMockFileManager!!.downloadFileEntryTracker.consume())
    pumpEventsAndWaitForFuture(myMockFileManager!!.downloadFileEntryCompletionTracker.consume())

    // Assert
    assertTrue(openPath[0].toString().endsWith("file1.txt"))
  }

  private fun replaceTestDialog(showFunction: Function<String, Int>) {
    val previousDialog = TestDialogManager.setTestDialog { t: String -> showFunction.apply(t) }
    if (myInitialTestDialog == null) {
      myInitialTestDialog = previousDialog
    }
  }

  private fun replaceTestInputDialog(returnValue: String?) {
    val previousDialog = TestDialogManager.setTestInputDialog(object : TestInputDialog {
      override fun show(message: String): String {
        return show(message, null)
      }

      override fun show(message: String, validator: InputValidator?): String {
        validator?.checkInput(message)
        return returnValue!!
      }
    })
    if (myInitialTestInputDialog == null) {
      myInitialTestInputDialog = previousDialog
    }
  }

  private fun checkContextMenuItemVisible(menuPath: String, visible: Boolean) {
    val action = getContextMenuAction(menuPath)
    val e = createContentMenuItemEvent()
    action.update(e)

    // Assert
    assertEquals(visible, e.presentation.isVisible)
    assertEquals(visible, e.presentation.isEnabled)
  }

  private fun getContextMenuAction(menuPath: String): AnAction {
    var actionGroup = myMockView!!.fileTreeActionGroup
    val menuNames = StringUtil.split(menuPath, "/")
    for (i in 0 until menuNames.size - 1) {
      val subGroup = getSubGroup(actionGroup!!, menuNames[i])
      assertNotNull(subGroup)
      actionGroup = subGroup
    }
    val action = getActionByText(actionGroup!!, menuNames[menuNames.size - 1])
    assertNotNull(action)
    return action!!
  }

  private fun createNodeExpandedFuture(entry: MockDeviceFileEntry): SettableFuture<TreePath> {
    assert(entry.isDirectory)
    val entryPath = getFileEntryPath(entry)
    val isNodeExpandedFuture = SettableFuture.create<TreePath>()
    val treeModelAdapter: TreeModelAdapter = object : TreeModelAdapter() {
      override fun process(event: TreeModelEvent, type: EventType) {
        if (isNodeFullyUpdated(event)) {
          isNodeExpandedFuture.set(event.treePath)
        }
      }

      private fun isNodeFullyUpdated(event: TreeModelEvent): Boolean {
        val entryNode = DeviceFileEntryNode.fromNode(entryPath.lastPathComponent)
        assertNotNull(entryNode)

        // Ensure this is the final event where we have all children (and not just the
        // "Loading..." child)
        if (entryNode != event.treePath.lastPathComponent) {
          return false
        }
        if (entryNode!!.childCount == 1 && entryNode.getChildAt(0) is ErrorNode) {
          return true
        }
        if (entryNode.childCount != entry.mockEntries.size) {
          return false
        }
        val nodes = entryNode.childEntryNodes.stream().map { x: DeviceFileEntryNode -> x.entry.name }
          .collect(Collectors.toSet())
        val entries = entry.mockEntries.stream().map(MockDeviceFileEntry::name).collect(Collectors.toSet())
        return nodes == entries
      }
    }
    myMockView!!.tree.model.addTreeModelListener(treeModelAdapter)
    myEdtExecutor!!.addConsumer(
      isNodeExpandedFuture
    ) { path: TreePath?, throwable: Throwable? -> myMockView!!.tree.model.removeTreeModelListener(treeModelAdapter) }
    return isNodeExpandedFuture
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  private fun checkMockViewInitialState(controller: DeviceExplorerController, activeDevice: MockDeviceFileSystem?) {
    checkMockViewComboBox(controller)
    checkMockViewActiveDevice(activeDevice)
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  private fun checkMockViewComboBox(controller: DeviceExplorerController) {
    // Check we have 2 devices available
    val devices = pumpEventsAndWaitForFutures(myMockView!!.deviceAddedTracker.consumeMany(2))
    assertEquals(2, devices.size)
    assertEquals(1, devices.stream().filter { x: DeviceFileSystem -> x.name === "TestDevice-1" }.count())
    assertEquals(1, devices.stream().filter { x: DeviceFileSystem -> x.name === "TestDevice-2" }.count())

    // The device combo box should contain both devices, and the first one should be selected
    assertEquals(2, myMockView!!.deviceCombo.itemCount)

    // The first device should be selected automatically
    pumpEventsAndWaitForFuture(myMockView!!.deviceSelectedTracker.consume())
    assertEquals(0, myMockView!!.deviceCombo.selectedIndex)
    assertTrue(controller.hasActiveDevice())
  }

  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  private fun checkMockViewActiveDevice(activeDevice: MockDeviceFileSystem?) {
    pumpEventsAndWaitForFuture(myMockView!!.startTreeBusyIndicatorTacker.consume())
    pumpEventsAndWaitForFuture(myMockView!!.stopTreeBusyIndicatorTacker.consume())

    // The root node should have been expanded to show the first level of children
    pumpEventsAndWaitForFuture(myMockView!!.treeNodeExpandingTracker.consume())

    // Check the file system tree is displaying the file system of the first device
    val rootEntry = DeviceFileEntryNode.fromNode(myMockView!!.tree.model.root)
    assertNotNull(rootEntry)
    assertEquals(activeDevice!!.root, rootEntry!!.entry)

    // Check the file system tree is showing the first level of entries of the file system
    pumpEventsAndWaitForFuture(myMockView!!.treeModelChangedTracker.consume())
    pumpEventsAndWaitForFuture(myMockView!!.treeStructureChangedTacker.consume())
    assertEquals(
      "mock: " + activeDevice.root.mockEntries + " rootEntry: " + TreeUtil.getChildren(
        rootEntry
      ).collect(Collectors.toList()),
      activeDevice.root.mockEntries.size, rootEntry.childCount
    )
  }

  @Throws(Exception::class)
  fun downloadFile(file: MockDeviceFileEntry?, trigger: Runnable): VirtualFile {
    // Prepare
    val controller = createController()

    // Act
    controller.setup()
    pumpEventsAndWaitForFuture(myMockView!!.startRefreshTracker.consume())
    checkMockViewInitialState(controller, myDevice1)
    myDevice1!!.downloadChunkSize = 1000 // download chunks of 1000 bytes at a time
    myDevice1!!.downloadChunkIntervalMillis = 10 // wait 10 millis between each 1000 bytes chunk
    // Setting the size to 200_000 bytes should force the download to take ~2 seconds,
    // i.e. 200 chunks of 1000 bytes at 100 chunks per second.
    // This allows use to cover the code that animates nodes UI during download.
    file!!.size = 200000

    // Select node
    val file1Path = getFileEntryPath(file)
    myMockView!!.tree.selectionPath = file1Path
    trigger.run()

    // Assert
    pumpEventsAndWaitForFuture(myMockFileManager!!.downloadFileEntryTracker.consume())
    return pumpEventsAndWaitForFuture(myMockFileManager!!.downloadFileEntryCompletionTracker.consume())
  }

  private fun expandEntry(entry: MockDeviceFileEntry) {
    // Attach listener for node expansion completion
    val futureNodeExpanded = createNodeExpandedFuture(entry)

    // Expand node
    myMockView!!.tree.expandPath(getFileEntryPath(entry))

    // Wait for tree node to be expanded
    pumpEventsAndWaitForFuture(futureNodeExpanded)
    pumpEventsAndWaitForFuture(myMockView!!.startTreeBusyIndicatorTacker.consume())
    pumpEventsAndWaitForFuture(myMockView!!.stopTreeBusyIndicatorTacker.consume())
  }

  private fun createController(fileOpener: DeviceExplorerController.FileOpener): DeviceExplorerController {
    return createController(myMockView, myMockService, myMockFileManager, fileOpener)
  }

  private fun createController(
    view: DeviceExplorerView? = myMockView,
    service: DeviceFileSystemService<*>? = myMockService,
    deviceExplorerFileManager: DeviceExplorerFileManager? = myMockFileManager,
    fileOpener: DeviceExplorerController.FileOpener? = DeviceExplorerController.FileOpener { localPath: Path ->
      myMockFileManager!!.openFile(
        localPath
      )
    }
  ): DeviceExplorerController {
    return DeviceExplorerController(project, myModel!!, view!!, service!!, deviceExplorerFileManager!!, fileOpener!!)
  }

  /**
   * Returns the [TreePath] corresponding to a given [DeviceFileEntry].
   * Throws an exception if the file entry is not found.
   */
  private fun getFileEntryPath(entry: MockDeviceFileEntry): TreePath {
    val entries = getEntryStack(entry)
    val nodes: MutableList<Any?> = ArrayList()
    var currentNode = DeviceFileEntryNode.fromNode(myMockView!!.tree.model.root)
    assertNotNull(currentNode)
    var currentEntry = entries.pop()
    assertEquals(currentNode!!.entry, currentEntry)
    nodes.add(currentNode)
    while (!entries.isEmpty()) {
      val newEntry = entries.pop()
      currentEntry = null
      for (i in 0 until myMockView!!.tree.model.getChildCount(currentNode)) {
        val newNode = DeviceFileEntryNode.fromNode(myMockView!!.tree.model.getChild(currentNode, i))
        assertNotNull(newNode)
        if (newNode!!.entry === newEntry) {
          currentNode = newNode
          currentEntry = newEntry
          break
        }
      }
      assertNotNull(String.format("File System Tree does not contain node \"%s\"", entry.fullPath), currentEntry)
      nodes.add(currentNode)
    }
    return TreePath(nodes.toTypedArray())
  }

  class MockDeviceFileSystemRendererFactory : DeviceFileSystemRendererFactory {
    override fun create(deviceNamePropertiesProvider: DeviceNamePropertiesProvider): DeviceFileSystemRenderer<*> {
      return MockDeviceFileSystemRenderer()
    }
  }

  companion object {
    private fun <V> enumerationAsList(e: Enumeration<*>): List<V> {
      return Collections.list<Any>(e)
    }

    private fun createContentMenuItemEvent(): AnActionEvent {
      return AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null) { dataId: String? -> null }
    }

    private fun getActionByText(actionGroup: ActionGroup, text: String): AnAction? {
      val e = createContentMenuItemEvent()
      return Arrays.stream(actionGroup.getChildren(null))
        .filter { x: AnAction? ->
          x!!.update(e)
          text == e.presentation.text
        }
        .findFirst()
        .orElseGet { null }
    }

    private fun getSubGroup(actionGroup: ActionGroup, name: String): ActionGroup? {
      return Arrays.stream(actionGroup.getChildren(null))
        .filter { x: AnAction? -> x is ActionGroup }
        .map { x: AnAction? -> x as ActionGroup? }
        .filter { x: ActionGroup? -> name == x!!.templatePresentation.text }
        .findFirst()
        .orElse(null)
    }

    private fun getEntryStack(entry: MockDeviceFileEntry): Stack<MockDeviceFileEntry> {
      var entry = entry
      val entries = Stack<MockDeviceFileEntry>()
      while (entry != null) {
        entries.add(entry)
        entry = entry.parent!!
      }
      return entries
    }

    private fun fireEnterKey(component: JComponent) {
      val event = KeyEvent(component, 0, 0, 0, KeyEvent.VK_ENTER, '\u0000')
      for (listener in component.keyListeners) {
        listener.keyPressed(event)
      }
      for (listener in component.keyListeners) {
        listener.keyTyped(event)
      }
      for (listener in component.keyListeners) {
        listener.keyReleased(event)
      }
    }

    private fun fireDoubleClick(component: JComponent, x: Int, y: Int) {
      val event = MouseEvent(component, 0, 0, 0, x, y, 2, false, MouseEvent.BUTTON1)
      for (listener in component.mouseListeners) {
        listener.mouseClicked(event)
      }
      for (listener in component.mouseListeners) {
        listener.mousePressed(event)
      }
    }
  }
}