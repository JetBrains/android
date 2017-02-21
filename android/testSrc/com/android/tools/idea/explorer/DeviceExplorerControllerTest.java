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
package com.android.tools.idea.explorer;

import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.explorer.fs.DeviceFileSystem;
import com.android.tools.idea.explorer.fs.DeviceFileSystemService;
import com.android.tools.idea.explorer.mocks.*;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.ide.ClipboardSynchronizer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestInputDialog;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.UIBundle;
import com.intellij.util.ui.tree.TreeModelAdapter;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mockito;
import org.picocontainer.MutablePicoContainer;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public class DeviceExplorerControllerTest extends AndroidTestCase {
  private static final long TIMEOUT_MILLISECONDS = 30_000;

  private DeviceExplorerModel myModel;
  private MockDeviceExplorerView myMockView;
  private MockDeviceFileSystemService myMockService;
  private MockDeviceExplorerFileManager myMockFileManager;
  private MockDeviceFileSystem myDevice1;
  private MockDeviceFileEntry myFoo;
  private MockDeviceFileEntry myFile1;
  private MockDeviceFileSystem myDevice2;
  private RepaintManager myMockRepaintManager;
  private MockDeviceFileEntry myFooDir;
  private TestDialog myInitialTestDialog;
  private TestInputDialog myInitialTestInputDialog;
  private FutureCallbackExecutor myEdtExecutor;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(getProject());
    ToolWindow toolWindow = toolWindowManager.registerToolWindow(
      DeviceExplorerToolWindowFactory.TOOL_WINDOW_ID, false, ToolWindowAnchor.RIGHT, getProject(), true);

    myEdtExecutor = new FutureCallbackExecutor(EdtExecutor.INSTANCE);
    myModel = new DeviceExplorerModel();
    myMockService = new MockDeviceFileSystemService(getProject(), myEdtExecutor);
    myMockView = new MockDeviceExplorerView(getProject(), toolWindow, new MockDeviceFileSystemRenderer(), myModel);
    myMockFileManager = new MockDeviceExplorerFileManager(getProject(), myEdtExecutor);

    myDevice1 = myMockService.addDevice("TestDevice-1");
    myFoo = myDevice1.getRoot().addDirectory("Foo");
    myFoo.addFile("fooFile1.txt");
    myFoo.addFile("fooFile2.txt");
    myFoo.addFileLink("fooLink1.txt", "fooFile1.txt");
    myFooDir = myFoo.addDirectory("fooDir");
    myFile1 = myDevice1.getRoot().addFile("file1.txt");
    myDevice1.getRoot().addFile("file2.txt");
    myDevice1.getRoot().addFile("file3.txt");

    myDevice2 = myMockService.addDevice("TestDevice-2");
    myDevice2.getRoot().addDirectory("Foo2");
    myDevice2.getRoot().addFile("foo2File1.txt");
    myDevice2.getRoot().addFile("foo2File2.txt");
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      RepaintManager.setCurrentManager(null);
      myMockRepaintManager = null;

      if (myInitialTestDialog != null) {
        Messages.setTestDialog(myInitialTestDialog);
      }

      if (myInitialTestInputDialog != null) {
        Messages.setTestInputDialog(myInitialTestInputDialog);
      }

      if (myMockFileManager != null) {
        Disposer.dispose(myMockFileManager);
        myMockFileManager = null;
      }
      ClipboardSynchronizer.getInstance().resetContent();
    }
    finally {
      super.tearDown();
    }
  }

  private void injectRepaintManagerMock() {
    RepaintManager current = RepaintManager.currentManager(null);
    assert current != null;
    myMockRepaintManager = Mockito.spy(current);
    RepaintManager.setCurrentManager(myMockRepaintManager);
  }

  public void testStartController() throws InterruptedException, ExecutionException, TimeoutException {
    // Prepare
    DeviceExplorerController controller = createController();

    // Act
    controller.setup();
    pumpEventsAndWaitForFuture(myMockView.getServiceSetupSuccessTracker().consume());

    // Assert
    checkMockViewInitialState(controller, myDevice1);
  }

  public void testStartControllerFailure() throws InterruptedException, ExecutionException, TimeoutException {
    // Prepare
    String setupErrorMessage = "<Unique error message>";
    DeviceFileSystemService service = Mockito.mock(DeviceFileSystemService.class);
    Mockito.when(service.start()).thenReturn(Futures.immediateFailedFuture(new RuntimeException(setupErrorMessage)));
    DeviceExplorerController controller = createController(myMockView, service);

    // Act
    controller.setup();
    String errorMessage = pumpEventsAndWaitForFuture(myMockView.getReportErrorRelatedToServiceTracker().consume());

    // Assert
    assertNotNull(errorMessage);
    assertTrue(errorMessage.contains(setupErrorMessage));
  }

  public void testStartControllerUnexpectedFailure() throws InterruptedException, ExecutionException, TimeoutException {
    // Prepare
    DeviceFileSystemService service = Mockito.mock(DeviceFileSystemService.class);
    Mockito.when(service.start()).thenReturn(Futures.immediateFailedFuture(new RuntimeException()));
    DeviceExplorerController controller = createController(myMockView, service);

    // Act
    controller.setup();
    String errorMessage = pumpEventsAndWaitForFuture(myMockView.getReportErrorRelatedToServiceTracker().consume());

    // Assert
    assertNotNull(errorMessage);
    assertTrue(errorMessage.contains("Unable to start file system service"));
  }

  public void testRestartController() throws InterruptedException, ExecutionException, TimeoutException {
    // Prepare
    DeviceExplorerController controller = createController();

    controller.setup();
    pumpEventsAndWaitForFuture(myMockView.getServiceSetupSuccessTracker().consume());
    checkMockViewInitialState(controller, myDevice1);

    // Act
    controller.restartService();
    pumpEventsAndWaitForFuture(myMockView.getAllDevicesRemovedTracker().consume());

    // Assert
    checkMockViewInitialState(controller, myDevice1);
  }

  public void testRestartControllerFailure() throws InterruptedException, ExecutionException, TimeoutException {
    // Prepare
    String setupErrorMessage = "<Unique error message>";
    DeviceFileSystemService service = Mockito.mock(DeviceFileSystemService.class);
    Mockito.when(service.start()).thenReturn(Futures.immediateFuture(null));
    Mockito.when(service.restart()).thenReturn(Futures.immediateFailedFuture(new RuntimeException(setupErrorMessage)));
    Mockito.when(service.getDevices()).thenReturn(Futures.immediateFuture(new ArrayList<>()));
    DeviceExplorerController controller = createController(myMockView, service);

    // Act
    controller.setup();
    controller.restartService();
    String errorMessage = pumpEventsAndWaitForFuture(myMockView.getReportErrorRelatedToServiceTracker().consume());

    // Assert
    assertNotNull(errorMessage);
    assertTrue(errorMessage.contains(setupErrorMessage));
  }

  public void testGetDevicesFailure() throws InterruptedException, ExecutionException, TimeoutException {
    // Prepare
    String setupErrorMessage = "<Unique error message>";
    DeviceFileSystemService service = Mockito.mock(DeviceFileSystemService.class);
    Mockito.when(service.start()).thenReturn(Futures.immediateFuture(null));
    Mockito.when(service.getDevices()).thenReturn(Futures.immediateFailedFuture(new RuntimeException(setupErrorMessage)));
    DeviceExplorerController controller = createController(myMockView, service);

    // Act
    controller.setup();
    String errorMessage = pumpEventsAndWaitForFuture(myMockView.getReportErrorRelatedToServiceTracker().consume());

    // Assert
    assertNotNull(errorMessage);
    assertTrue(errorMessage.contains(errorMessage));
  }

  public void testGetRootDirectoryFailure() throws InterruptedException, ExecutionException, TimeoutException {
    // Prepare
    String setupErrorMessage = "<Unique error message>";
    myDevice1.setRootDirectoryError(new RuntimeException(setupErrorMessage));
    DeviceExplorerController controller = createController();

    // Act
    controller.setup();
    pumpEventsAndWaitForFuture(myMockView.getServiceSetupSuccessTracker().consume());

    checkMockViewComboBox(controller);
    String errorMessage = pumpEventsAndWaitForFuture(myMockView.getReportErrorRelatedToDeviceTracker().consume());

    // Assert
    assertNotNull(errorMessage);
    assertTrue(errorMessage.contains(setupErrorMessage));
  }

  public void testExpandChildren() throws InterruptedException, ExecutionException, TimeoutException, ExpandVetoException {
    // Prepare
    DeviceExplorerController controller = createController();

    // Act
    controller.setup();
    pumpEventsAndWaitForFuture(myMockView.getServiceSetupSuccessTracker().consume());
    checkMockViewInitialState(controller, myDevice1);

    // Set timers to ensure the "loading..." animation code is hit
    controller.setShowLoadingNodeDelayMillis(10);
    controller.setDownloadingNodeRepaintMillis(10);
    myFoo.setGetEntriesTimeoutMillis(1_000);

    // Listen to node expansion effect (structure changed event)
    TreePath fooPath = getFileEntryPath(myFoo);
    SettableFuture<TreePath> futureNodeExpanded = createNodeExpandedFuture(myFoo);
    SettableFuture<MyLoadingNode> futureTreeNodesChanged = SettableFuture.create();
    myMockView.getTree().getModel().addTreeModelListener(new TreeModelAdapter() {
      @Override
      public void treeNodesChanged(TreeModelEvent event) {
        if (Objects.equals(fooPath.getLastPathComponent(), event.getTreePath().getLastPathComponent())) {
          Object[] children = event.getChildren();
          if (children != null && children.length == 1) {
            Object child = children[0];
            if (child instanceof MyLoadingNode) {
              futureTreeNodesChanged.set((MyLoadingNode)child);
            }
          }
        }
      }
    });

    // Expand node
    myMockView.getTree().expandPath(fooPath);

    // Wait for tree node to be expanded
    MyLoadingNode myLoadingNode = pumpEventsAndWaitForFuture(futureTreeNodesChanged);
    TreePath nodeExpandedPath = pumpEventsAndWaitForFuture(futureNodeExpanded);

    // Assert
    assertTrue(myLoadingNode.getDownloadingTick() > 1);
    assertEquals(fooPath.getLastPathComponent(), nodeExpandedPath.getLastPathComponent());
  }

  public void testExpandChildrenFailure() throws InterruptedException, ExecutionException, TimeoutException {
    // Prepare
    DeviceExplorerController controller = createController();
    String errorMessage = "<Expected test error>";
    myDevice1.getRoot().setGetEntriesError(new RuntimeException(errorMessage));

    // Act
    controller.setup();
    pumpEventsAndWaitForFuture(myMockView.getServiceSetupSuccessTracker().consume());
    String loadingError = pumpEventsAndWaitForFuture(myMockView.getReportErrorRelatedToNodeTracker().consume());

    // Assert
    assertNotNull(loadingError);
    assertTrue(loadingError.contains(errorMessage));
  }

  public void testDownloadFileWithEnterKey() throws Exception {
    downloadFile(() -> {
      // Send a VK_ENTER key event
      fireEnterKey(myMockView.getTree());

      pumpEventsAndWaitForFuture(myMockView.getOpenNodeInEditorInvokedTracker().consume());
    });
    pumpEventsAndWaitForFuture(myMockFileManager.getOpenFileInEditorTracker().consume());
  }

  public void testDownloadFileWithMouseClick() throws Exception {
    downloadFile(() -> {
      TreePath path = getFileEntryPath(myFile1);
      Rectangle pathBounds = myMockView.getTree().getPathBounds(path);
      assert pathBounds != null;

      // Fire double-click event
      fireDoubleClick(myMockView.getTree(), pathBounds.x, pathBounds.y);

      pumpEventsAndWaitForFuture(myMockView.getOpenNodeInEditorInvokedTracker().consume());
    });
    pumpEventsAndWaitForFuture(myMockFileManager.getOpenFileInEditorTracker().consume());
  }

  public void testDownloadFileFailure() throws InterruptedException, ExecutionException, TimeoutException {
    // Prepare
    DeviceExplorerController controller = createController();

    // Act
    controller.setup();
    pumpEventsAndWaitForFuture(myMockView.getServiceSetupSuccessTracker().consume());
    checkMockViewInitialState(controller, myDevice1);

    String errorMessage = "<Expected test error>";
    myDevice1.setDownloadError(new RuntimeException(errorMessage));

    // Select node
    myMockView.getTree().setSelectionPath(getFileEntryPath(myFile1));

    // Send a VK_ENTER key event
    fireEnterKey(myMockView.getTree());
    pumpEventsAndWaitForFuture(myMockView.getOpenNodeInEditorInvokedTracker().consume());

    pumpEventsAndWaitForFuture(myMockFileManager.getDownloadFileEntryTracker().consume());
    Throwable t = pumpEventsAndWaitForFutureException(myMockFileManager.getDownloadFileEntryCompletionTracker().consume());
    String loadingError = pumpEventsAndWaitForFuture(myMockView.getReportErrorRelatedToNodeTracker().consume());

    // Assert
    assertNotNull(t);
    assertNotNull(loadingError);
    assertTrue(loadingError.contains(errorMessage));
  }

  public void testOpenFileInEditorFailure() throws InterruptedException, ExecutionException, TimeoutException {
    // Prepare
    DeviceExplorerController controller = createController();

    // Act
    controller.setup();
    pumpEventsAndWaitForFuture(myMockView.getServiceSetupSuccessTracker().consume());
    checkMockViewInitialState(controller, myDevice1);

    String errorMessage = "<Expected test error>";
    myMockFileManager.setOpenFileInEditorError(new RuntimeException(errorMessage));

    // Select node
    myMockView.getTree().setSelectionPath(getFileEntryPath(myFile1));

    // Send a VK_ENTER key event
    fireEnterKey(myMockView.getTree());
    pumpEventsAndWaitForFuture(myMockView.getOpenNodeInEditorInvokedTracker().consume());

    pumpEventsAndWaitForFuture(myMockFileManager.getDownloadFileEntryTracker().consume());
    pumpEventsAndWaitForFuture(myMockFileManager.getDownloadFileEntryCompletionTracker().consume());
    String loadingError = pumpEventsAndWaitForFuture(myMockView.getReportErrorRelatedToNodeTracker().consume());

    // Assert
    assertNotNull(loadingError);
    assertTrue(loadingError.contains(errorMessage));
  }

  public void testChangeActiveDevice() throws InterruptedException, ExecutionException, TimeoutException {
    // Prepare
    DeviceExplorerController controller = createController();

    // Act
    controller.setup();
    pumpEventsAndWaitForFuture(myMockView.getServiceSetupSuccessTracker().consume());
    checkMockViewInitialState(controller, myDevice1);

    myMockView.getDeviceCombo().setSelectedItem(myDevice2);

    // Assert
    checkMockViewActiveDevice(myDevice2);
  }

  public void testUpdateActiveDeviceState() throws InterruptedException, ExecutionException, TimeoutException {
    // Prepare
    DeviceExplorerController controller = createController();
    controller.setup();
    pumpEventsAndWaitForFuture(myMockView.getServiceSetupSuccessTracker().consume());
    checkMockViewInitialState(controller, myDevice1);
    TreeModel model = myModel.getTreeModel();

    // Act
    injectRepaintManagerMock();
    Arrays.stream(myMockService.getListeners()).forEach(l -> l.deviceUpdated(myDevice1));
    pumpEventsAndWaitForFuture(myMockView.getDeviceUpdatedTracker().consume());

    // Assert
    // Check there was no update to the underlying model, and that only
    // the combo box UI has been invalidated.
    assertEquals(myDevice1, myModel.getActiveDevice());
    assertEquals(model, myModel.getTreeModel());
    Mockito.verify(myMockRepaintManager).addDirtyRegion(myMockView.getDeviceCombo(),
                                                        0,
                                                        0,
                                                        myMockView.getDeviceCombo().getWidth(),
                                                        myMockView.getDeviceCombo().getHeight());
  }

  public void testAddDevice() throws InterruptedException, ExecutionException, TimeoutException {
    // Prepare
    DeviceExplorerController controller = createController();

    // Act
    controller.setup();
    pumpEventsAndWaitForFuture(myMockView.getServiceSetupSuccessTracker().consume());
    checkMockViewInitialState(controller, myDevice1);

    SettableFuture<Void> futureItemAdded = SettableFuture.create();
    myMockView.getDeviceCombo().getModel().addListDataListener(new ListDataListener() {
      @Override
      public void intervalAdded(ListDataEvent e) {
        futureItemAdded.set(null);
      }

      @Override
      public void intervalRemoved(ListDataEvent e) {
      }

      @Override
      public void contentsChanged(ListDataEvent e) {
      }
    });
    DeviceFileSystem newFileSystem = myMockService.addDevice("TestDevice-3");
    DeviceFileSystem addedFileSystem = pumpEventsAndWaitForFuture(myMockView.getDeviceAddedTracker().consume());
    pumpEventsAndWaitForFuture(futureItemAdded);

    // Assert
    assertEquals(newFileSystem, addedFileSystem);
    assertEquals(3, myMockView.getDeviceCombo().getItemCount());
  }

  public void testRemoveActiveDevice() throws InterruptedException, ExecutionException, TimeoutException {
    // Prepare
    DeviceExplorerController controller = createController();

    // Act
    controller.setup();
    pumpEventsAndWaitForFuture(myMockView.getServiceSetupSuccessTracker().consume());
    checkMockViewInitialState(controller, myDevice1);

    SettableFuture<Void> futureItemRemoved = SettableFuture.create();
    myMockView.getDeviceCombo().getModel().addListDataListener(new ListDataListener() {
      @Override
      public void intervalAdded(ListDataEvent e) {
      }

      @Override
      public void intervalRemoved(ListDataEvent e) {
        futureItemRemoved.set(null);
      }

      @Override
      public void contentsChanged(ListDataEvent e) {
      }
    });
    assertTrue(myMockService.removeDevice(myDevice1));
    pumpEventsAndWaitForFuture(myMockView.getDeviceRemovedTracker().consume());
    pumpEventsAndWaitForFuture(futureItemRemoved);

    // Assert
    assertEquals(1, myMockView.getDeviceCombo().getItemCount());
    checkMockViewActiveDevice(myDevice2);
  }

  public void testFileSystemTree_ContextMenu_Items_Present() throws InterruptedException, ExecutionException, TimeoutException {
    // Prepare
    DeviceExplorerController controller = createController();

    // Act
    controller.setup();
    pumpEventsAndWaitForFuture(myMockView.getServiceSetupSuccessTracker().consume());
    checkMockViewInitialState(controller, myDevice1);

    // Assert
    ActionGroup actionGroup = myMockView.getFileTreeActionGroup();
    assertEquals(6, actionGroup.getChildren(null).length);

    ActionGroup subGroup = getSubGroup(actionGroup, "New");
    assertNotNull(subGroup);
    assertEquals(2, subGroup.getChildren(null).length);

    // Act: Call "update" on each action, just to make sure the code is covered
    myMockView.getTree().setSelectionPath(getFileEntryPath(myFile1));
    List<AnAction> actions = Arrays.asList(actionGroup.getChildren(null));
    AnActionEvent e = createContentMenuItemEvent();
    actions.forEach(x -> x.update(e));
  }

  public void testFileSystemTree_ContextMenu_Open_Works() throws Exception {
    downloadFile(() -> {
      ActionGroup actionGroup = myMockView.getFileTreeActionGroup();
      AnAction action = getActionByText(actionGroup, "Open");
      assertNotNull(action);
      AnActionEvent e = createContentMenuItemEvent();
      action.update(e);
      // Assert
      assertTrue(e.getPresentation().isVisible());
      assertTrue(e.getPresentation().isEnabled());

      // Act
      action.actionPerformed(e);

      // Assert
      pumpEventsAndWaitForFuture(myMockView.getOpenNodeInEditorInvokedTracker().consume());
    });
    pumpEventsAndWaitForFuture(myMockFileManager.getOpenFileInEditorTracker().consume());
  }

  public void testFileSystemTree_ContextMenu_SaveAs_Works() throws Exception {
    File tempFile = FileUtil.createTempFile("foo", "bar");

    downloadFile(() -> {
      // Prepare
      // The "Save As" dialog does not work in headless mode, so we register a custom
      // comonent that simply returns the tempFile we created above.
      replaceApplicationComponent(FileChooserFactory.class, new FileChooserFactoryImpl() {
        @NotNull
        @Override
        public FileSaverDialog createSaveFileDialog(@NotNull FileSaverDescriptor descriptor, @Nullable Project project) {
          return (baseDir, filename) -> new VirtualFileWrapper(tempFile);
        }
      });

      // Invoke "Save As..." content menu
      ActionGroup actionGroup = myMockView.getFileTreeActionGroup();
      AnAction action = getActionByText(actionGroup, "Save As...");
      assertNotNull(action);
      AnActionEvent e = createContentMenuItemEvent();
      action.update(e);

      // Assert
      assertTrue(e.getPresentation().isVisible());
      assertTrue(e.getPresentation().isEnabled());

      // Act
      action.actionPerformed(e);

      // Assert
      pumpEventsAndWaitForFuture(myMockView.getSaveNodeAsTracker().consume());
    });

    // Assert
    assertTrue(tempFile.exists());
    assertEquals(200_000, tempFile.length());
  }

  public void testFileSystemTree_ContextMenu_New_IsHiddenForFiles() throws Exception {
    // Prepare
    DeviceExplorerController controller = createController();
    controller.setup();
    pumpEventsAndWaitForFuture(myMockView.getServiceSetupSuccessTracker().consume());
    checkMockViewInitialState(controller, myDevice1);

    // Act
    myMockView.getTree().setSelectionPath(getFileEntryPath(myFile1));

    // Assert
    checkContextMenuItemVisible("New/File", false);
    checkContextMenuItemVisible("New/Directory", false);
  }

  public void testFileSystemTree_ContextMenu_New_IsVisibleForDirectories() throws Exception {
    // Prepare
    DeviceExplorerController controller = createController();
    controller.setup();
    pumpEventsAndWaitForFuture(myMockView.getServiceSetupSuccessTracker().consume());
    checkMockViewInitialState(controller, myDevice1);

    // Act
    expandEntry(myFoo);
    myMockView.getTree().setSelectionPath(getFileEntryPath(myFooDir));

    // Assert
    checkContextMenuItemVisible("New/File", true);
    checkContextMenuItemVisible("New/Directory", true);
  }

  public void testFileSystemTree_ContextMenu_NewFile_Works() throws Exception {
    // Prepare
    DeviceExplorerController controller = createController();
    controller.setup();
    pumpEventsAndWaitForFuture(myMockView.getServiceSetupSuccessTracker().consume());
    checkMockViewInitialState(controller, myDevice1);

    expandEntry(myFoo);
    TreePath fooDirPath = getFileEntryPath(myFooDir);
    myMockView.getTree().setSelectionPath(fooDirPath);
    SettableFuture<TreePath> fooDirExpandedFuture = createNodeExpandedFuture(myFooDir);
    String newFileName = "foobar.txt";
    replaceTestInputDialog(newFileName);

    // Act
    AnAction action = getContextMenuAction("New/File");
    AnActionEvent e = createContentMenuItemEvent();
    action.update(e);
    action.actionPerformed(e);

    // Assert
    pumpEventsAndWaitForFuture(fooDirExpandedFuture);

    // Look for the new file entry in the tree view
    DeviceFileEntryNode newChild = enumerationAsList(((DeviceFileEntryNode)fooDirPath.getLastPathComponent()).children())
      .stream()
      .filter(x -> x instanceof DeviceFileEntryNode)
      .map(x -> (DeviceFileEntryNode)x)
      .filter(x -> Objects.equals(newFileName, x.getEntry().getName()) && x.getEntry().isFile())
      .findFirst()
      .orElse(null);
    assertNotNull(newChild);
  }

  public void testFileSystemTree_ContextMenu_NewDirectory_Works() throws Exception {
    // Prepare
    DeviceExplorerController controller = createController();
    controller.setup();
    pumpEventsAndWaitForFuture(myMockView.getServiceSetupSuccessTracker().consume());
    checkMockViewInitialState(controller, myDevice1);

    expandEntry(myFoo);
    TreePath fooDirPath = getFileEntryPath(myFooDir);
    myMockView.getTree().setSelectionPath(fooDirPath);
    SettableFuture<TreePath> fooDirExpandedFuture = createNodeExpandedFuture(myFooDir);
    String newDirectoryName = "foobar.txt";
    replaceTestInputDialog(newDirectoryName);

    // Act
    AnAction action = getContextMenuAction("New/Directory");
    AnActionEvent e = createContentMenuItemEvent();
    action.update(e);
    action.actionPerformed(e);

    // Assert
    pumpEventsAndWaitForFuture(fooDirExpandedFuture);

    // Look for the new file entry in the tree view
    DeviceFileEntryNode newChild = enumerationAsList(((DeviceFileEntryNode)fooDirPath.getLastPathComponent()).children())
      .stream()
      .filter(x -> x instanceof DeviceFileEntryNode)
      .map(x -> (DeviceFileEntryNode)x)
      .filter(x -> Objects.equals(newDirectoryName, x.getEntry().getName()) && x.getEntry().isDirectory())
      .findFirst()
      .orElse(null);
    assertNotNull(newChild);
  }

  public void testFileSystemTree_ContextMenu_NewDirectory_ExistingPath_Fails() throws Exception {
    // Prepare
    DeviceExplorerController controller = createController();
    controller.setup();
    pumpEventsAndWaitForFuture(myMockView.getServiceSetupSuccessTracker().consume());
    checkMockViewInitialState(controller, myDevice1);

    TreePath fooPath = getFileEntryPath(myFoo);
    myMockView.getTree().setSelectionPath(fooPath);
    String newDirectoryName = myFooDir.getName(); // Existing name to create conflict
    replaceTestInputDialog(newDirectoryName);
    SettableFuture<String> futureMessageDialog = SettableFuture.create();
    replaceTestDialog(s -> {
      futureMessageDialog.set(s);

      // Simulate a "Cancel" dialog in the "New Folder Name" dialog, since the controller
      // shows the "New Folder Name" dialog as long as an error is detected when
      // creating the new folder.
      replaceTestInputDialog(null);
      return 0;
    });

    // Act
    AnAction action = getContextMenuAction("New/Directory");
    AnActionEvent e = createContentMenuItemEvent();
    action.update(e);
    action.actionPerformed(e);

    // Assert
    String message = pumpEventsAndWaitForFuture(futureMessageDialog);
    assertNotNull(message);
    assertTrue(message.contains(UIBundle.message("create.new.folder.could.not.create.folder.error.message", newDirectoryName)));

    // Ensure entry does not exist in tree view
    DeviceFileEntryNode newChild = enumerationAsList(((DeviceFileEntryNode)fooPath.getLastPathComponent()).children())
      .stream()
      .filter(x -> x instanceof DeviceFileEntryNode)
      .map(x -> (DeviceFileEntryNode)x)
      .filter(x -> Objects.equals(newDirectoryName, x.getEntry().getName()) && x.getEntry().isDirectory())
      .findFirst()
      .orElse(null);
    assertNull(newChild);
  }

  public void testFileSystemTree_ContextMenu_CopyPath_Works() throws Exception {
    // Prepare
    DeviceExplorerController controller = createController();

    // Act
    controller.setup();
    pumpEventsAndWaitForFuture(myMockView.getServiceSetupSuccessTracker().consume());
    checkMockViewInitialState(controller, myDevice1);

    myMockView.getTree().setSelectionPath(getFileEntryPath(myFile1));

    ActionGroup actionGroup = myMockView.getFileTreeActionGroup();
    AnAction action = getActionByText(actionGroup, "Copy Path");
    assertNotNull(action);
    AnActionEvent e = createContentMenuItemEvent();
    action.update(e);

    // Assert
    assertTrue(e.getPresentation().isVisible());
    assertTrue(e.getPresentation().isEnabled());

    // Act
    action.actionPerformed(e);
    pumpEventsAndWaitForFuture(myMockView.getCopyNodePathTracker().consume());

    // Assert
    Transferable contents = CopyPasteManager.getInstance().getContents();
    assertNotNull(contents);
    assertEquals("/" + myFile1.getName(), contents.getTransferData(DataFlavor.stringFlavor));
  }

  private static <V> List<V> enumerationAsList(Enumeration e) {
    //noinspection unchecked
    return Collections.list(e);
  }

  @SuppressWarnings("SameParameterValue")
  private void replaceTestDialog(@NotNull Function<String, Integer> showFunction) {
    TestDialog previousDialog = Messages.setTestDialog(showFunction::apply);
    if (myInitialTestDialog == null) {
      myInitialTestDialog = previousDialog;
    }
  }

  @SuppressWarnings("SameParameterValue")
  private void replaceTestInputDialog(@Nullable String returnValue) {
    TestInputDialog previousDialog = Messages.setTestInputDialog(new TestInputDialog() {
      @Override
      public String show(String message) {
        return show(message, null);
      }

      @Override
      public String show(String message, @Nullable InputValidator validator) {
        if (validator != null) {
          validator.checkInput(message);
        }
        return returnValue;
      }
    });

    if (myInitialTestInputDialog == null) {
      myInitialTestInputDialog = previousDialog;
    }
  }

  /**
   * Replace an application component with a custom component instance
   */
  private static <T> void replaceApplicationComponent(Class<T> cls, T instance) {
    String key = cls.getName();
    MutablePicoContainer container = (MutablePicoContainer)ApplicationManager.getApplication().getPicoContainer();
    container.unregisterComponent(key);
    container.registerComponentInstance(key, instance);
  }

  @NotNull
  private static AnActionEvent createContentMenuItemEvent() {
    return AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, dataId -> null);
  }

  @Nullable
  private static AnAction getActionByText(@NotNull ActionGroup actionGroup, @NotNull String text) {
    AnActionEvent e = createContentMenuItemEvent();
    return Arrays.stream(actionGroup.getChildren(null))
      .filter(x -> {
        x.update(e);
        return Objects.equals(text, e.getPresentation().getText());
      })
      .findFirst()
      .orElseGet(() -> null);
  }

  @SuppressWarnings("SameParameterValue")
  @Nullable
  private static ActionGroup getSubGroup(@NotNull ActionGroup actionGroup, @NotNull String name) {
    return Arrays.stream(actionGroup.getChildren(null))
      .filter(x -> x instanceof ActionGroup)
      .map(x -> (ActionGroup)x)
      .filter(x -> Objects.equals(name, x.getTemplatePresentation().getText()))
      .findFirst()
      .orElse(null);
  }

  private void checkContextMenuItemVisible(@NotNull String menuPath, boolean visible) {
    AnAction action = getContextMenuAction(menuPath);
    AnActionEvent e = createContentMenuItemEvent();
    action.update(e);

    // Assert
    assertEquals(visible, e.getPresentation().isVisible());
    assertEquals(visible, e.getPresentation().isEnabled());
  }

  @NotNull
  private AnAction getContextMenuAction(@NotNull String menuPath) {
    ActionGroup actionGroup = myMockView.getFileTreeActionGroup();
    List<String> menuNames = StringUtil.split(menuPath, "/");
    for (int i = 0; i < menuNames.size() - 1; i++) {
      ActionGroup subGroup = getSubGroup(actionGroup, menuNames.get(i));
      assertNotNull(subGroup);
      actionGroup = subGroup;
    }
    AnAction action = getActionByText(actionGroup, menuNames.get(menuNames.size() - 1));
    assertNotNull(action);
    return action;
  }

  @NotNull
  private SettableFuture<TreePath> createNodeExpandedFuture(@NotNull final MockDeviceFileEntry entry) {
    assert entry.isDirectory();

    TreePath entryPath = getFileEntryPath(entry);
    SettableFuture<TreePath> futureTreeStructureChanged = SettableFuture.create();
    TreeModelAdapter treeModelAdapter = new TreeModelAdapter() {
      @Override
      public void treeStructureChanged(TreeModelEvent event) {
        DeviceFileEntryNode entryNode = DeviceFileEntryNode.fromNode(entryPath.getLastPathComponent());
        assertNotNull(entryNode);

        // Ensure this is the final event where we have all children (and not just the
        // "Loading..." child)
        if (!Objects.equals(entryNode, event.getTreePath().getLastPathComponent())) {
          return;
        }

        if (entryNode.getChildCount() != entry.getMockEntries().size()) {
          return;
        }

        for(int i = 0; i < entryNode.getChildCount(); i++) {
          DeviceFileEntryNode childNode = DeviceFileEntryNode.fromNode(entryNode.getChildAt(i));
          if (childNode == null) {
            // It could be the "Loading..." node
            return;
          }

          if (!Objects.equals(childNode.getEntry().getName(), entry.getMockEntries().get(i).getName())) {
            return;
          }
        }

        // All children are equal, the parent node is fully expanded!
        futureTreeStructureChanged.set(event.getTreePath());
      }
    };
    myMockView.getTree().getModel().addTreeModelListener(treeModelAdapter);
    myEdtExecutor.addConsumer(futureTreeStructureChanged,
                              (path, throwable) -> myMockView.getTree().getModel().removeTreeModelListener(treeModelAdapter));
    return futureTreeStructureChanged;
  }

  private void checkMockViewInitialState(DeviceExplorerController controller, MockDeviceFileSystem activeDevice)
    throws InterruptedException, ExecutionException, TimeoutException {
    checkMockViewComboBox(controller);
    checkMockViewActiveDevice(activeDevice);
  }

  private void checkMockViewComboBox(DeviceExplorerController controller)
    throws InterruptedException, ExecutionException, TimeoutException {
    // Check we have 2 devices available
    List<DeviceFileSystem> devices = pumpEventsAndWaitForFutures(myMockView.getDeviceAddedTracker().consumeMany(2));
    assertEquals(2, devices.size());
    assertEquals(1, devices.stream().filter(x -> x.getName() == "TestDevice-1").count());
    assertEquals(1, devices.stream().filter(x -> x.getName() == "TestDevice-2").count());

    // The device combo box should contain both devices, and the first one should be selected
    assertEquals(2, myMockView.getDeviceCombo().getItemCount());

    // The first device should be selected automatically
    pumpEventsAndWaitForFuture(myMockView.getDeviceSelectedTracker().consume());
    assertEquals(0, myMockView.getDeviceCombo().getSelectedIndex());
    assertTrue(controller.hasActiveDevice());
  }

  private void checkMockViewActiveDevice(MockDeviceFileSystem activeDevice)
    throws InterruptedException, ExecutionException, TimeoutException {

    pumpEventsAndWaitForFuture(myMockView.getStartTreeBusyIndicatorTacker().consume());
    pumpEventsAndWaitForFuture(myMockView.getStopTreeBusyIndicatorTacker().consume());

    // The root node should have been expanded to show the first level of children
    pumpEventsAndWaitForFuture(myMockView.getTreeNodeExpandingTracker().consume());

    // Check the file system tree is displaying the file system of the first device
    DeviceFileEntryNode rootEntry = DeviceFileEntryNode.fromNode(myMockView.getTree().getModel().getRoot());
    assertNotNull(rootEntry);
    assertEquals(activeDevice.getRoot(), rootEntry.getEntry());

    // Check the file system tree is showing the first level of entries of the file system
    pumpEventsAndWaitForFuture(myMockView.getTreeModelChangedTracker().consume());
    pumpEventsAndWaitForFuture(myMockView.getTreeStructureChangedTacker().consume());
    assertEquals(activeDevice.getRoot().getMockEntries().size(), rootEntry.getChildCount());
  }

  public void downloadFile(Runnable trigger) throws Exception {
    // Prepare
    DeviceExplorerController controller = createController();

    // Act
    controller.setup();
    pumpEventsAndWaitForFuture(myMockView.getServiceSetupSuccessTracker().consume());
    checkMockViewInitialState(controller, myDevice1);

    myDevice1.setDownloadFileChunkSize(1_000); // download chunks of 1000 bytes at a time
    myDevice1.setDownloadFileChunkIntervalMillis(10); // wait 10 millis between each 1000 bytes chunk
    // Setting the size to 200_000 bytes should force the download to take ~2 seconds,
    // i.e. 200 chunks of 1000 bytes at 100 chunks per second.
    // This allows use to cover the code that animates nodes UI during download.
    myFile1.setSize(200_000);

    // Select node
    TreePath file1Path = getFileEntryPath(myFile1);
    myMockView.getTree().setSelectionPath(file1Path);

    trigger.run();

    // Assert
    pumpEventsAndWaitForFuture(myMockFileManager.getDownloadFileEntryTracker().consume());
    pumpEventsAndWaitForFuture(myMockFileManager.getDownloadFileEntryCompletionTracker().consume());
  }

  private void expandEntry(@NotNull MockDeviceFileEntry entry) {
    // Attach listener for node expansion completion
    SettableFuture<TreePath> futureNodeExpanded = createNodeExpandedFuture(entry);

    // Expand node
    myMockView.getTree().expandPath(getFileEntryPath(entry));

    // Wait for tree node to be expanded
    pumpEventsAndWaitForFuture(futureNodeExpanded);
  }

  private DeviceExplorerController createController() {
    return createController(myMockView, myMockService);
  }

  private DeviceExplorerController createController(DeviceExplorerView view, DeviceFileSystemService service) {
    return new DeviceExplorerController(getProject(), myModel, view, service, myMockFileManager, EdtExecutor.INSTANCE);
  }

  private static <V> List<V> pumpEventsAndWaitForFutures(List<ListenableFuture<V>> futures) {
    return pumpEventsAndWaitForFuture(Futures.allAsList(futures));
  }

  private static <V> V pumpEventsAndWaitForFuture(ListenableFuture<V> future) {
    try {
      return FutureUtils.pumpEventsAndWaitForFuture(future, TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static <V> Throwable pumpEventsAndWaitForFutureException(ListenableFuture<V> future) {
    try {
      FutureUtils.pumpEventsAndWaitForFuture(future, TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
      throw new RuntimeException("Expected ExecutionException from future, got value instead");
    }
    catch (ExecutionException e) {
      return e;
    }
    catch (Throwable t) {
      throw new RuntimeException("Expected ExecutionException from future, got Throwable instead", t);
    }
  }

  /**
   * Returns the {@link TreePath} corresponding to a given {@link DeviceFileEntry}.
   * Throws an exception if the file entry is not found.
   */
  @NotNull
  private TreePath getFileEntryPath(@NotNull final MockDeviceFileEntry entry) {
    Stack<MockDeviceFileEntry> entries = getEntryStack(entry);

    List<Object> nodes = new ArrayList<>();
    DeviceFileEntryNode currentNode = DeviceFileEntryNode.fromNode(myMockView.getTree().getModel().getRoot());
    assertNotNull(currentNode);
    MockDeviceFileEntry currentEntry = entries.pop();
    assertEquals(currentNode.getEntry(), currentEntry);
    nodes.add(currentNode);
    while (!entries.isEmpty()) {
      MockDeviceFileEntry newEntry = entries.pop();

      currentEntry = null;
      for (int i = 0; i < myMockView.getTree().getModel().getChildCount(currentNode); i++) {
        DeviceFileEntryNode newNode = DeviceFileEntryNode.fromNode(myMockView.getTree().getModel().getChild(currentNode, i));
        assertNotNull(newNode);
        if (newNode.getEntry() == newEntry) {
          currentNode = newNode;
          currentEntry = newEntry;
          break;
        }
      }
      assertNotNull(String.format("File System Tree does not contain node \"%s\"", entry.getFullPath()), currentEntry);
      nodes.add(currentNode);
    }

    return new TreePath(nodes.toArray());
  }

  @NotNull
  private static Stack<MockDeviceFileEntry> getEntryStack(@NotNull MockDeviceFileEntry entry) {
    Stack<MockDeviceFileEntry> entries = new Stack<>();
    while (entry != null) {
      entries.add(entry);
      entry = (MockDeviceFileEntry)entry.getParent();
    }
    return entries;
  }

  private static void fireEnterKey(@NotNull JComponent component) {
    KeyEvent event = new KeyEvent(component, 0, 0, 0, KeyEvent.VK_ENTER, '\0');
    for (KeyListener listener : component.getKeyListeners()) {
      listener.keyPressed(event);
    }
    for (KeyListener listener : component.getKeyListeners()) {
      listener.keyTyped(event);
    }
    for (KeyListener listener : component.getKeyListeners()) {
      listener.keyReleased(event);
    }
  }

  private static void fireDoubleClick(@NotNull JComponent component, int x, int y) {
    MouseEvent event = new MouseEvent(component, 0, 0, 0, x, y, 2, false, MouseEvent.BUTTON1);
    for (MouseListener listener : component.getMouseListeners()) {
      listener.mouseClicked(event);
    }
    for (MouseListener listener : component.getMouseListeners()) {
      listener.mousePressed(event);
    }
  }
}