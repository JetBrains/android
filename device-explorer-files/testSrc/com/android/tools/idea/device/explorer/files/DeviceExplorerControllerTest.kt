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
package com.android.tools.idea.device.explorer.files

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.adb.AdbShellCommandException
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFutureException
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFutures
import com.android.tools.idea.device.explorer.files.DeviceFileExplorerController.Companion.getProjectController
import com.android.tools.idea.device.explorer.files.DeviceFileExplorerController.NodeSorting.CustomComparator
import com.android.tools.idea.device.explorer.files.fs.DeviceFileEntry
import com.android.tools.idea.device.explorer.files.fs.DeviceFileSystem
import com.android.tools.idea.device.explorer.files.fs.DeviceFileSystemService
import com.android.tools.idea.device.explorer.files.fs.DeviceState
import com.android.tools.idea.device.explorer.files.mocks.MockDeviceExplorerFileManager
import com.android.tools.idea.device.explorer.files.mocks.MockDeviceExplorerView
import com.android.tools.idea.device.explorer.files.mocks.MockDeviceFileEntry
import com.android.tools.idea.device.explorer.files.mocks.MockDeviceFileSystem
import com.android.tools.idea.device.explorer.files.mocks.MockDeviceFileSystemService
import com.android.tools.idea.device.explorer.files.ui.TreeUtil
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.ThreadingCheckRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.SettableFuture
import com.intellij.ide.ClipboardSynchronizer
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.FileSaverDialog
import com.intellij.openapi.fileChooser.PathChooserDialog
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.ui.TestInputDialog
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.ui.UIBundle
import com.intellij.util.Consumer
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.ui.tree.TreeModelAdapter
import kotlinx.coroutines.runBlocking
import org.jetbrains.ide.PooledThreadExecutor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.awt.Component
import java.awt.Point
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.nio.file.Files
import java.nio.file.Path
import java.util.Arrays
import java.util.Collections
import java.util.Enumeration
import java.util.Stack
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.Collectors
import javax.swing.JComponent
import javax.swing.RepaintManager
import javax.swing.TransferHandler
import javax.swing.TransferHandler.TransferSupport
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import javax.swing.event.TreeModelEvent
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.DefaultTreeSelectionModel
import javax.swing.tree.TreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

@RunsInEdt
class DeviceExplorerControllerTest {

  // We need to use a heavy fixture (i.e. on disk) so that the project gets disposed
  // at the end; otherwise, some state will be left over and interfere with other tests.
  // (Specifically, the DeviceNamePropertiesFetcher.DeviceChangeListener will remain in
  // AndroidDebugBridge.)
  @get:Rule
  val androidProjectRule = AndroidProjectRule.onDisk().onEdt()

  @get:Rule
  val threadingCheckRule = ThreadingCheckRule()

  private val project: Project
    get() = androidProjectRule.project

  private lateinit var myModel: DeviceFileExplorerModel
  private lateinit var myMockView: MockDeviceExplorerView
  private lateinit var myMockService: MockDeviceFileSystemService
  private lateinit var myMockFileManager: MockDeviceExplorerFileManager
  private val myDownloadLocation = AtomicReference<Path>()
  private lateinit var myDevice1: MockDeviceFileSystem
  private lateinit var myFoo: MockDeviceFileEntry
  private lateinit var myFooFile1: MockDeviceFileEntry
  private lateinit var myFooFile2: MockDeviceFileEntry
  private lateinit var myFooLink1: MockDeviceFileEntry
  private lateinit var myFile1: MockDeviceFileEntry
  private lateinit var myFile2: MockDeviceFileEntry
  private lateinit var myDevice2: MockDeviceFileSystem
  private lateinit var myFooDir: MockDeviceFileEntry
  private lateinit var myFooDirLink: MockDeviceFileEntry
  private var myInitialTestDialog: TestDialog? = null
  private var myInitialTestInputDialog: TestInputDialog? = null
  private lateinit var myEdtExecutor: FutureCallbackExecutor
  private lateinit var myTaskExecutor: FutureCallbackExecutor
  private var myTearingDown = false

  @Before
  fun setUp() {
    myEdtExecutor = FutureCallbackExecutor.wrap(EdtExecutorService.getInstance())
    myTaskExecutor = FutureCallbackExecutor.wrap(PooledThreadExecutor.INSTANCE)
    myModel = object : DeviceFileExplorerModel() {
      override fun setActiveDeviceTreeModel(
        device: DeviceFileSystem?,
        treeModel: DefaultTreeModel?,
        treeSelectionModel: DefaultTreeSelectionModel?
      ) {
        if (!myTearingDown) {
          // We notify the mock view before everything else to avoid having a dependency
          // on the order of registration of listeners registered with {@code DeviceExplorerModel.addListener()}
          myMockView.deviceTreeModelUpdated(device, treeModel, treeSelectionModel)
        }
        super.setActiveDeviceTreeModel(device, treeModel, treeSelectionModel)
      }
    }
    myMockService = MockDeviceFileSystemService(project, myEdtExecutor, myTaskExecutor)
    myMockView = MockDeviceExplorerView(project, myModel)
    val downloadPath = FileUtil.createTempDirectory("device-explorer-temp", "", true)
    myDownloadLocation.set(downloadPath.toPath())
    myMockFileManager = MockDeviceExplorerFileManager(project, myDownloadLocation::get)
    myDevice1 = myMockService.addDevice("TestDevice-1")
    myFoo = myDevice1.root.addDirectory("Foo")
    myFooDirLink = myDevice1.root.addDirLink("fooDirLink", "fooDir")
    myFooFile1 = myFoo.addFile("fooFile1.txt")
    myFooFile2 = myFoo.addFile("fooFile2.txt")
    myFooLink1 = myFoo.addFileLink("fooLink1.txt", "fooFile1.txt")
    myFooDir = myFoo.addDirectory("fooDir")
    myFooDir.addFile("fooDirFile1.txt")
    myFooDir.addFile("fooDirFile2.txt")
    myFile1 = myDevice1.root.addFile("file1.txt")
    myFile2 = myDevice1.root.addFile("file2.txt")
    myDevice1.root.addFile("file3.txt")
    myDevice2 = myMockService.addDevice("TestDevice-2")
    myDevice2.root.addDirectory("Foo2")
    myDevice2.root.addFile("foo2File1.txt")
    myDevice2.root.addFile("foo2File2.txt")
  }

  @After
  fun tearDown() {
    myTearingDown = true
    RepaintManager.setCurrentManager(null)
    if (myInitialTestDialog != null) {
      TestDialogManager.setTestDialog(myInitialTestDialog)
    }
    if (myInitialTestInputDialog != null) {
      TestDialogManager.setTestInputDialog(myInitialTestInputDialog)
    }
    Disposer.dispose(myMockFileManager)
    ClipboardSynchronizer.getInstance().resetContent()
  }

  @Test
  fun controllerIsSetAsProjectKey() {
    // Prepare
    val controller = createController()

    // Assert
    assertEquals(controller, getProjectController(project))
  }

  @Test
  fun startController() {
    // Prepare // Act // Assert
    createControllerAndVerifyViewInitialState()
  }

  @Test
  fun showNoDeviceViewWithNoActiveDevice() {
    // Prepare
    val controller = createController()

    // Act
    controller.setup()
    controller.setActiveConnectedDevice(null)

    pumpEventsAndWaitForFuture(myMockView.showNoDeviceScreenTracker.consume())
  }

