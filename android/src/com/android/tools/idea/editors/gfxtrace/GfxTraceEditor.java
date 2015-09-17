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
import com.android.tools.idea.editors.gfxtrace.controllers.MainController;
import com.android.tools.idea.editors.gfxtrace.service.*;
import com.android.tools.idea.editors.gfxtrace.service.atom.AtomMetadata;
import com.android.tools.idea.editors.gfxtrace.service.path.CapturePath;
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.android.tools.idea.editors.gfxtrace.service.path.PathListener;
import com.android.tools.rpclib.binary.BinaryObject;
import com.android.tools.rpclib.schema.ConstantSet;
import com.android.tools.rpclib.schema.Dynamic;
import com.android.tools.rpclib.schema.SchemaClass;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static com.intellij.idea.IdeaApplication.IDEA_IS_INTERNAL_PROPERTY;

public class GfxTraceEditor extends UserDataHolderBase implements FileEditor {
  @NotNull public static final String SELECT_CAPTURE = "Select a capture";
  @NotNull public static final String SELECT_ATOM = "Select an atom";

  @NotNull private static final Logger LOG = Logger.getInstance(GfxTraceEditor.class);
  @NotNull private static final String SERVER_HOST = "localhost";
  @NotNull private static final String SERVER_EXECUTABLE_NAME = "gapis";
  @NotNull private static final String SERVER_RELATIVE_PATH = "bin";
  private static final int SERVER_PORT = 6700;
  private static final int SERVER_LAUNCH_TIMEOUT_MS = 2000;
  private static final int SERVER_LAUNCH_SLEEP_INCREMENT_MS = 10;

  private static final Object myPathLock = new Object();
  private static File myGapisRoot;
  private static File myServerDirectory;
  private static File myGapisPath;

  @NotNull private final Project myProject;
  @NotNull private LoadingDecorator myLoadingDecorator;
  @NotNull private JBPanel myView = new JBPanel(new BorderLayout());
  @NotNull private final ListeningExecutorService myExecutor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
  private Process myServerProcess;
  private Socket myServerSocket;
  private ServiceClient myClient;

  @NotNull private List<PathListener> myPathListeners = new ArrayList<PathListener>();

  public static boolean isEnabled() {
    updatePath();
    return myGapisPath != null;
  }

