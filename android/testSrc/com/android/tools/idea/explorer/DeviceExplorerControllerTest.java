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
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.ui.tree.TreeModelAdapter;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(getProject());
    ToolWindow toolWindow = toolWindowManager.registerToolWindow(
      DeviceExplorerToolWindowFactory.TOOL_WINDOW_ID, false, ToolWindowAnchor.RIGHT, getProject(), true);

    myModel = new DeviceExplorerModel();
    myMockService = new MockDeviceFileSystemService(getProject(), EdtExecutor.INSTANCE);
    myMockView = new MockDeviceExplorerView(getProject(), toolWindow, new MockDeviceFileSystemRenderer(), myModel);
    myMockFileManager = new MockDeviceExplorerFileManager(getProject(), EdtExecutor.INSTANCE);

    myDevice1 = myMockService.addDevice("TestDevice-1");
    myFoo = myDevice1.getRoot().addDirectory("Foo");
    myFoo.addFile("fooFile1.txt");
    myFoo.addFile("fooFile2.txt");
    myFoo.addFileLink("fooLink1.txt", "fooFile1.txt");
    myFoo.addDirectory("fooDir");
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

      if (myMockFileManager != null) {
        Disposer.dispose(myMockFileManager);
        myMockFileManager = null;
      }
    } finally {
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
    SettableFuture<TreePath> futureTreeStructureChanged = SettableFuture.create();
    SettableFuture<MyLoadingNode> futureTreeNodesChanged = SettableFuture.create();
    myMockView.getTree().getModel().addTreeModelListener(new TreeModelAdapter() {
      @Override
      public void treeStructureChanged(TreeModelEvent event) {
        // Ensure this is the final event where we have all children (and not just the
        // "Loading..." child)
        if (fooPath.getLastPathComponent() == event.getTreePath().getLastPathComponent()) {
          if (((DeviceFileEntryNode)fooPath.getLastPathComponent()).getChildCount() == myFoo.getMockEntries().size()) {
            futureTreeStructureChanged.set(event.getTreePath());
          }
        }
      }

      @Override
      public void treeNodesChanged(TreeModelEvent event) {
        if (fooPath.getLastPathComponent() == event.getTreePath().getLastPathComponent()) {
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
    TreePath treeStructureChangedPath = pumpEventsAndWaitForFuture(futureTreeStructureChanged);

    // Assert
    assertTrue(myLoadingNode.getDownloadingTick() > 1);
    assertEquals(fooPath.getLastPathComponent(), treeStructureChangedPath.getLastPathComponent());
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

  public void testDownloadFileWithEnterKey() throws InterruptedException, ExecutionException, TimeoutException {
    downloadFile(() -> {
      // Send a VK_ENTER key event
      fireEnterKey(myMockView.getTree());
    });
  }

  public void testDownloadFileWithMouseClick() throws InterruptedException, ExecutionException, TimeoutException {
    downloadFile(() -> {
      // Find location of "file1" node in the tree
      Object rootNode = myMockView.getTree().getModel().getRoot();
      DeviceFileEntryNode file1Node = DeviceFileEntryNode.fromNode(myMockView.getTree().getModel().getChild(rootNode, 1));
      assert file1Node != null;

      TreePath path = new TreePath(new Object[]{rootNode, file1Node});
      Rectangle pathBounds = myMockView.getTree().getPathBounds(path);
      assert pathBounds != null;

      // Fire double-click event
      fireDoubleClick(myMockView.getTree(), pathBounds.x, pathBounds.y);
    });
  }

  public void testDownloadFileFailure() throws InterruptedException, ExecutionException, TimeoutException {
    // Prepare
    DeviceExplorerController controller = createController();

    // Act
    controller.setup();
    pumpEventsAndWaitForFuture(myMockView.getServiceSetupSuccessTracker().consume());
    checkMockViewInitialState(controller, myDevice1);

    Object rootNode = myMockView.getTree().getModel().getRoot();
    DeviceFileEntryNode file1Node =
      DeviceFileEntryNode.fromNode(myMockView.getTree().getModel().getChild(rootNode, 1));

    String errorMessage = "<Expected test error>";
    myDevice1.setDownloadError(new RuntimeException(errorMessage));

    // Select node
    myMockView.getTree().setSelectionPath(new TreePath(new Object[]{rootNode, file1Node}));

    // Send a VK_ENTER key event
    fireEnterKey(myMockView.getTree());
    pumpEventsAndWaitForFuture(myMockView.getTreeNodeActionPerformedTracker().consume());

    pumpEventsAndWaitForFuture(myMockFileManager.getDownloadFileEntryTracker().consume());
    pumpEventsAndWaitForFuture(myMockFileManager.getDownloadFileEntryCompletionTracker().consume());
    String loadingError = pumpEventsAndWaitForFuture(myMockView.getReportErrorRelatedToNodeTracker().consume());

    // Assert
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

    Object rootNode = myMockView.getTree().getModel().getRoot();
    DeviceFileEntryNode file1Node =
      DeviceFileEntryNode.fromNode(myMockView.getTree().getModel().getChild(rootNode, 1));

    String errorMessage = "<Expected test error>";
    myMockFileManager.setOpenFileInEditorError(new RuntimeException(errorMessage));

    // Select node
    myMockView.getTree().setSelectionPath(new TreePath(new Object[]{rootNode, file1Node}));

    // Send a VK_ENTER key event
    fireEnterKey(myMockView.getTree());
    pumpEventsAndWaitForFuture(myMockView.getTreeNodeActionPerformedTracker().consume());

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

  public void downloadFile(Runnable trigger) throws InterruptedException, ExecutionException, TimeoutException {
    // Prepare
    DeviceExplorerController controller = createController();

    // Act
    controller.setup();
    pumpEventsAndWaitForFuture(myMockView.getServiceSetupSuccessTracker().consume());
    checkMockViewInitialState(controller, myDevice1);

    Object rootNode = myMockView.getTree().getModel().getRoot();
    DeviceFileEntryNode file1Node =
      DeviceFileEntryNode.fromNode(myMockView.getTree().getModel().getChild(rootNode, 1));

    myDevice1.setDownloadFileChunkSize(1_000); // download chunks of 1000 bytes at a time
    myDevice1.setDownloadFileChunkIntervalMillis(10); // wait 10 millis between each 1000 bytes chunk
    // Setting the size to 200_000 bytes should force the download to take ~2 seconds,
    // i.e. 200 chunks of 1000 bytes at 100 chunks per second.
    // This allows use to cover the code that animates nodes UI during download.
    myFile1.setSize(200_000);

    // Select node
    myMockView.getTree().setSelectionPath(new TreePath(new Object[]{rootNode, file1Node}));

    trigger.run();
    pumpEventsAndWaitForFuture(myMockView.getTreeNodeActionPerformedTracker().consume());

    // Assert
    pumpEventsAndWaitForFuture(myMockFileManager.getDownloadFileEntryTracker().consume());
    pumpEventsAndWaitForFuture(myMockFileManager.getDownloadFileEntryCompletionTracker().consume());
    pumpEventsAndWaitForFuture(myMockFileManager.getOpenFileInEditorTracker().consume());
  }

  private DeviceExplorerController createController() {
    return createController(myMockView, myMockService);
  }

  private DeviceExplorerController createController(DeviceExplorerView view, DeviceFileSystemService service) {
    return new DeviceExplorerController(getProject(), myModel, view, service, myMockFileManager, EdtExecutor.INSTANCE);
  }

  private static <V> List<V> pumpEventsAndWaitForFutures(List<ListenableFuture<V>> futures)
    throws InterruptedException, ExecutionException, TimeoutException {
    return pumpEventsAndWaitForFuture(Futures.allAsList(futures));
  }

  private static <V> V pumpEventsAndWaitForFuture(ListenableFuture<V> future)
    throws InterruptedException, ExecutionException, TimeoutException {
    return FutureUtils.pumpEventsAndWaitForFuture(future, TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
  }

  /**
   * Returns the {@link TreePath} corresponding to a given {@link DeviceFileEntry}.
   * Throws an exception if the file entry is not found.
   */
  @NotNull
  private TreePath getFileEntryPath(@NotNull MockDeviceFileEntry entry) {
    Stack<MockDeviceFileEntry> entries = new Stack<>();
    while (entry != null) {
      entries.add(entry);
      entry = (MockDeviceFileEntry)entry.getParent();
    }

    List<Object> nodes = new ArrayList<>();
    DeviceFileEntryNode currentNode = DeviceFileEntryNode.fromNode(myMockView.getTree().getModel().getRoot());
    MockDeviceFileEntry currentEntry = entries.pop();
    assertEquals(currentNode.getEntry(), currentEntry);
    nodes.add(currentNode);
    while (!entries.isEmpty()) {
      MockDeviceFileEntry newEntry = entries.pop();

      currentEntry = null;
      for (int i = 0; i < myMockView.getTree().getModel().getChildCount(currentNode); i++) {
        DeviceFileEntryNode newNode = DeviceFileEntryNode.fromNode(myMockView.getTree().getModel().getChild(currentNode, i));
        if (newNode.getEntry() == newEntry) {
          currentNode = newNode;
          currentEntry = newEntry;
          break;
        }
      }
      assertNotNull(currentEntry);
      nodes.add(currentNode);
    }

    return new TreePath(nodes.toArray());
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