  @Test
  fun getRootDirectoryFailure() {
    // Prepare
    val setupErrorMessage = "<Unique error message>"
    myDevice1.rootDirectoryError = RuntimeException(setupErrorMessage)
    val controller = createController()

    // Act
    controller.setup()
    controller.setActiveConnectedDevice(myDevice1)
    val errorMessage = pumpEventsAndWaitForFuture(myMockView.reportErrorRelatedToDeviceTracker.consume())

    // Assert
    checkNotNull(errorMessage)
    assertTrue(errorMessage.contains(setupErrorMessage))
  }

  @Test
  fun expandChildren() {
    // Prepare
    val controller = createControllerAndVerifyViewInitialState()

    // Act
    // Set timers to ensure the "loading..." animation code is hit
    controller.showLoadingNodeDelayMillis = 10
    controller.transferringNodeRepaintMillis = 10
    myFoo.getEntriesTimeoutMillis = 1000

    // Listen to node expansion effect (structure changed event)
    val fooPath = getFileEntryPath(myFoo)
    val futureNodeExpanded = createNodeExpandedFuture(myFoo)
    val futureTreeNodesChanged = SettableFuture.create<MyLoadingNode>()
    myMockView.tree.model.addTreeModelListener(object : TreeModelAdapter() {
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
    myMockView.tree.expandPath(fooPath)

    // Wait for tree node to be expanded
    val myLoadingNode = pumpEventsAndWaitForFuture(futureTreeNodesChanged)
    val nodeExpandedPath = pumpEventsAndWaitForFuture(futureNodeExpanded)

    // Assert
    assertTrue(myLoadingNode.tick > 1)
    assertEquals(fooPath.lastPathComponent, nodeExpandedPath.lastPathComponent)
  }

  @Test
  fun expandChildrenFailure() {
    // Prepare
    createControllerAndVerifyViewInitialState()
    val errorMessage = "<Expected test error>"
    myFoo.getEntriesError = RuntimeException(errorMessage)
    val nodeExpandedFuture = createNodeExpandedFuture(myFoo)

    // Act
    expandEntry(myFoo)
    pumpEventsAndWaitForFuture(nodeExpandedFuture)

    // Assert
    val fooNode = getFileEntryPath(myFoo).lastPathComponent
    assertThat(fooNode).isInstanceOf(TreeNode::class.java)
    assertTrue((fooNode as TreeNode).childCount == 1)
    val errorNode = fooNode.getChildAt(0)
    assertThat(errorNode).isInstanceOf(ErrorNode::class.java)
    assertEquals(errorMessage, (errorNode as ErrorNode).text)
  }

  @Test
  fun openNodeInEditorDoesNothingForSymlinkToDirectory() {
    // Prepare
    val mockFileManager: DeviceExplorerFileManager = Mockito.spy(myMockFileManager)
    val controller = createController(myMockView, mockFileManager) { localPath: Path -> myMockFileManager.openFile(localPath) }
    setupControllerAndVerifyViewInitialState(controller)
    val listener = myMockView.listeners[0]
    val fooDirPath = getFileEntryPath(myFooDirLink)
    myMockView.tree.selectionPath = fooDirPath
    val node = fooDirPath.lastPathComponent as DeviceFileEntryNode
    val nodes: MutableList<DeviceFileEntryNode> = ArrayList()

    // Act
    nodes.add(node)
    listener.openNodesInEditorInvoked(nodes)

    // Verify
    Mockito.verifyNoMoreInteractions(mockFileManager)
  }

  @Test
  fun downloadFileWithEnterKey() {
    createControllerAndVerifyViewInitialState()

    downloadFile(myFile1) {

      // Send a VK_ENTER key event
      fireEnterKey(myMockView.tree)
      pumpEventsAndWaitForFuture(myMockView.openNodesInEditorInvokedTracker.consume())
    }
    pumpEventsAndWaitForFuture(myMockFileManager.openFileInEditorTracker.consume())
  }

  @Test
  fun downloadFileWithMouseClick() {
    createControllerAndVerifyViewInitialState()

    downloadFile(myFile1) {
      val path = getFileEntryPath(myFile1)
      val pathBounds = checkNotNull(myMockView.tree.getPathBounds(path))

      // Fire double-click event
      fireDoubleClick(myMockView.tree, pathBounds.x, pathBounds.y)
      pumpEventsAndWaitForFuture(myMockView.openNodesInEditorInvokedTracker.consume())
    }
    pumpEventsAndWaitForFuture(myMockFileManager.openFileInEditorTracker.consume())
  }

  @Test
  fun downloadFileLocationWithMouseClick() {
    createControllerAndVerifyViewInitialState()

    // This saves in the default location for test
    downloadFile(myFile1) {
      val path = getFileEntryPath(myFile1)
      val pathBounds = checkNotNull(myMockView.tree.getPathBounds(path))

      // Fire double-click event
      fireDoubleClick(myMockView.tree, pathBounds.x, pathBounds.y)
      pumpEventsAndWaitForFuture(myMockView.openNodesInEditorInvokedTracker.consume())
    }
    var downloadPath = pumpEventsAndWaitForFuture(myMockFileManager.openFileInEditorTracker.consume())
    assertThat(downloadPath.systemIndependentPath)
        .endsWith(myDownloadLocation.get().systemIndependentPath + "/TestDevice-1/file1.txt")

    // Change the setting to an alternate directory, ensure that changing during runtime works
    val changedPath = FileUtil.createTempDirectory("device-explorer-temp-2", "", true).toPath()
    myDownloadLocation.set(changedPath)

    // Now try the alternate location
    downloadFile(myFile1) {
      val path = getFileEntryPath(myFile1)
      val pathBounds = checkNotNull(myMockView.tree.getPathBounds(path))

      // Fire double-click event
      fireDoubleClick(myMockView.tree, pathBounds.x, pathBounds.y)
      pumpEventsAndWaitForFuture(myMockView.openNodesInEditorInvokedTracker.consume())
    }
    downloadPath = pumpEventsAndWaitForFuture(myMockFileManager.openFileInEditorTracker.consume())
    assertThat(downloadPath.systemIndependentPath).endsWith("device-explorer-temp-2/TestDevice-1/file1.txt")
  }

  @Test
  fun downloadFileFailure() {
    // Prepare
    createControllerAndVerifyViewInitialState()

    // Act
    val errorMessage = "<Expected test error>"
    myDevice1.downloadError = RuntimeException(errorMessage)

    // Select node
    myMockView.tree.selectionPath = getFileEntryPath(myFile1)

    // Send a VK_ENTER key event
    fireEnterKey(myMockView.tree)
    pumpEventsAndWaitForFuture(myMockView.openNodesInEditorInvokedTracker.consume())
    pumpEventsAndWaitForFuture(myMockFileManager.downloadFileEntryTracker.consume())
    val t = pumpEventsAndWaitForFutureException<VirtualFile>(myMockFileManager.downloadFileEntryCompletionTracker.consume())
    val loadingError = pumpEventsAndWaitForFuture(myMockView.reportErrorRelatedToNodeTracker.consume())

    // Assert
    checkNotNull(t)
    checkNotNull(loadingError)
    assertTrue(loadingError.contains(errorMessage))
  }

  @Test
  fun changeActiveDevice() {
    // Prepare
    val controller = createControllerAndVerifyViewInitialState()

    // Act
    controller.setActiveConnectedDevice(myDevice2)

    // Assert
    checkMockViewActiveDevice()
  }

  @Test
  fun changeActiveDeviceDuringFileDownload() {
    // Prepare
    val controller = createControllerAndVerifyViewInitialState()

    // Start file download.
    downloadFile(myFile1) {

      // Send a VK_ENTER key event.
      fireEnterKey(myMockView.tree)
      pumpEventsAndWaitForFuture(myMockFileManager.openFileInEditorTracker.consume())
    }
    myMockView.startTreeBusyIndicatorTacker.clear()
    myMockView.stopTreeBusyIndicatorTacker.clear()
    myMockView.reportMessageRelatedToNodeTracker.clear()

    // Change selected device.
    controller.setActiveConnectedDevice(myDevice2)

    // Check that the view shows the second device.
    checkMockViewActiveDevice()
    // Check that the download from the first device finished successfully.
    pumpEventsAndWaitForFuture(myMockView.openNodesInEditorInvokedTracker.consume())
  }

  @Test
  fun fileSystemTree_ContextMenu_Items_Present() {
    // Prepare
    createControllerAndVerifyViewInitialState()

    // Act // Assert
    val actionGroup = myMockView.fileTreeActionGroup
    assertEquals(9, actionGroup.getChildren(null).size)
    val subGroup = getSubGroup(actionGroup, "New")
    checkNotNull(subGroup)
    assertEquals(2, subGroup.getChildren(null).size)

    // Act: Call "update" on each action, just to make sure the code is covered
    myMockView.tree.selectionPath = getFileEntryPath(myFile1)
    val actions = Arrays.asList(*actionGroup.getChildren(null))
    val e = createContentMenuItemEvent()
    actions.forEach  { it.update(e) }
  }

  @Test
  fun fileSystemTree_ContextMenu_Open_Works() {
    createControllerAndVerifyViewInitialState()

    downloadFile(myFile1) {
      val actionGroup = myMockView.fileTreeActionGroup
      val action = getActionByText(actionGroup, "Open")
      checkNotNull(action)
      val e = createContentMenuItemEvent()
      action.update(e)
      // Assert
      assertTrue(e.presentation.isVisible)
      assertTrue(e.presentation.isEnabled)

      // Act
      action.actionPerformed(e)

      // Assert
      pumpEventsAndWaitForFuture(myMockView.openNodesInEditorInvokedTracker.consume())
    }
    pumpEventsAndWaitForFuture(myMockFileManager.openFileInEditorTracker.consume())
  }

  @Test
  fun fileSystemTree_ContextMenu_SaveFileAs_Works() {
    createControllerAndVerifyViewInitialState()

    val tempFile = FileUtil.createTempFile("foo", "bar")
    downloadFile(myFile1) {

      // Prepare
      // The "Save As" dialog does not work in headless mode, so we register a custom
      // component that simply returns the tempFile we created above.
      val factory: FileChooserFactoryImpl = object : FileChooserFactoryImpl() {
        override fun createSaveFileDialog(descriptor: FileSaverDescriptor, project: Project?): FileSaverDialog {
          return object : FileSaverDialog {
            override fun save(baseDir: VirtualFile?, filename: String?): VirtualFileWrapper {
              return VirtualFileWrapper(tempFile)
            }

            override fun save(baseDir: Path?, filename: String?): VirtualFileWrapper {
              return VirtualFileWrapper(tempFile)
            }
          }
        }
      }
      ApplicationManager.getApplication().replaceService(
        FileChooserFactory::class.java, factory, androidProjectRule.testRootDisposable)

      // Invoke "Save As..." content menu
      val actionGroup = myMockView.fileTreeActionGroup
      val action = getActionByText(actionGroup, "Save As...")
      checkNotNull(action)
      val e = createContentMenuItemEvent()
      action.update(e)

      // Assert
      assertTrue(e.presentation.isVisible)
      assertTrue(e.presentation.isEnabled)

      // Act
      action.actionPerformed(e)

      // Assert
      pumpEventsAndWaitForFuture(myMockView.saveNodesAsTracker.consume())
    }

    // Assert
    assertTrue(tempFile.exists())
    assertEquals(200000, tempFile.length())
  }

  @Test
  fun fileSystemTree_ContextMenu_SaveDirectoryAs_Works() {
    // Prepare
    createControllerAndVerifyViewInitialState()

    // Act
    // Select node
    val file1Path = getFileEntryPath(myFoo)
    myMockView.tree.selectionPath = file1Path
    val actionGroup = myMockView.fileTreeActionGroup
    val action = getActionByText(actionGroup, "Save As...")
    checkNotNull(action)
    val e = createContentMenuItemEvent()
    action.update(e)

    // Assert
    assertTrue(e.presentation.isVisible)
    assertTrue(e.presentation.isEnabled)

    // Prepare
    // The "Choose file" dialog does not work in headless mode, so we register a custom
    // component that simply returns the tempFile we created above.
    val tempDirectory = FileUtil.createTempDirectory("saveAsDir", "")
    myDevice1.downloadChunkSize = 1000 // download chunks of 1000 bytes at a time
    myDevice1.downloadChunkIntervalMillis = 10 // wait 10 millis between each 1000 bytes chunk
    val factory: FileChooserFactoryImpl = object : FileChooserFactoryImpl() {
      override fun createPathChooser(
        descriptor: FileChooserDescriptor,
        project: Project?,
        parent: Component?
      ): PathChooserDialog {
        return PathChooserDialog { toSelect: VirtualFile?, callback: Consumer<in List<VirtualFile?>> ->
          callback.consume(listOf(VirtualFileWrapper(tempDirectory).virtualFile))
        }
      }
    }
    ApplicationManager.getApplication().replaceService(
      FileChooserFactory::class.java, factory, androidProjectRule.testRootDisposable)

    // Act
    myMockView.startTreeBusyIndicatorTacker.clear()
    myMockView.stopTreeBusyIndicatorTacker.clear()
    myMockView.reportMessageRelatedToNodeTracker.clear()
    action.actionPerformed(e)

    // Assert
    pumpEventsAndWaitForFuture(myMockView.startTreeBusyIndicatorTacker.consume())
    pumpEventsAndWaitForFuture(myMockView.stopTreeBusyIndicatorTacker.consume())
    val summaryMessage = pumpEventsAndWaitForFuture(myMockView.reportMessageRelatedToNodeTracker.consume())
    checkNotNull(summaryMessage)
    println("SaveAs message: $summaryMessage")
    assertTrue(summaryMessage.contains("Successfully downloaded"))
    val files = checkNotNull(tempDirectory.listFiles())
    assertThat(files.map { it.name }).containsExactlyElementsIn(
      listOf(myFooFile1, myFooFile2, myFooLink1, myFooDir).map { it.name })
  }

  @Test
  fun fileSystemTree_ContextMenu_SaveMultipleFilesAs_Works() {
    // Prepare
    createControllerAndVerifyViewInitialState()

    // Act
    // Select nodes
    expandEntry(myFoo)
    myMockView.tree.selectionPath = getFileEntryPath(myFooFile1)
    myMockView.tree.addSelectionPath(getFileEntryPath(myFooFile2))
    val actionGroup = myMockView.fileTreeActionGroup
    val action = getActionByText(actionGroup, "Save To...")
    checkNotNull(action)
    val e = createContentMenuItemEvent()
    action.update(e)

    // Assert
    assertTrue(e.presentation.isVisible)
    assertTrue(e.presentation.isEnabled)

    // Prepare
    // The "Choose file" dialog does not work in headless mode, so we register a custom
    // component that simply returns the tempFile we created above.
    val tempDirectory = FileUtil.createTempDirectory("saveAsDir", "")
    myDevice1.downloadChunkSize = 1000 // download chunks of 1000 bytes at a time
    myDevice1.downloadChunkIntervalMillis = 10 // wait 10 millis between each 1000 bytes chunk
    val factory: FileChooserFactoryImpl = object : FileChooserFactoryImpl() {
      override fun createPathChooser(
        descriptor: FileChooserDescriptor,
        project: Project?,
        parent: Component?
      ): PathChooserDialog {
        return PathChooserDialog { toSelect: VirtualFile?, callback: Consumer<in List<VirtualFile?>?> ->
          val list = listOf(
            VirtualFileWrapper(tempDirectory).virtualFile
          )
          callback.consume(list)
        }
      }
    }
    ApplicationManager.getApplication().replaceService(
      FileChooserFactory::class.java, factory, androidProjectRule.testRootDisposable)

    // Act
    myMockView.startTreeBusyIndicatorTacker.clear()
    myMockView.stopTreeBusyIndicatorTacker.clear()
    myMockView.reportMessageRelatedToNodeTracker.clear()
    action.actionPerformed(e)

    // Assert
    pumpEventsAndWaitForFuture(myMockView.startTreeBusyIndicatorTacker.consume())
    pumpEventsAndWaitForFuture(myMockView.stopTreeBusyIndicatorTacker.consume())
    val summaryMessage = pumpEventsAndWaitForFuture(myMockView.reportMessageRelatedToNodeTracker.consume())
    checkNotNull(summaryMessage)
    println("SaveAs message: $summaryMessage")
    assertTrue(summaryMessage.contains("Successfully downloaded"))
    val files = checkNotNull(tempDirectory.listFiles())
    assertThat(files.map { it.name }).containsExactlyElementsIn(listOf(myFooFile1, myFooFile2).map { it.name })
  }

  @Test
  fun fileSystemTree_ContextMenu_SaveDirectoryAs_ShowsProblems() {
    // Prepare
    createControllerAndVerifyViewInitialState()

    // Act
    // Select node
    val file1Path = getFileEntryPath(myFoo)
    myMockView.tree.selectionPath = file1Path
    val actionGroup = myMockView.fileTreeActionGroup
    val action = getActionByText(actionGroup, "Save As...")
    checkNotNull(action)
    val e = createContentMenuItemEvent()
    action.update(e)

    // Assert
    assertTrue(e.presentation.isVisible)
    assertTrue(e.presentation.isEnabled)

    // Prepare
    // The "Choose file" dialog does not work in headless mode, so we register a custom
    // component that simply returns the tempFile we created above.
    val tempDirectory = FileUtil.createTempDirectory("saveAsDir", "")
    myDevice1.downloadChunkSize = 1000 // download chunks of 1000 bytes at a time
    myDevice1.downloadChunkIntervalMillis = 10 // wait 10 millis between each 1000 bytes chunk
    val downloadErrorMessage = "[test] Error downloading file"
    myDevice1.downloadError = Exception(downloadErrorMessage)
    val factory: FileChooserFactoryImpl = object : FileChooserFactoryImpl() {
      override fun createPathChooser(
        descriptor: FileChooserDescriptor,
        project: Project?,
        parent: Component?
      ): PathChooserDialog {
        return PathChooserDialog { toSelect: VirtualFile?, callback: Consumer<in List<VirtualFile?>> ->
          val list = listOf(
            VirtualFileWrapper(tempDirectory).virtualFile
          )
          callback.consume(list)
        }
      }
    }
    ApplicationManager.getApplication().replaceService(
      FileChooserFactory::class.java, factory, androidProjectRule.testRootDisposable)

    // Act
    myMockView.startTreeBusyIndicatorTacker.clear()
    myMockView.stopTreeBusyIndicatorTacker.clear()
    myMockView.reportMessageRelatedToNodeTracker.clear()
    action.actionPerformed(e)

    // Assert
    pumpEventsAndWaitForFuture(myMockView.startTreeBusyIndicatorTacker.consume())
    pumpEventsAndWaitForFuture(myMockView.stopTreeBusyIndicatorTacker.consume())
    val summaryMessage = pumpEventsAndWaitForFuture(myMockView.reportErrorRelatedToNodeTracker.consume())
    checkNotNull(summaryMessage)
    println("SaveAs message: $summaryMessage")
    assertTrue(summaryMessage.contains("There were errors"))
    assertTrue(summaryMessage.contains(downloadErrorMessage))

    // Note: Even though downloading files failed, empty directories are still created during
    //       a directory tree download, in this case we should have exactly one.
    val files = tempDirectory.listFiles()
    checkNotNull(files)
    val createdFiles = Arrays.asList(*files)
    assertEquals(1, createdFiles.size)
    assertTrue(createdFiles.any { it.name == myFooDir.name })
  }

  @Test
  fun fileSystemTree_ContextMenu_New_IsHiddenForFiles() {
    // Prepare
    createControllerAndVerifyViewInitialState()

    // Act
    myMockView.tree.selectionPath = getFileEntryPath(myFile1)

    // Assert
    checkContextMenuItemVisible("New/File", false)
    checkContextMenuItemVisible("New/Directory", false)
  }

  @Test
  fun fileSystemTree_ContextMenu_New_IsVisibleForDirectories() {
    // Prepare
    createControllerAndVerifyViewInitialState()

    // Act
    expandEntry(myFoo)
    myMockView.tree.selectionPath = getFileEntryPath(myFooDir)

    // Assert
    checkContextMenuItemVisible("New/File", true)
    checkContextMenuItemVisible("New/Directory", true)
  }

  @Test
  fun fileSystemTree_ContextMenu_NewFile_Works() {
    // Prepare
    createControllerAndVerifyViewInitialState()
    expandEntry(myFoo)
    val fooDirPath = getFileEntryPath(myFooDir)
    myMockView.tree.selectionPath = fooDirPath
    val fooDirExpandedFuture = createNodeExpandedFuture(
      myFooDir
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
    val newChild = enumerationAsList((fooDirPath.lastPathComponent as DeviceFileEntryNode).children())
      .filterIsInstance<DeviceFileEntryNode>()
      .firstOrNull { newFileName == it.entry.name && it.entry.isFile }
    assertNotNull(newChild)
  }

  @Test
  fun fileSystemTree_ContextMenu_NewDirectory_Works() {
    // Prepare
    createControllerAndVerifyViewInitialState()
    expandEntry(myFoo)
    val fooDirPath = getFileEntryPath(myFooDir)
    myMockView.tree.selectionPath = fooDirPath
    val fooDirExpandedFuture = createNodeExpandedFuture(
      myFooDir
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
    val newChild = enumerationAsList((fooDirPath.lastPathComponent as DeviceFileEntryNode).children())
      .filterIsInstance<DeviceFileEntryNode>()
      .firstOrNull { newDirectoryName == it.entry.name && it.entry.isDirectory }
    checkNotNull(newChild)
  }

  @Test
  fun fileSystemTree_ContextMenu_NewDirectory_ExistingPath_Fails() {
    // Prepare
    createControllerAndVerifyViewInitialState()
    val fooPath = getFileEntryPath(myFoo)
    myMockView.tree.selectionPath = fooPath
    val newDirectoryName = myFooDir.name // Existing name to create conflict
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
    checkNotNull(message)
    assertTrue(message.contains(UIBundle.message("create.new.folder.could.not.create.folder.error.message", newDirectoryName)))

    // Ensure entry does not exist in tree view
    val newChild = enumerationAsList((fooPath.lastPathComponent as DeviceFileEntryNode).children())
      .filterIsInstance<DeviceFileEntryNode>()
      .firstOrNull { newDirectoryName == it.entry.name && it.entry.isDirectory }
    assertNull(newChild)
  }

  @Test
  fun fileSystemTree_ContextMenu_CopyPath_Works() {
    // Prepare
    createControllerAndVerifyViewInitialState()

    // Act
    myMockView.tree.selectionPath = getFileEntryPath(myFile1)
    val actionGroup = myMockView.fileTreeActionGroup
    val action = getActionByText(actionGroup, "Copy Path")
    checkNotNull(action)
    val e = createContentMenuItemEvent()
    action.update(e)

    // Assert
    assertTrue(e.presentation.isVisible)
    assertTrue(e.presentation.isEnabled)

    // Act
    action.actionPerformed(e)
    pumpEventsAndWaitForFuture(myMockView.copyNodePathsTracker.consume())

    // Assert
    val contents = CopyPasteManager.getInstance().contents
    checkNotNull(contents)
    assertEquals("/" + myFile1.name, contents.getTransferData(DataFlavor.stringFlavor))
  }

  @Test
  fun fileSystemTree_ContextMenu_CopyPaths_Works() {
    // Prepare
    createControllerAndVerifyViewInitialState()

    // Act
    myMockView.tree.selectionPath = getFileEntryPath(myFile1)
    myMockView.tree.addSelectionPath(getFileEntryPath(myFile2))
    val actionGroup = myMockView.fileTreeActionGroup
    val action = getActionByText(actionGroup, "Copy Paths")
    checkNotNull(action)
    val e = createContentMenuItemEvent()
    action.update(e)

    // Assert
    assertTrue(e.presentation.isVisible)
    assertTrue(e.presentation.isEnabled)

    // Act
    action.actionPerformed(e)
    pumpEventsAndWaitForFuture(myMockView.copyNodePathsTracker.consume())

    // Assert
    val contents = CopyPasteManager.getInstance().contents
    checkNotNull(contents)
    assertEquals(
      """
  ${myFile1.fullPath}
  ${myFile2.fullPath}
  """.trimIndent(), contents.getTransferData(DataFlavor.stringFlavor)
    )
  }

  @Test
  fun fileSystemTree_ContextMenu_Delete_Works() {
    // Prepare
    createControllerAndVerifyViewInitialState()

    // Act
    expandEntry(myFoo)
    myMockView.tree.selectionPath = getFileEntryPath(myFooFile1)
    myMockView.tree.addSelectionPath(getFileEntryPath(myFooFile2))
    val futureTreeChanged = createNodeExpandedFuture(myFoo)
    val actionGroup = myMockView.fileTreeActionGroup
    val action = getActionByText(actionGroup, "Delete...")
    checkNotNull(action)
    val e = createContentMenuItemEvent()
    action.update(e)
    replaceTestDialog { 0 } // "OK" button

    // Assert
    assertTrue(e.presentation.isVisible)
    assertTrue(e.presentation.isEnabled)

    // Act
    action.actionPerformed(e)
    pumpEventsAndWaitForFuture(myMockView.deleteNodesTracker.consume())
    pumpEventsAndWaitForFuture(futureTreeChanged)

    // Assert
    val fooNode = DeviceFileEntryNode.fromNode(getFileEntryPath(myFoo).lastPathComponent)
    checkNotNull(fooNode)
    assertEquals(2, fooNode.childCount)
  }

  @Test
  fun fileSystemTree_ContextMenu_Delete_ShowProblems() {
    // Prepare
    createControllerAndVerifyViewInitialState()

    // Act
    expandEntry(myFoo)
    myFooFile1.deleteError = AdbShellCommandException("Error deleting file")
    myMockView.tree.selectionPath = getFileEntryPath(myFooFile1)
    myMockView.tree.addSelectionPath(getFileEntryPath(myFooFile2))
    val futureTreeChanged = createNodeExpandedFuture(myFoo)
    val actionGroup = myMockView.fileTreeActionGroup
    val action = getActionByText(actionGroup, "Delete...")
    checkNotNull(action)
    val e = createContentMenuItemEvent()
    action.update(e)
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
    pumpEventsAndWaitForFuture(myMockView.deleteNodesTracker.consume())
    pumpEventsAndWaitForFuture(showProblemsFuture)
    pumpEventsAndWaitForFuture(futureTreeChanged)

    // Assert
    // One entry has been deleted
    val fooNode = DeviceFileEntryNode.fromNode(getFileEntryPath(myFoo).lastPathComponent)
    checkNotNull(fooNode)
    assertEquals(3, fooNode.childCount)
  }

  @Test
  fun fileSystemTree_ContextMenu_Upload_SingleFile_Works() {
    // Prepare
    createControllerAndVerifyViewInitialState()
    expandEntry(myFoo)
    myMockView.tree.selectionPath = getFileEntryPath(myFoo)
    val futureTreeChanged = createNodeExpandedFuture(myFoo)
    val actionGroup = myMockView.fileTreeActionGroup
    val action = getActionByText(actionGroup, "Upload...")
    checkNotNull(action)
    val e = createContentMenuItemEvent()
    action.update(e)

    // The "Choose file" dialog does not work in headless mode, so we register a custom
    // component that simply returns the tempFile we created above.
    val tempFile = FileUtil.createTempFile("foo", "bar.txt")
    Files.write(tempFile.toPath(), ByteArray(10000))
    myDevice1.uploadChunkSize = 500
    myDevice1.uploadChunkIntervalMillis = 20
    val factory: FileChooserFactoryImpl = object : FileChooserFactoryImpl() {
      override fun createPathChooser(
        descriptor: FileChooserDescriptor,
        project: Project?,
        parent: Component?
      ): PathChooserDialog {
        return PathChooserDialog { toSelect: VirtualFile?, callback: Consumer<in List<VirtualFile?>> ->
          callback.consume(listOf(VirtualFileWrapper(tempFile).virtualFile))
        }
      }
    }
    ApplicationManager.getApplication().replaceService(
      FileChooserFactory::class.java, factory, androidProjectRule.testRootDisposable)

    // Assert
    assertTrue(e.presentation.isVisible)
    assertTrue(e.presentation.isEnabled)

    // Act
    myMockView.startTreeBusyIndicatorTacker.clear()
    myMockView.stopTreeBusyIndicatorTacker.clear()
    action.actionPerformed(e)
    pumpEventsAndWaitForFuture(myMockView.startTreeBusyIndicatorTacker.consume())
    pumpEventsAndWaitForFuture(myMockView.uploadFilesTracker.consume())
    pumpEventsAndWaitForFuture(futureTreeChanged)
    pumpEventsAndWaitForFuture(myMockView.stopTreeBusyIndicatorTacker.consume())
    pumpEventsAndWaitForFuture(myMockView.reportMessageRelatedToNodeTracker.consume())

    // Assert
    // One node has been added
    val fooNode = DeviceFileEntryNode.fromNode(getFileEntryPath(myFoo).lastPathComponent)
    checkNotNull(fooNode)
    assertEquals(5, fooNode.childCount)
  }

  @Test
  fun fileSystemTree_ContextMenu_Upload_SingleFile_Cancellation_Works() {
    // Prepare
    val controller = createControllerAndVerifyViewInitialState()
    expandEntry(myFoo)
    myMockView.tree.selectionPath = getFileEntryPath(myFoo)
    val actionGroup = myMockView.fileTreeActionGroup
    val action = getActionByText(actionGroup, "Upload...")
    checkNotNull(action)
    val e = createContentMenuItemEvent()
    action.update(e)

    // The "Choose file" dialog does not work in headless mode, so we register a custom
    // component that simply returns the tempFile we created above.
    val tempFile = FileUtil.createTempFile("foo", "bar.txt")
    Files.write(tempFile.toPath(), ByteArray(10000))
    myDevice1.uploadChunkSize = 500
    val factory: FileChooserFactoryImpl = object : FileChooserFactoryImpl() {
      override fun createPathChooser(
        descriptor: FileChooserDescriptor,
        project: Project?,
        parent: Component?
      ): PathChooserDialog {
        return PathChooserDialog { toSelect: VirtualFile?, callback: Consumer<in List<VirtualFile?>> ->
          callback.consume(listOf(VirtualFileWrapper(tempFile).virtualFile))
        }
      }
    }
    ApplicationManager.getApplication().replaceService(
      FileChooserFactory::class.java, factory, androidProjectRule.testRootDisposable)

    // Give ourselves time to cancel
    myDevice1.uploadChunkIntervalMillis = 1_000
    myDevice1.uploadChunkSize = 100

    // Start the upload verify that a long-running operation is present
    myMockView.startTreeBusyIndicatorTacker.clear()
    myMockView.stopTreeBusyIndicatorTacker.clear()
    action.actionPerformed(e)
    pumpEventsAndWaitForFuture(myMockView.startTreeBusyIndicatorTacker.consume())
    assertThat(controller.checkLongRunningOperationAllowed()).isFalse()

    // Cancel upload
    myMockView.cancelTransfer()
    pumpEventsAndWaitForFuture(myMockView.stopTreeBusyIndicatorTacker.consume())

    // Verify that a long-running operation is no longer present
    assertThat(controller.checkLongRunningOperationAllowed()).isTrue()
  }

  @Test
  fun fileSystemTree_DropFile_SingleFile_Works() {
    // Prepare
    createControllerAndVerifyViewInitialState()
    expandEntry(myFoo)
    val path = getFileEntryPath(myFoo)
    val bounds = myMockView.tree.ui.getPathBounds(myMockView.tree, path)
    myMockView.tree.selectionPath = path
    val futureTreeChanged = createNodeExpandedFuture(myFoo)
    val tempFile = FileUtil.createTempFile("foo", "bar.txt")
    Files.write(tempFile.toPath(), ByteArray(10000))
    myDevice1.uploadChunkSize = 500
    myDevice1.uploadChunkIntervalMillis = 20
    val handler = myMockView.tree.transferHandler
    assertFalse(handler.canImport(myMockView.tree, arrayOf(DataFlavor.stringFlavor)))
    assertTrue(handler.canImport(myMockView.tree, arrayOf(DataFlavor.javaFileListFlavor)))
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
    val support = mock<TransferSupport>()
    val location = mock<TransferHandler.DropLocation>()
    whenever(location.dropPoint).thenReturn(Point(bounds.centerX.toInt(), bounds.centerY.toInt()))
    whenever(support.transferable).thenReturn(transferable)
    whenever(support.dropLocation).thenReturn(location)

    // Act
    myMockView.startTreeBusyIndicatorTacker.clear()
    myMockView.stopTreeBusyIndicatorTacker.clear()
    assertTrue(handler.importData(support))
    pumpEventsAndWaitForFuture(myMockView.startTreeBusyIndicatorTacker.consume())
    pumpEventsAndWaitForFuture(myMockView.uploadFilesTracker.consume())
    pumpEventsAndWaitForFuture(futureTreeChanged)
    pumpEventsAndWaitForFuture(myMockView.stopTreeBusyIndicatorTacker.consume())
    pumpEventsAndWaitForFuture(myMockView.reportMessageRelatedToNodeTracker.consume())

    // Assert
    // One node has been added
    val fooNode = DeviceFileEntryNode.fromNode(getFileEntryPath(myFoo).lastPathComponent)
    checkNotNull(fooNode)
    assertEquals(5, fooNode.childCount)
  }

  @Test
  fun fileSystemTree_ContextMenu_Upload_DirectoryAndFile_Works() {
    // Prepare
    createControllerAndVerifyViewInitialState()

    // Act
    expandEntry(myFoo)
    myMockView.tree.selectionPath = getFileEntryPath(myFoo)
    val futureTreeChanged = createNodeExpandedFuture(myFoo)
    val actionGroup = myMockView.fileTreeActionGroup
    val action = getActionByText(actionGroup, "Upload...")
    checkNotNull(action)
    val e = createContentMenuItemEvent()
    action.update(e)
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
        return PathChooserDialog { toSelect: VirtualFile?, callback: Consumer<in List<VirtualFile?>> ->
          val files = listOf(tempFile, tempDirectory).map { VirtualFileWrapper(it).virtualFile }
          callback.consume(files)
        }
      }
    }
    ApplicationManager.getApplication().replaceService(
      FileChooserFactory::class.java, factory, androidProjectRule.testRootDisposable)

    // Assert
    assertTrue(e.presentation.isVisible)
    assertTrue(e.presentation.isEnabled)

    // Act
    myMockView.startTreeBusyIndicatorTacker.clear()
    myMockView.stopTreeBusyIndicatorTacker.clear()
    action.actionPerformed(e)
    pumpEventsAndWaitForFuture(myMockView.startTreeBusyIndicatorTacker.consume())
    pumpEventsAndWaitForFuture(myMockView.uploadFilesTracker.consume())
    pumpEventsAndWaitForFuture(futureTreeChanged)
    pumpEventsAndWaitForFuture(myMockView.stopTreeBusyIndicatorTacker.consume())
    pumpEventsAndWaitForFuture(myMockView.reportMessageRelatedToNodeTracker.consume())

    // Assert
    // Two nodes have been added
    val fooNode = DeviceFileEntryNode.fromNode(getFileEntryPath(myFoo).lastPathComponent)
    checkNotNull(fooNode)
    assertEquals(6, fooNode.childCount)
  }

  @Test
  fun fileSystemTree_ContextMenu_Upload_ShowsProblems() {
    // Prepare
    createControllerAndVerifyViewInitialState()

    // Act
    expandEntry(myFoo)
    myMockView.tree.selectionPath = getFileEntryPath(myFoo)
    val futureTreeChanged = createNodeExpandedFuture(myFoo)
    val actionGroup = myMockView.fileTreeActionGroup
    val action = getActionByText(actionGroup, "Upload...")
    checkNotNull(action)
    val e = createContentMenuItemEvent()
    action.update(e)

    // Create 15 temporary files, so that we hit the "limit # of problems to display to 10" code path
    val tempFiles = (1..15).map { FileUtil.createTempFile("foo", ".txt") }

    // The "Choose file" dialog does not work in headless mode, so we register a custom
    // component that simply returns the tempFile we created above.
    val factory: FileChooserFactoryImpl = object : FileChooserFactoryImpl() {
      override fun createPathChooser(
        descriptor: FileChooserDescriptor,
        project: Project?,
        parent: Component?
      ): PathChooserDialog {
        return PathChooserDialog { toSelect: VirtualFile?, callback: Consumer<in List<VirtualFile?>?> ->
          val files = tempFiles.map { VirtualFileWrapper(it).virtualFile }
          callback.consume(files)
        }
      }
    }
    ApplicationManager.getApplication().replaceService(
      FileChooserFactory::class.java, factory, androidProjectRule.testRootDisposable)

    // Ensure file upload fails
    myDevice1.uploadError = AdbShellCommandException("Permission error")

    // Assert
    assertTrue(e.presentation.isVisible)
    assertTrue(e.presentation.isEnabled)

    // Act
    myMockView.startTreeBusyIndicatorTacker.clear()
    myMockView.stopTreeBusyIndicatorTacker.clear()
    action.actionPerformed(e)
    pumpEventsAndWaitForFuture(myMockView.startTreeBusyIndicatorTacker.consume())
    pumpEventsAndWaitForFuture(myMockView.uploadFilesTracker.consume())
    pumpEventsAndWaitForFuture(futureTreeChanged)
    pumpEventsAndWaitForFuture(myMockView.stopTreeBusyIndicatorTacker.consume())
    pumpEventsAndWaitForFuture(myMockView.reportErrorRelatedToNodeTracker.consume())

    // Assert
    // No node has been added
    val fooNode = DeviceFileEntryNode.fromNode(getFileEntryPath(myFoo).lastPathComponent)
    checkNotNull(fooNode)
    assertEquals(4, fooNode.childCount)
  }

  @Test
  fun fileSystemTree_ContextMenu_Synchronize_Works() {
    // Prepare
    createControllerAndVerifyViewInitialState()

    // Expand 2 directories
    expandEntry(myFoo)
    expandEntry(myFooDir)

    // Select 2 nodes, but do not select the "myFooDir" subdirectory, as synchronizing
    // its parent ("myFoo") show implicitly expand all its children too.
    myMockView.tree.selectionPath = getFileEntryPath(myFoo)
    myMockView.tree.addSelectionPath(getFileEntryPath(myFooFile2))
    val actionGroup = myMockView.fileTreeActionGroup
    val action = getActionByText(actionGroup, "Synchronize")
    checkNotNull(action)
    val e = createContentMenuItemEvent()
    action.update(e)
    assertTrue(e.presentation.isVisible)
    assertTrue(e.presentation.isEnabled)

    // Add 1 files in each expanded directory, check the tree does not show them yet
    myFoo.addFile("NewFile.txt")
    myFooDir.addFile("NewFile.txt")
    assertEquals(
      myFoo.mockEntries.size - 1,
      DeviceFileEntryNode.fromNode(getFileEntryPath(myFoo).lastPathComponent)?.childCount
    )
    assertEquals(
      myFooDir.mockEntries.size - 1,
      DeviceFileEntryNode.fromNode(getFileEntryPath(myFooDir).lastPathComponent)?.childCount
    )
    val futureMyFooChanged = createNodeExpandedFuture(myFoo)
    val futureMyFooDirChanged = createNodeExpandedFuture(myFooDir)

    // Act
    action.actionPerformed(e)

    // Assert
    pumpEventsAndWaitForFuture(myMockView.synchronizeNodesTracker.consume())
    pumpEventsAndWaitForFuture(futureMyFooChanged)
    pumpEventsAndWaitForFuture(futureMyFooDirChanged)
    assertEquals(
      myFoo.mockEntries.size,
      DeviceFileEntryNode.fromNode(getFileEntryPath(myFoo).lastPathComponent)?.childCount
    )
    assertEquals(
      myFooDir.mockEntries.size,
      DeviceFileEntryNode.fromNode(getFileEntryPath(myFooDir).lastPathComponent)?.childCount
    )
  }

  @Test
  fun treeNodeOrder() {
    val comparator = CustomComparator({ s -> s }, { s: String -> s.startsWith("D") })
    val l: List<String?> = listOf(null, "Dir3", "B1", "AbC", "abD", null, "Dir1", "DiR2")
    assertThat(l.sortedWith(comparator))
      .containsExactly(null, null, "Dir1", "DiR2", "Dir3", "AbC", "abD", "B1")
      .inOrder()
  }

  @Test
  fun openFileInEditorFailure() {
    // Prepare
    createControllerAndVerifyViewInitialState()

    // Act
    val errorMessage = "<Expected test error>"
    myMockFileManager.openFileInEditorError = RuntimeException(errorMessage)

    // Select node
    myMockView.tree.selectionPath = getFileEntryPath(myFile1)

    // Send a VK_ENTER key event
    fireEnterKey(myMockView.tree)
    pumpEventsAndWaitForFuture(myMockView.openNodesInEditorInvokedTracker.consume())
    pumpEventsAndWaitForFuture(myMockFileManager.downloadFileEntryTracker.consume())
    pumpEventsAndWaitForFuture(myMockFileManager.downloadFileEntryCompletionTracker.consume())
    val loadingError = pumpEventsAndWaitForFuture(myMockView.reportErrorRelatedToNodeTracker.consume())

    // Assert
    assertThat(loadingError).contains(errorMessage)
  }

  @Test
  fun customFileOpenerIsCalled() {
    // Prepare
    val openPath = arrayOf<Path?>(null)
    val controller = createController(fileOpener = { localPath -> openPath[0] = localPath })

    // Act
    setupControllerAndVerifyViewInitialState(controller)

    // Select node
    myMockView.tree.selectionPath = getFileEntryPath(myFile1)

    // Send a VK_ENTER key event
    fireEnterKey(myMockView.tree)
    pumpEventsAndWaitForFuture(myMockView.openNodesInEditorInvokedTracker.consume())
    pumpEventsAndWaitForFuture(myMockFileManager.downloadFileEntryTracker.consume())
    pumpEventsAndWaitForFuture(myMockFileManager.downloadFileEntryCompletionTracker.consume())

    // Assert
    assertTrue(openPath[0].toString().endsWith("file1.txt"))
  }

  private fun replaceTestDialog(showFunction: (String) -> Int) {
    val previousDialog = TestDialogManager.setTestDialog(showFunction)
    if (myInitialTestDialog == null) {
      myInitialTestDialog = previousDialog
    }
  }

  private fun replaceTestInputDialog(returnValue: String?) {
    val previousDialog = TestDialogManager.setTestInputDialog(object : TestInputDialog {
      override fun show(message: String): String? {
        return show(message, null)
      }

      override fun show(message: String, validator: InputValidator?): String? {
        validator?.checkInput(message)
        return returnValue
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
    var actionGroup = myMockView.fileTreeActionGroup
    val menuNames = StringUtil.split(menuPath, "/")
    for (i in 0 until menuNames.size - 1) {
      val subGroup = getSubGroup(actionGroup, menuNames[i])
      checkNotNull(subGroup)
      actionGroup = subGroup
    }
    val action = getActionByText(actionGroup, menuNames[menuNames.size - 1])
    checkNotNull(action)
    return action
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
        checkNotNull(entryNode)

        // Ensure this is the final event where we have all children (and not just the
        // "Loading..." child)
        if (entryNode != event.treePath.lastPathComponent) {
          return false
        }
        if (entryNode.childCount == 1 && entryNode.getChildAt(0) is ErrorNode) {
          return true
        }
        if (entryNode.childCount != entry.mockEntries.size) {
          return false
        }
        val nodes = entryNode.childEntryNodes.map { it.entry.name }.toSet()
        val entries = entry.mockEntries.map(MockDeviceFileEntry::name).toSet()
        return nodes == entries
      }
    }
    myMockView.tree.model.addTreeModelListener(treeModelAdapter)
    myEdtExecutor.addConsumer(isNodeExpandedFuture) { _, _ ->
      myMockView.tree.model.removeTreeModelListener(treeModelAdapter)
    }
    return isNodeExpandedFuture
  }

  private fun checkMockViewInitialState(activeDevice: MockDeviceFileSystem) {
    checkMockViewActiveDevice()

    // Check the file system tree is displaying the file system of the first device
    val rootEntry = DeviceFileEntryNode.fromNode(myMockView.tree.model.root)
    checkNotNull(rootEntry)
    assertEquals(activeDevice.root, rootEntry.entry)

    pumpEventsAndWaitForFuture(myMockView.treeNodesInsertedTacker.consume())
    assertEquals(
      "mock: ${activeDevice.root.mockEntries} rootEntry: " + TreeUtil.getChildren(rootEntry).collect(Collectors.toList()),
      activeDevice.root.mockEntries.size, rootEntry.childCount
    )
  }

  private fun checkMockViewActiveDevice() {
    pumpEventsAndWaitForFuture(myMockView.startTreeBusyIndicatorTacker.consume())
    pumpEventsAndWaitForFuture(myMockView.stopTreeBusyIndicatorTacker.consume())

    // The root node should have been expanded to show the first level of children
    pumpEventsAndWaitForFuture(myMockView.treeNodeExpandingTracker.consume())

    // Check the file system tree is showing the first level of entries of the file system
    pumpEventsAndWaitForFuture(myMockView.treeModelChangedTracker.consume())
  }

  private fun setupControllerAndVerifyViewInitialState(controller: DeviceFileExplorerController) {
    controller.setup()
    controller.setActiveConnectedDevice(myDevice1)
    checkMockViewInitialState(myDevice1)
  }

  private fun createControllerAndVerifyViewInitialState(): DeviceFileExplorerController {
    // Prepare
    val controller = createController()

    // Act
    setupControllerAndVerifyViewInitialState(controller)
    return controller
  }

  private fun downloadFile(file: MockDeviceFileEntry, trigger: Runnable): VirtualFile {
    myDevice1.downloadChunkSize = 1000 // download chunks of 1000 bytes at a time
    myDevice1.downloadChunkIntervalMillis = 10 // wait 10 millis between each 1000 bytes chunk
    // Setting the size to 200_000 bytes should force the download to take ~2 seconds,
    // i.e. 200 chunks of 1000 bytes at 100 chunks per second.
    // This allows us to cover the code that animates nodes UI during download.
    file.size = 200000

    // Select node
    val file1Path = getFileEntryPath(file)
    myMockView.tree.selectionPath = file1Path
    trigger.run()

    // Assert
    pumpEventsAndWaitForFuture(myMockFileManager.downloadFileEntryTracker.consume())
    return pumpEventsAndWaitForFuture(myMockFileManager.downloadFileEntryCompletionTracker.consume())
  }

  private fun expandEntry(entry: MockDeviceFileEntry) {
    // Attach listener for node expansion completion
    val futureNodeExpanded = createNodeExpandedFuture(entry)

    // Expand node
    myMockView.tree.expandPath(getFileEntryPath(entry))

    // Wait for tree node to be expanded
    pumpEventsAndWaitForFuture(futureNodeExpanded)
    pumpEventsAndWaitForFuture(myMockView.startTreeBusyIndicatorTacker.consume())
    pumpEventsAndWaitForFuture(myMockView.stopTreeBusyIndicatorTacker.consume())
  }

  private fun createController(
    view: DeviceFileExplorerView = myMockView,
    deviceExplorerFileManager: DeviceExplorerFileManager = myMockFileManager,
    fileOpener: suspend (Path) -> Unit = myMockFileManager::openFile
  ): DeviceFileExplorerController {
    return DeviceFileExplorerController(project, myModel, view, deviceExplorerFileManager,
                                    object : DeviceFileExplorerController.FileOpener {
                                      override suspend fun openFile(localPath: Path) { fileOpener(localPath) }
                                    })
  }

  /**
   * Returns the [TreePath] corresponding to a given [DeviceFileEntry].
   * Throws an exception if the file entry is not found.
   */
  private fun getFileEntryPath(entry: MockDeviceFileEntry): TreePath {
    val entries = getEntryStack(entry)
    val nodes: MutableList<DeviceFileEntryNode> = ArrayList()
    var currentNode = checkNotNull(DeviceFileEntryNode.fromNode(myMockView.tree.model.root))
    var currentEntry = entries.pop()
    assertEquals(currentNode.entry, currentEntry)
    nodes.add(currentNode)
    while (!entries.isEmpty()) {
      val newEntry = entries.pop()
      currentEntry = null
      for (i in 0 until myMockView.tree.model.getChildCount(currentNode)) {
        val newNode = checkNotNull(DeviceFileEntryNode.fromNode(myMockView.tree.model.getChild(currentNode, i)))
        if (newNode.entry === newEntry) {
          currentNode = newNode
          currentEntry = newEntry
          break
        }
      }
      checkNotNull(currentEntry) { "File System Tree does not contain node \"${entry.fullPath}\"" }
      nodes.add(currentNode)
    }
    return TreePath(nodes.toTypedArray())
  }

  companion object {
    private fun <V> enumerationAsList(e: Enumeration<V>): List<V> {
      return Collections.list(e)
    }

    private fun createContentMenuItemEvent(): AnActionEvent {
      return AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, { dataId -> null })
    }

    private fun getActionByText(actionGroup: ActionGroup, text: String): AnAction? {
      val e = createContentMenuItemEvent()
      return actionGroup.getChildren(null).firstOrNull { action ->
        action.update(e)
        text == e.presentation.text
      }
    }

    private fun getSubGroup(actionGroup: ActionGroup, name: String): ActionGroup? {
      return actionGroup.getChildren(null)
          .filterIsInstance<ActionGroup>()
          .firstOrNull { name == it.templatePresentation.text }
    }

    private fun getEntryStack(entry: MockDeviceFileEntry): Stack<MockDeviceFileEntry> {
      val entries = Stack<MockDeviceFileEntry>()
      generateSequence(entry) { it.parent }.forEach { entries.add(it) }
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