  public GfxTraceEditor(@NotNull final Project project, @NotNull final VirtualFile file) {
    myProject = project;
    myLoadingDecorator = new LoadingDecorator(myView, this, 0);
    myLoadingDecorator.setLoadingText("Initializing GFX Trace System");
    myLoadingDecorator.startLoading(false);

    // Attempt to start/connect to the server on a separate thread to reduce the IDE from stalling.
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        if (!connectToServer()) {
          setLoadingErrorTextOnEdt("Unable to connect to server");
          return;
        }

        try {
          ServiceClient rpcClient =
            new ServiceClientRPC(myExecutor, myServerSocket.getInputStream(), myServerSocket.getOutputStream(), 1024);
          myClient = new ServiceClientCache(rpcClient);
        }
        catch (IOException e) {
          setLoadingErrorTextOnEdt("Unable to talk to server");
          return;
        }

        // Prefetch the schema
        final ListenableFuture<Schema> schemaF = myClient.getSchema();
        Futures.addCallback(schemaF, new LoadingCallback<Schema>(LOG) {
          @Override
          public void onSuccess(@Nullable final Schema schema) {
            LOG.info("Schema with " + schema.getClasses().length + " classes, " + schema.getConstants().length + " constant sets");
            int atoms = 0;
            for (SchemaClass type : schema.getClasses()) {
              // Find the atom metadata, if present
              if (AtomMetadata.find(type) != null) {
                atoms++;
              }
              Dynamic.register(type);
            }
            LOG.info("Schema with " + atoms + " atoms");
            for (ConstantSet set : schema.getConstants()) {
              ConstantSet.register(set);
            }
          }
        });

        try {
          final ListenableFuture<CapturePath> captureF;
          if (file.getFileSystem().getProtocol().equals(StandardFileSystems.FILE_PROTOCOL)) {
            LOG.info("Load gfxtrace in " + file.getPresentableName());
            captureF = myClient.loadCapture(file.getCanonicalPath());
          } else {
            // Upload the trace file
            byte[] data = file.contentsToByteArray();
            LOG.info("Upload " + data.length + " bytes of gfxtrace as " + file.getPresentableName());
            captureF = myClient.importCapture(file.getPresentableName(), data);
          }

          // When both steps are complete, activate the capture path
          Futures.addCallback(Futures.allAsList(schemaF, captureF), new LoadingCallback<List<BinaryObject>>(LOG) {
            @Override
            public void onSuccess(@Nullable final List<BinaryObject> all) {
              CapturePath path = (CapturePath)all.get(1);
              LOG.info("Capture uploaded");
              if (path != null) {
                activatePath(path);
              }
              else {
                LOG.error("Invalid capture file " + file.getPresentableName());
              }
            }
          });
        }
        catch (IOException e) {
          setLoadingErrorTextOnEdt("Error reading gfxtrace file");
          return;
        }

        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            myView.add(MainController.createUI(GfxTraceEditor.this), BorderLayout.CENTER);
            myLoadingDecorator.stopLoading();
          }
        });
      }
    });
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myView;
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

  public void activatePath(@NotNull final Path path) {
    // All path notifications are executed in the editor thread
    EdtExecutor.INSTANCE.execute(new Runnable() {
      @Override
      public void run() {
        LOG.warn("Activate path " + path);
        for (PathListener listener : myPathListeners) {
          listener.notifyPath(path);
        }
      }
    });
  }

  public void addPathListener(@NotNull PathListener listener) {
    myPathListeners.add(listener);
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
  public ListeningExecutorService getExecutor() {
    return myExecutor;
  }

  @Override
  public void dispose() {
    shutdown();
  }

  /**
   * Attempts to connect to a gapis server.
   * <p/>
   * If the first attempt to connect fails, will launch a new server process and attempt to connect again.
   * <p/>
   * TODO: Implement more robust process management.  For example:
   * TODO: - Better handling of shutdown so that the replayd process does not continue running.
   * TODO: - Better way to detect when server has started in order to avoid polling for the socket.
   *
   * @return true if a connection to the server was established.
   */
  private boolean connectToServer() {
    assert !ApplicationManager.getApplication().isDispatchThread();

    Factory.register();

    myServerSocket = null;
    try {
      // Try to connect to an existing server.
      myServerSocket = new Socket(SERVER_HOST, SERVER_PORT);
    }
    catch (IOException ignored) {
    }

    if (myServerSocket != null) {
      return true;
    }

    // The connection failed, so try to start a new instance of the server.
    try {
      LOG.info("launch gapis: \"" + myGapisPath.getAbsolutePath() + "\"");
      ProcessBuilder pb = new ProcessBuilder(myGapisPath.getAbsolutePath());

      // Add the server's directory to the path.  This allows the server to find and launch the replayd.
      Map<String, String> env = pb.environment();
      String path = env.get("PATH");
      path = myServerDirectory.getAbsolutePath() + File.pathSeparator + path;
      env.put("PATH", path);

      // Use the plugin directory as the working directory for the server.
      pb.directory(myGapisRoot);

      // This will throw IOException if the server executable is not found.
      myServerProcess = pb.start();
    }
    catch (IOException e) {
      LOG.warn(e);
      return false;
    }

    // After starting, the server requires a little time before it will be ready to accept connections.
    // This loop polls the server to establish a connection.
    for (int waitTime = 0; waitTime < SERVER_LAUNCH_TIMEOUT_MS; waitTime += SERVER_LAUNCH_SLEEP_INCREMENT_MS) {
      try {
        myServerSocket = new Socket(SERVER_HOST, SERVER_PORT);
        return true;
      }
      catch (IOException e1) {
        myServerSocket = null;
      }

      try {
        // Wait before trying again.
        Thread.sleep(SERVER_LAUNCH_SLEEP_INCREMENT_MS);
      }
      catch (InterruptedException e) {
        Thread.interrupted(); // reset interrupted status
        shutdown();
        return false; // Some external factor cancelled our busy-wait, so exit immediately.
      }
    }

    shutdown();
    return false;
  }

  private void setLoadingErrorTextOnEdt(@NotNull final String error) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        myLoadingDecorator.setLoadingText(error);
      }
    });
  }

  private void shutdown() {
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

    myExecutor.shutdown();
  }

  static File getBinaryPath() {
    updatePath();
    return myServerDirectory;
  }

  private static boolean testPath(File root) {
    File bin = new File(root, SERVER_RELATIVE_PATH);
    File gapis = new File(bin, SERVER_EXECUTABLE_NAME);
    if (!gapis.exists()) {
      return false;
    }
    myGapisRoot = root;
    myServerDirectory = bin;
    myGapisPath = gapis;
    return true;
  }

  private static void updatePath() {
    synchronized (myPathLock) {
      if (myGapisPath != null) {
        return;
      }
      File androidPlugin = PluginPathManager.getPluginHome("android");
      if (Boolean.getBoolean(IDEA_IS_INTERNAL_PROPERTY)) {
        // Check the default build location for a standard repo checkout
        if (testPath(new File(androidPlugin.getParentFile().getParentFile().getParentFile(), "gpu"))) return;
        // Check the GOPATH in case it is non standard
        String gopath = System.getenv("GOPATH");
        if (gopath != null && gopath.length() > 0 && testPath(new File(gopath))) return;
        // TODO: Check the prebuilts location
      }
      testPath(androidPlugin);
    }
  }

}
