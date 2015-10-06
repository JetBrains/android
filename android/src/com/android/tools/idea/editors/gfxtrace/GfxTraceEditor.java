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
import com.android.tools.idea.editors.gfxtrace.service.path.PathStore;
import com.android.tools.rpclib.binary.BinaryObject;
import com.android.tools.rpclib.schema.ConstantSet;
import com.android.tools.rpclib.schema.Dynamic;
import com.android.tools.rpclib.schema.Message;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.android.tools.rpclib.schema.Entity;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
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
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

import static com.intellij.idea.IdeaApplication.IDEA_IS_INTERNAL_PROPERTY;

public class GfxTraceEditor extends UserDataHolderBase implements FileEditor {
  @NotNull public static final String LOADING_CAPTURE = "Loading capture...";
  @NotNull public static final String SELECT_ATOM = "Select a frame or command";
  @NotNull public static final String SELECT_MEMORY = "Select a memory range in the command list";
  @NotNull public static final String SELECT_TEXTURE = "Select a texture";
  @NotNull public static final String NO_TEXTURES = "No textures have been created by this point";


  @NotNull private static final Logger LOG = Logger.getInstance(GfxTraceEditor.class);

  private static final Object myPathLock = new Object();
  private static Paths myPaths;

  @NotNull private final Project myProject;
  @NotNull private LoadingDecorator myLoadingDecorator;
  @NotNull private JBPanel myView = new JBPanel(new BorderLayout());
  @NotNull private final ListeningExecutorService myExecutor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
  private ServerConnection myServerConnection;
  private ServiceClient myClient;

  @NotNull private List<PathListener> myPathListeners = new ArrayList<PathListener>();
  @NotNull private PathStore<Path> myLastActivatadPath = new PathStore<Path>();

  public static boolean isEnabled() {
    return getPaths().isValid();
  }

  public GfxTraceEditor(@NotNull final Project project, @SuppressWarnings("UnusedParameters") @NotNull final VirtualFile file) {
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
          myClient = new ServiceClientCache(myServerConnection.createServiceClient(myExecutor));
        }
        catch (IOException e) {
          setLoadingErrorTextOnEdt("Unable to talk to server");
          return;
        }

        // Prefetch the schema
        final ListenableFuture<Message> schemaF = myClient.getSchema();
        Futures.addCallback(schemaF, new LoadingCallback<Message>(LOG) {
          @Override
          public void onSuccess(@Nullable final Message schema) {
            LOG.info("Schema with " + schema.entities.length + " classes, " + schema.constants.length + " constant sets");
            int atoms = 0;
            for (Entity type : schema.entities) {
              // Find the atom metadata, if present
              if (AtomMetadata.find(type) != null) {
                atoms++;
              }
              Dynamic.register(type);
            }
            LOG.info("Schema with " + atoms + " atoms");
            for (ConstantSet set : schema.constants) {
              ConstantSet.register(set);
            }
          }
        });

