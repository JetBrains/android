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

import com.android.tools.idea.editors.gfxtrace.controllers.*;
import com.android.tools.idea.editors.gfxtrace.controllers.modeldata.AtomNode;
import com.android.tools.idea.editors.gfxtrace.controllers.modeldata.HierarchyNode;
import com.android.tools.idea.editors.gfxtrace.renderers.ScrubberCellRenderer;
import com.android.tools.idea.editors.gfxtrace.service.Schema;
import com.android.tools.idea.editors.gfxtrace.service.ServiceClient;
import com.android.tools.idea.editors.gfxtrace.service.ServiceClientCache;
import com.android.tools.idea.editors.gfxtrace.service.ServiceClientRPC;
import com.android.tools.idea.editors.gfxtrace.service.atom.AtomGroup;
import com.android.tools.idea.editors.gfxtrace.service.atom.AtomMetadata;
import com.android.tools.idea.editors.gfxtrace.service.path.CapturePath;
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.android.tools.idea.editors.gfxtrace.service.path.PathListener;
import com.android.tools.rpclib.binary.Namespace;
import com.android.tools.rpclib.schema.*;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
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
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class GfxTraceEditor extends UserDataHolderBase implements FileEditor, ScrubberCellRenderer.DimensionChangeListener {
  @NotNull public static final String SELECT_CAPTURE = "Select a capture";
  @NotNull public static final String SELECT_ATOM = "Select an atom";

  @NotNull private static final Logger LOG = Logger.getInstance(GfxTraceEditor.class);
  @NotNull private static final String SERVER_HOST = "localhost";
  @NotNull private static final String SERVER_EXECUTABLE_NAME = "gapis";
  @NotNull private static final String SERVER_RELATIVE_PATH = "bin";
  private static final int SERVER_PORT = 6700;
  private static final int SERVER_LAUNCH_TIMEOUT_MS = 2000;
  private static final int SERVER_LAUNCH_SLEEP_INCREMENT_MS = 10;

  @NotNull private final Project myProject;
  @NotNull private final GfxTraceViewPanel myView;
  @NotNull private final ListeningExecutorService myService = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
  private Process myServerProcess;
  private Socket myServerSocket;
  @NotNull private ServiceClient myClient;

  @NotNull private List<PathListener> myPathListeners = new ArrayList<PathListener>();
  private ContextController myContextController;
  private AtomController myAtomController;
  private ScrubberController myScrubberController;
  private FrameBufferController myFrameBufferController;
  private StateController myStateController;
  private DocumentationController myDocumentationController;

  public GfxTraceEditor(@NotNull final Project project, @SuppressWarnings("UnusedParameters") @NotNull final VirtualFile file) {
    myProject = project;

    myView = new GfxTraceViewPanel();
    myView.setupViewHierarchy(myProject);

    try {
      if (connectToServer()) {
        ServiceClient rpcClient = new ServiceClientRPC(myService , myServerSocket.getInputStream(), myServerSocket.getOutputStream(), 1024);
        myClient = new ServiceClientCache(rpcClient);

        // prefetch the schema
        Futures.addCallback(myClient.getSchema(), new FutureCallback<Schema>() {
          @Override
          public void onSuccess(Schema schema) {
            LOG.warn("Schema with " + schema.getClasses().length + " classes, " + schema.getConstants().length + " constant sets");
            int atoms = 0;
            for (SchemaClass type : schema.getClasses()) {
              // Find the atom metadata, if present
              if (AtomMetadata.find(type) != null) {
                atoms++;
              }
              Dynamic.register(type);
            }
            LOG.warn("Schema with " + atoms + " atoms");
          }

          @Override
          public void onFailure(Throwable t) {
            LOG.error(t);
          }
        });

        // Upload the trace file
        byte[] data = file.contentsToByteArray();
        LOG.warn("Upload " + data.length + " bytes of gfxtrace as " + file.getPresentableName());
        Futures.addCallback(myClient.importCapture(file.getPresentableName(), data), new FutureCallback<CapturePath>() {
          @Override
          public void onSuccess(CapturePath path) {
            LOG.warn("Capture uploaded");
            // block on the schema before we activate the new capture
            try {
              myClient.getSchema().get();
              if (path != null) {
                activatePath(path);
              } else {
                LOG.error("Invalid capture file "+ file.getPresentableName());
              }
            }
            catch (InterruptedException e) {}
            catch (ExecutionException e) {}
          }

          @Override
          public void onFailure(Throwable t) {
            LOG.error(t);
          }
        });

        myContextController = new ContextController(this, myView.getDeviceList(), myView.getCapturesList());

        myAtomController = new AtomController(this, project, myView.getAtomScrollPane());
        myScrubberController = new ScrubberController(this, myView.getScrubberScrollPane(), myView.getScrubberList());
        myFrameBufferController =
          new FrameBufferController(this, myView.getBufferTabs(), myView.getColorScrollPane(), myView.getWireframeScrollPane(),
                                    myView.getDepthScrollPane());
        myStateController = new StateController(this, myView.getStateScrollPane());

        // TODO: Rewrite to use IntelliJ documentation view.
        myDocumentationController = new DocumentationController(this, myView.getDocsPane());

        myContextController.initialize();

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
  public ServiceClient getClient() {
    return myClient;
  }

  @NotNull
  public ListeningExecutorService getService() {
    return myService;
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
    com.android.tools.idea.editors.gfxtrace.service.Factory.register();
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
          LOG.info("launch gapis: \"" + serverExecutable.getAbsolutePath() + "\"");
          ProcessBuilder pb = new ProcessBuilder(serverExecutable.getAbsolutePath());

          // Add the server's directory to the path.  This allows the server to find and launch the replayd.
          Map<String, String> env = pb.environment();
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

  public void activatePath(@NotNull final Path path) {
    for (PathListener listener : myPathListeners) {
      listener.notifyPath(path);
    }
  }

  public void addPathListener(@NotNull PathListener listener) {
    myPathListeners.add(listener);
  }

  @Override
  public void notifyDimensionChanged(@NotNull Dimension newDimension) {
    myView.resize();
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

          // TODO: convert to atom path and then select it
          return;
        }

        DefaultMutableTreeNode node = (DefaultMutableTreeNode)myAtomController.getTree().getLastSelectedPathComponent();

        if (node == null) { // This could happen when user collapses a node.
          return;
        }

        Object userObject = node.getUserObject();
        assert (userObject instanceof HierarchyNode || userObject instanceof AtomNode);

        long atomIndex;
        if (userObject instanceof HierarchyNode) {
          HierarchyNode hierarchyNode = (HierarchyNode)userObject;
          atomIndex = hierarchyNode.getRepresentativeAtomIndex();
        }
        else {
          AtomNode atomNode = (AtomNode)userObject;
          atomIndex = atomNode.getRepresentativeAtomIndex();
        }
        // TODO: convert to atom path and then activate it
      }
    });

    // Establish scrubber->atom tree controls.
    myView.getScrubberList().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent listSelectionEvent) {
        if (!listSelectionEvent.getValueIsAdjusting()) {
          AtomGroup selection = myScrubberController.getFrameSelectionReference();
          if (selection != null) {
            // TODO: convert to atom path and then activate it
          }
        }
      }
    });
  }
}
