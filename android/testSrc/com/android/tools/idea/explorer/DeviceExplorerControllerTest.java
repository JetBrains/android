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
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.List;
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
  private MockDeviceFileEntry myFile1;

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
    myDevice1.getRoot().addDirectory("Foo");
    myFile1 = myDevice1.getRoot().addFile("file1.txt");
    myDevice1.getRoot().addFile("file2.txt");
    myDevice1.getRoot().addFile("file3.txt");

    myMockService.addDevice("TestDevice-2");
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myMockFileManager != null) {
        Disposer.dispose(myMockFileManager);
      }
    } finally {
      super.tearDown();
    }
  }

  public void testStartController() throws InterruptedException, ExecutionException, TimeoutException {
    // Prepare
    DeviceExplorerController controller = createController();

    // Act
    controller.setup();
    pumpEventsAndWaitForFuture(myMockView.getServiceSetupSuccessTracker().consume());

    // Assert
    checkMockViewState(controller, myDevice1);
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
    checkMockViewState(controller, myDevice1);

    // Act
    controller.restartService();
    pumpEventsAndWaitForFuture(myMockView.getAllDevicesRemovedTracker().consume());

    // Assert
    checkMockViewState(controller, myDevice1);
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
    checkMockViewState(controller, myDevice1);

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

  private void checkMockViewState(DeviceExplorerController controller, MockDeviceFileSystem activeDevice)
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

    // The root node should have been expanded to show the first level of children
    pumpEventsAndWaitForFuture(myMockView.getTreeNodeExpandingTracker().consume());

    // Check the file system tree is displaying the file system of the first device
    Object rootValue = myMockView.getTree().getModel().getRoot();
    assertInstanceOf(rootValue, DeviceFileEntryNode.class);
    assertEquals(activeDevice.getRoot(), ((DeviceFileEntryNode)rootValue).getEntry());

    // Check the file system tree is showing the first level of entries of the file system
    pumpEventsAndWaitForFuture(myMockView.getTreeModelChangedTracker().consume());
    pumpEventsAndWaitForFuture(myMockView.getTreeStructureChangedTacker().consume());
    assertEquals(activeDevice.getRoot().getMockEntries().size(), myMockView.getTree().getModel().getChildCount(rootValue));
  }

  public void downloadFile(Runnable trigger) throws InterruptedException, ExecutionException, TimeoutException {
    // Prepare
    DeviceExplorerController controller = createController();

    // Act
    controller.setup();
    pumpEventsAndWaitForFuture(myMockView.getServiceSetupSuccessTracker().consume());
    checkMockViewState(controller, myDevice1);

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