        try {
          final ListenableFuture<CapturePath> captureF;
          if (file.getFileSystem().getProtocol().equals(StandardFileSystems.FILE_PROTOCOL)) {
            LOG.info("Load gfxtrace in " + file.getPresentableName());
            captureF = myClient.loadCapture(file.getCanonicalPath());
          }
          else {
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
                activatePath(path, GfxTraceEditor.this);
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

  public void activatePath(@NotNull final Path path, final Object source) {
    synchronized (myLastActivatadPath) {
      if (!myLastActivatadPath.update(path)) {
        return;
      }
    }

    final PathListener.PathEvent event = new PathListener.PathEvent(path, source);
    // All path notifications are executed in the editor thread
    Runnable eventDispatch = new Runnable() {
      @Override
      public void run() {
        LOG.info("Activate path " + path + ", source: " + source.getClass().getName());
        for (PathListener listener : myPathListeners) {
          listener.notifyPath(event);
        }
      }
    };
    Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      eventDispatch.run();
    } else {
      application.invokeLater(eventDispatch);
    }
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

  private boolean connectToServer() {
    assert !ApplicationManager.getApplication().isDispatchThread();

    myServerConnection = ServerProcess.INSTANCE.connect();
    return myServerConnection.isConnected();
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
    if (myServerConnection != null) {
      myServerConnection.close();
      myServerConnection = null;
    }

    myExecutor.shutdown();
  }

  static File getBinaryPath() {
    return getPaths().myServerDirectory;
  }

  private static Paths getPaths() {
    synchronized (myPathLock) {
      if (myPaths == null || !myPaths.isValid()) {
        myPaths = Paths.get();
      }
      return myPaths;
    }
  }

  private static class ServerProcess {
    public static final ServerProcess INSTANCE = new ServerProcess();
    private static final ServerConnection NOT_CONNECTED = new ServerConnection(INSTANCE, null);

    private static final int SERVER_LAUNCH_TIMEOUT_MS = 2000;
    private static final int SERVER_LAUNCH_SLEEP_INCREMENT_MS = 10;
    private static final String SERVER_HOST = "localhost";

    private final Set<ServerConnection> myConnections = Sets.newIdentityHashSet();
    private Thread myServerThread;
    private int myPort;

    private ServerProcess() {
      Factory.register();
    }

    /**
     * Attempts to connect to a gapis server.
     * <p/>
     * Will launch a new server process if none has been started.
     * <p/>
     * TODO: Implement more robust process management.  For example:
     * TODO: - Better way to detect when server has started in order to avoid polling for the socket.
     */
    public ServerConnection connect() {
      synchronized (this) {
        if (!isServerRunning()) {
          if (!launchServer()) {
            return NOT_CONNECTED;
          }
        }
      }

      // After starting, the server requires a little time before it will be ready to accept connections.
      // This loop polls the server to establish a connection.
      ServerConnection connection = NOT_CONNECTED;
      try {
        for (int waitTime = 0; waitTime < SERVER_LAUNCH_TIMEOUT_MS; waitTime += SERVER_LAUNCH_SLEEP_INCREMENT_MS) {
          if ((connection = attemptToConnect()).isConnected()) {
            LOG.info("Established a new client connection to " + myPort);
            break;
          }
          Thread.sleep(SERVER_LAUNCH_SLEEP_INCREMENT_MS);
        }
      }
      catch (InterruptedException e) {
        Thread.interrupted(); // reset interrupted status
      }
      return connection;
    }

    public void onClose(ServerConnection serverConnection) {
      synchronized (myConnections) {
        myConnections.remove(serverConnection);
        if (myConnections.isEmpty()) {
          LOG.info("Interrupting server thread on last connection close");
          myServerThread.interrupt();
        }
      }
    }

    private ServerConnection attemptToConnect() {
      try {
        ServerConnection connection = new ServerConnection(this, new Socket(SERVER_HOST, myPort));
        synchronized (myConnections) {
          myConnections.add(connection);
        }
        return connection;
      }
      catch (IOException ignored) {
      }
      return NOT_CONNECTED;
    }

    private boolean isServerRunning() {
      return myServerThread != null && myServerThread.isAlive();
    }

    private boolean launchServer() {
      myPort = findFreePort();
      final Paths paths = getPaths();
      // The connection failed, so try to start a new instance of the server.
      if (paths.myGapisPath == null) {
        LOG.warn("Could not find gapis, but needed to start the server.");
        return false;
      }
      myServerThread = new Thread() {
        @Override
        public void run() {
          LOG.info("Launching gapis: \"" + paths.myGapisPath.getAbsolutePath() + "\" on port " + myPort);
          ProcessBuilder pb = new ProcessBuilder(getCommandAndArgs(paths.myGapisPath));

          // Add the server's directory to the path.  This allows the server to find and launch the replayd.
          Map<String, String> env = pb.environment();
          String path = env.get("PATH");
          path = paths.myServerDirectory.getAbsolutePath() + File.pathSeparator + path;
          env.put("PATH", path);

          // Use the plugin directory as the working directory for the server.
          pb.directory(paths.myGapisRoot);
          pb.redirectErrorStream(true);

          Process serverProcess = null;
          try {
            // This will throw IOException if the server executable is not found.
            serverProcess = pb.start();
            final BufferedReader stdout =
              new BufferedReader(new InputStreamReader(serverProcess.getInputStream(), Charset.forName("UTF-8")));
            new Thread() {
              @Override
              public void run() {
                try {
                  for (String line; (line = stdout.readLine()) != null; ) {
                    LOG.warn("gapis: " + line);
                  }
                }
                catch (IOException ignored) {
                }
              }
            }.start();

            int exitValue = serverProcess.waitFor();
            if (exitValue != 0) {
              LOG.warn("The gapis process exited with a non-zero exit value: " + exitValue);
            } else {
              LOG.info("gapis exited cleanly");
            }
          }
          catch (IOException e) {
            LOG.warn(e);
          }
          catch (InterruptedException e) {
            if (serverProcess != null) {
              LOG.info("Killing server process");
              serverProcess.destroy();
            }
          }
        }

        private List<String> getCommandAndArgs(File gapis) {
          List<String> result = Lists.newArrayList();
          result.add(gapis.getAbsolutePath());
          result.add("-shutdown_on_disconnect");
          result.add("-rpc"); result.add(SERVER_HOST + ":" + myPort);
          result.add("-logs"); result.add(PathManager.getLogPath());
          return result;
        }
      };
      myServerThread.start();
      return true;
    }

    private static int findFreePort() {
      ServerSocket socket = null;
      try {
        socket = new ServerSocket(0);
        return socket.getLocalPort();
      }
      catch (IOException e) {
        throw Throwables.propagate(e);
      }
      finally {
        if (socket != null) {
          try {
            socket.close();
          }
          catch (IOException ignored) {
          }
        }
      }
    }
  }

  private static class ServerConnection implements Closeable {
    private final ServerProcess myParent;
    private final Socket myServerSocket;

    public ServerConnection(ServerProcess parent, Socket serverSocket) {
      myParent = parent;
      myServerSocket = serverSocket;
    }

    public boolean isConnected() {
      return myServerSocket != null && myServerSocket.isConnected();
    }

    public ServiceClient createServiceClient(ListeningExecutorService executor) throws IOException {
      if (!isConnected()) {
        throw new IOException("Not connected");
      }
      return new ServiceClientRPC(executor, myServerSocket.getInputStream(), myServerSocket.getOutputStream(), 1024);
    }

    @Override
    public void close() {
      synchronized (this) {
        if (isConnected()) {
          try {
            myServerSocket.close();
          }
          catch (IOException e) {
          }
          myParent.onClose(this);
        }
      }
    }
  }

  private static class Paths {
    private static final String SERVER_EXECUTABLE_NAME = "gapis";
    private static final String SERVER_RELATIVE_PATH = "bin";

    public final File myGapisRoot;
    public final File myServerDirectory;
    public final File myGapisPath;

    public Paths(File gapisRoot, File serverDirectory, File gapisPath) {
      myGapisRoot = gapisRoot;
      myServerDirectory = serverDirectory;
      myGapisPath = gapisPath;
    }

    public boolean isValid() {
      return myGapisPath.exists();
    }

    private static Paths create(File root) {
      File bin = new File(root, SERVER_RELATIVE_PATH);
      File gapis = new File(bin, SERVER_EXECUTABLE_NAME);
      return new Paths(root, bin, gapis);
    }

    public static Paths get() {
      File androidPlugin = PluginPathManager.getPluginHome("android");
      Paths result = create(androidPlugin);
      if (Boolean.getBoolean(IDEA_IS_INTERNAL_PROPERTY)) {
        // Check the default build location for a standard repo checkout
        Paths internal = create(new File(androidPlugin.getParentFile().getParentFile().getParentFile(), "gpu"));
        if (!internal.isValid()) {
          // Check the GOPATH in case it is non standard
          String gopath = System.getenv("GOPATH");
          if (gopath != null && gopath.length() > 0) {
            internal = create(new File(gopath));
          }
        }
        // TODO: Check the prebuilts location
        if (internal.isValid()) {
          result = internal;
        }
      }
      return result;
    }
  }
}
