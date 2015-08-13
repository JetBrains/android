/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace;

import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.editors.gfxtrace.controllers.*;
import com.android.tools.idea.editors.gfxtrace.controllers.modeldata.AtomNode;
import com.android.tools.idea.editors.gfxtrace.controllers.modeldata.EnumInfoCache;
import com.android.tools.idea.editors.gfxtrace.controllers.modeldata.HierarchyNode;
import com.android.tools.idea.editors.gfxtrace.renderers.ScrubberCellRenderer;
import com.android.tools.idea.editors.gfxtrace.rpc.*;
import com.android.tools.idea.editors.gfxtrace.schema.Atom;
import com.android.tools.idea.editors.gfxtrace.schema.AtomReader;
import com.google.common.util.concurrent.*;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class GfxTraceEditor extends UserDataHolderBase implements FileEditor, ScrubberCellRenderer.DimensionChangeListener {
  @NotNull private static final Logger LOG = Logger.getInstance(GfxTraceEditor.class);
  @NotNull private static final String SERVER_HOST = "localhost";
  @NotNull private static final String SERVER_EXECUTABLE_NAME = "gapis";
  @NotNull private static final String SERVER_RELATIVE_PATH = "bin";
  private static final int SERVER_PORT = 6700;
  private static final int SERVER_LAUNCH_TIMEOUT_MS = 2000;
  private static final int SERVER_LAUNCH_SLEEP_INCREMENT_MS = 10;

  @NotNull private final Project myProject;
  @NotNull private final GfxTraceViewPanel myView;
  @NotNull private final ListeningExecutorService myService = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
  private Process myServerProcess;
  private Socket myServerSocket;
  @NotNull private Client myClient;
  private Schema mySchema;
  private EnumInfoCache myEnumInfoCache;
  private AtomStream myAtomStream;
  private AtomReader myAtomReader;

  @NotNull private List<GfxController> myControllers = new ArrayList<GfxController>();
  private ContextController myContextController;
  private AtomController myAtomController;
  private ScrubberController myScrubberController;
  private FrameBufferController myFrameBufferController;
  private StateController myStateController;
  private DocumentationController myDocumentationController;

  volatile private int myCaptureChangeId;
  private boolean myIsConnectedToServer;

  public GfxTraceEditor(@NotNull final Project project, @SuppressWarnings("UnusedParameters") @NotNull final VirtualFile file) {
    myProject = project;

    myView = new GfxTraceViewPanel();
    myView.setupViewHierarchy(myProject);

    try {
      if (connectToServer()) {
        myClient = new ClientImpl(Executors.newCachedThreadPool(), myServerSocket.getInputStream(), myServerSocket.getOutputStream(), 1024);
        myIsConnectedToServer = true;

        myContextController = new ContextController(this, myView.getDeviceList(), myView.getCapturesList(), myView.getGfxContextList());

        myAtomController = new AtomController(project, myView.getAtomScrollPane());
        myScrubberController = new ScrubberController(this, myView.getScrubberScrollPane(), myView.getScrubberList());
        myFrameBufferController =
          new FrameBufferController(this, myView.getBufferTabs(), myView.getColorScrollPane(), myView.getWireframeScrollPane(),
                                    myView.getDepthScrollPane());
        myStateController = new StateController(this, myView.getStateScrollPane());

        myControllers.add(myAtomController);
        myControllers.add(myScrubberController);
        myControllers.add(myStateController);
        myControllers.add(myFrameBufferController);

        myContextController.initialize();

        // TODO: Rewrite to use IntelliJ documentation view.
        myDocumentationController = new DocumentationController(myView.getDocsPane());

        establishInterViewControls();
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myView.getRootComponent();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @NotNull
  @Override
  public String getName() {
    return "GfxTraceView";
  }

  @NotNull
  @Override
  public FileEditorState getState(@NotNull FileEditorStateLevel level) {
    return FileEditorState.INSTANCE;
  }

  @Override
  public void setState(@NotNull FileEditorState state) {

  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void selectNotify() {

  }

  @Override
  public void deselectNotify() {

  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {

  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {

  }

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }

  @NotNull
  public Client getClient() {
    return myClient;
  }

  @Nullable
  public CaptureId getCaptureId() {
    if (myContextController.getCurrentCapture() != null) {
      return myContextController.getCurrentCaptureId();
    }
    return null;
  }

  @Nullable
  public DeviceId getDeviceId() {
    if (myContextController.getCurrentDevice() != null) {
      return myContextController.getCurrentDeviceId();
    }
    return null;
  }

  @NotNull
  public ListeningExecutorService getService() {
    return myService;
  }

  @Nullable
  public Integer getContext() {
    return myContextController.getCurrentContext();
  }

  private void clearCaptureState() {
    mySchema = null;
    myAtomReader = null;
  }

  private void clear() {
    for (GfxController controller : myControllers) {
      controller.clear();
    }
  }

  @Override
  public void dispose() {
    if (myServerSocket != null) {
      try {
        myServerSocket.close();
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

    // Only kill the server if we started it.
    if (myServerProcess != null) {
      myServerProcess.destroy();
    }

    myService.shutdown();
  }

  public void notifyDeviceChanged(@SuppressWarnings("UnusedParameters") @NotNull final Device device) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    for (GfxController controller : myControllers) {
      controller.clearCache();
    }
  }

  private void sleepThread(int milliseconds) {
    try {
      Thread.sleep(milliseconds);
    } catch (InterruptedException e) {
    }
  }

  /**
   * Attempts to connect to a gapis server.
   *
   * If the first attempt to connect fails, will launch a new server process and attempt to connect again.
   *
   * TODO: Implement more robust process management.  For example:
   * TODO: - Launch the new process in a separate thread so the GUI doesn't hang while the process is starting.
   * TODO: - Better handling of shutdown so that the replayd process does not continue running.
   * TODO: - Better way to detect when server has started in order to avoid polling for the socket.
   *
   * @return true if a connection to the server was established.
   */
  private boolean connectToServer() {
    myServerSocket = null;
    try {
      // Try to connect to an existing server.
      myServerSocket = new Socket(SERVER_HOST, SERVER_PORT);
    } catch (IOException e) {
      myServerSocket = null;
    }

    if (myServerSocket == null) {
      // The connection failed, so try to start a new instance of the server.
      try {
        // Look for the server binary in a subdirectory of the plugin.
        File baseDirectory = new File(PathUtil.getJarPathForClass(getClass()));
        if (baseDirectory.isFile()) {
          // We got a .jar file, so use the directory containing the .jar file.
          baseDirectory = baseDirectory.getParentFile();
        }
        if (baseDirectory.isDirectory()) {
          File serverDirectory = new File(baseDirectory, SERVER_RELATIVE_PATH);
          File serverExecutable = new File(serverDirectory, SERVER_EXECUTABLE_NAME);
          ProcessBuilder pb = new ProcessBuilder(serverExecutable.getAbsolutePath());

          // Add the server's directory to the path.  This allows the server to find and launch the replayd.
          java.util.Map<String, String> env = pb.environment();
          String path = env.get("PATH");
          path = serverDirectory.getAbsolutePath() + File.pathSeparator + path;
          env.put("PATH", path);

          // Use the plugin directory as the working directory for the server.
          pb.directory(baseDirectory);

          // This will throw IOException if the server executable is not found.
          myServerProcess = pb.start();
        } else {
          LOG.error("baseDirectory is not a directory: \"" + baseDirectory.getAbsolutePath() + "\"");
        }
      } catch (IOException e) {
        LOG.warn(e);
      }
      if (myServerProcess != null) {
        // After starting, the server requires a little time before it will be ready to accept connections.
        // This loop polls the server to establish a connection.
        for (int waitTime = 0;
             myServerSocket == null && waitTime < SERVER_LAUNCH_TIMEOUT_MS;
             waitTime += SERVER_LAUNCH_SLEEP_INCREMENT_MS) {
          try {
            myServerSocket = new Socket(SERVER_HOST, SERVER_PORT);
          } catch (IOException e1) {
            myServerSocket = null;
            // Wait before trying again.
            sleepThread(SERVER_LAUNCH_SLEEP_INCREMENT_MS);
          }
        }
      }
    }
    return myServerSocket != null;
  }

  public void notifyCaptureChanged(@NotNull final Capture capture) {
    // We need to keep track of what capture change is the most current, since the user could have changed the capture multiple times
    // before the client-server transfers are complete. We don't want to process stale data and potentially show users said stale state.
    myCaptureChangeId++;
    final int closedCaptureChangeId = myCaptureChangeId; // Record the counter for our closure.
    clear();
    clearCaptureState();

    // We need to perform this on an independent thread as this is over the network and will block.
    ListenableFuture<GfxController.CaptureChangeState> captureChange = myService.submit(new Callable<GfxController.CaptureChangeState>() {
      @Override
      public GfxController.CaptureChangeState call() throws Exception {
        AtomStream atomStream = myClient.ResolveAtomStream(capture.getAtoms()).get();
        Schema schema = myClient.ResolveSchema(atomStream.getSchema()).get();
        return new GfxController.CaptureChangeState(atomStream, schema);
      }
    });
    Futures.addCallback(captureChange, new FutureCallback<GfxController.CaptureChangeState>() {
      @Override
      public void onSuccess(@Nullable GfxController.CaptureChangeState state) {
        if (state != null && myIsConnectedToServer && closedCaptureChangeId == myCaptureChangeId) {
          myAtomStream = state.myAtomStream;
          mySchema = state.mySchema;
          myEnumInfoCache = new EnumInfoCache(mySchema);
          myContextController.populateUi(capture.getContextIds());
        }
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        LOG.error(t);
      }
    }, EdtExecutor.INSTANCE);
  }

  @Override
  public void notifyDimensionChanged(@NotNull Dimension newDimension) {
    myView.resize();
  }

  public void resolveGfxContextChange(@NotNull final AtomicBoolean shouldStop) {
    // Since gfx context is dependent on capture, this needs to synchronize against it.
    final int closedCaptureChangeId = myCaptureChangeId;
    clear();

    for (GfxController controller : myControllers) {
      controller.startLoad();
    }

    final GfxController.GfxContextChangeState state = new GfxController.GfxContextChangeState();
    state.myCaptureChangeState.myAtomStream = myAtomStream;
    state.myCaptureChangeState.mySchema = mySchema; // Get a reference of this on the EDT so there is no need to synchronize on it.
    state.myEnumInfoCache = myEnumInfoCache;

    assert (myContextController.getCurrentCapture() != null);
    final CaptureId captureId = myContextController.getCurrentCaptureId();
    final Integer contextId = myContextController.getCurrentContext();
    assert (contextId != null);

    ListenableFuture<GfxController.GfxContextChangeState> contextChange =
      myService.submit(new Callable<GfxController.GfxContextChangeState>() {
        @Override
        @Nullable
        public GfxController.GfxContextChangeState call() throws Exception {
          if (shouldStop.get()) {
            return null;
          }

          Hierarchy hierarchy = myClient.ResolveHierarchy(myClient.GetHierarchy(captureId, contextId).get()).get();
          state.myAtomReader = new AtomReader(state.myCaptureChangeState.myAtomStream, state.myCaptureChangeState.mySchema);
          state.myTreeRoot = AtomController.prepareData(hierarchy);
          state.myScrubberList = myScrubberController.prepareData(hierarchy, state.myAtomReader);

          return state;
        }
      });
    Futures.addCallback(contextChange, new FutureCallback<GfxController.GfxContextChangeState>() {
      @Override
      public void onSuccess(@Nullable GfxController.GfxContextChangeState result) {
        if (result != null) {
          myAtomReader = result.myAtomReader;
          for (GfxController controller : myControllers) {
            controller.commitData(result);
          }
          populateUi(shouldStop, closedCaptureChangeId);
        }
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        LOG.error(t);
      }
    }, EdtExecutor.INSTANCE);
  }

  private void populateUi(@NotNull AtomicBoolean shouldStop, int initialCaptureChangeId) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (!shouldStop.get() && initialCaptureChangeId == myCaptureChangeId) {
      // Initialize UI components.
      assert (myContextController.getCurrentContext() != null);
      myScrubberController.populateUi(myClient);
      myAtomController.populateUi(myAtomReader);
    }
  }

  /**
   * Establishes atom tree->scrubber and atom tree->framebuffer/memory/state/etc... controls.
   * This transitively establishes scrubber->framebuffer/memory/state/etc... controls.
   */
  private void establishInterViewControls() {
    myAtomController.getTree().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent treeSelectionEvent) {
        if (treeSelectionEvent.isAddedPath()) {
          Object[] pathObjects = treeSelectionEvent.getPath().getPath();
          assert (pathObjects.length >= 2); // The root is hidden, so the user should always select something at least 2 levels deep.
          assert (pathObjects[1] instanceof DefaultMutableTreeNode);

          Object userObject = ((DefaultMutableTreeNode)pathObjects[1]).getUserObject();
          assert (userObject instanceof HierarchyNode);
          HierarchyNode node = (HierarchyNode)userObject;

          myScrubberController.selectFrame(node.getRepresentativeAtomId());
        }

        DefaultMutableTreeNode node = (DefaultMutableTreeNode)myAtomController.getTree().getLastSelectedPathComponent();

        if (node == null) { // This could happen when user collapses a node.
          myFrameBufferController.clearCache();
          return;
        }

        Object userObject = node.getUserObject();
        assert (userObject instanceof HierarchyNode || userObject instanceof AtomNode);

        long atomId;
        if (userObject instanceof HierarchyNode) {
          HierarchyNode hierarchyNode = (HierarchyNode)userObject;
          atomId = hierarchyNode.getRepresentativeAtomId();
        }
        else {
          AtomNode atomNode = (AtomNode)userObject;
          atomId = atomNode.getRepresentativeAtomId();
          try {
            myDocumentationController.setDocumentation(myAtomReader.read(atomId).info.getDocumentationUrl());
          }
          catch (IOException e) {
            LOG.error(e);
            return;
          }
        }
        myFrameBufferController.setImageForId(findPreviousDrawCall(atomId)); // Select draw call at or prior to atomId.
        myStateController.updateTreeModelFromAtomId(atomId);
      }
    });

    // Establish scrubber->atom tree controls.
    myView.getScrubberList().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent listSelectionEvent) {
        if (!listSelectionEvent.getValueIsAdjusting()) {
          AtomGroup selection = myScrubberController.getFrameSelectionReference();
          if (selection != null) {
            myAtomController.selectFrame(selection);
          }
        }
      }
    });
  }

  /**
   * Finds the latest atom ID at or prior to the given ID that is a valid draw call/end of frame.
   */
  private long findPreviousDrawCall(long selectedId) {
    try {
      Atom atom = myAtomReader.read(selectedId);
      if (atom.info.getIsDrawCall()) {
        return selectedId;
      }

      if (selectedId - 1 > Integer.MAX_VALUE) {
        throw new RuntimeException("Selected Atom ID exceeds largest Atom ID supported.");
      }

      for (long i = selectedId - 1; i >= 0; --i) {
        atom = myAtomReader.read(i);
        if (atom.info.getIsDrawCall()) {
          return i;
        }
        else if (atom.info.getIsEndOfFrame()) {
          return i + 1;
        }
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }

    return 0;
  }
}
