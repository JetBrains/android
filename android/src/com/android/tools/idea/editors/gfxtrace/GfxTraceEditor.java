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

import com.android.repository.api.LocalPackage;
import com.android.tools.idea.editors.gfxtrace.controllers.MainController;
import com.android.tools.idea.editors.gfxtrace.gapi.*;
import com.android.tools.idea.editors.gfxtrace.models.AtomStream;
import com.android.tools.idea.editors.gfxtrace.models.GpuState;
import com.android.tools.idea.editors.gfxtrace.service.ServiceClient;
import com.android.tools.idea.editors.gfxtrace.service.ServiceClientCache;
import com.android.tools.idea.editors.gfxtrace.service.atom.AtomMetadata;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.android.tools.idea.editors.gfxtrace.service.stringtable.Info;
import com.android.tools.idea.editors.gfxtrace.service.stringtable.StringTable;
import com.android.tools.idea.editors.gfxtrace.widgets.LoadablePanel;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.android.tools.rpclib.rpccore.Rpc;
import com.android.tools.rpclib.rpccore.RpcException;
import com.android.tools.rpclib.schema.ConstantSet;
import com.android.tools.rpclib.schema.Dynamic;
import com.android.tools.rpclib.schema.Entity;
import com.android.tools.rpclib.schema.Message;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class GfxTraceEditor extends UserDataHolderBase implements FileEditor {
  private static final Version REQUIRED_GAPI_VERSION = Version.VERSION_1;

  private static final int FETCH_SCHEMA_TIMEOUT_MS = 3000;
  private static final int FETCH_FEATURES_TIMEOUT_MS = 3000;
  private static final int FETCH_STRING_TABLE_TIMEOUT_MS = 3000;
  private static final int FETCH_REPLAY_DEVICE_TIMEOUT_MS = 3000;
  private static final int FETCH_REPLAY_DEVICE_RETRY_DELAY_MS = 3000;
  private static final int FETCH_REPLAY_DEVICE_MAX_RETRIES = 30;
  private static final int FETCH_TRACE_TIMEOUT_MS = 30000;

  @NotNull public static final String LOADING_CAPTURE = "Loading capture...";
  @NotNull public static final String SELECT_ATOM = "Select a frame or command";
  @NotNull public static final String SELECT_DRAW_CALL = "Select a draw call";
  @NotNull public static final String SELECT_MEMORY = "Select a memory range or pointer in the command list";
  @NotNull public static final String SELECT_TEXTURE = "Select a texture";
  @NotNull public static final String NO_TEXTURES = "No textures have been created by this point";

  @NotNull private static final Logger LOG = Logger.getInstance(GfxTraceEditor.class);

  @NotNull private final Project myProject;
  @NotNull private final JBPanel myView = new JBPanel(new BorderLayout());
  @NotNull private final LoadablePanel myLoadingPanel = new LoadablePanel(myView);
  @NotNull private final ListeningExecutorService myExecutor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
  @NotNull private final AtomStream myAtomStream = new AtomStream(this);
  @NotNull private final GpuState myState = new GpuState(this);
  @NotNull private final VirtualFile myFile;
  @NotNull private final JComponent myMainUi;

  @NotNull private final List<PathListener> myPathListeners = new ArrayList<PathListener>();
  @NotNull private final PathStore<Path> myLastActivatadPath = new PathStore<Path>();

  @Nullable private GapisConnection myGapisConnection;
  @Nullable private ServiceClient myClient;

  public static boolean isEnabled() {
    return true;
  }

  public GfxTraceEditor(@NotNull final Project project, @SuppressWarnings("UnusedParameters") @NotNull final VirtualFile file) {
    myProject = project;
    myFile = file;
    myLoadingPanel.setLoadingText("Initializing GFX Trace System");

    addPathListener(myAtomStream);
    addPathListener(myState);

    // we need to create the UI before we call connect, as the UI will listen to events that happen during connection.
    myMainUi = MainController.createUI(this);

    connect();
  }

  private void connect() {
    myLoadingPanel.startLoading();

    // Attempt to start/connect to the server on a separate thread to reduce the IDE from stalling.
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        if (installNeeded()) {
          HyperlinkLabel link = new HyperlinkLabel("(install here)");
          link.addHyperlinkListener(new HyperlinkListener() {
            @Override
            public void hyperlinkUpdate(HyperlinkEvent e) {
              Collection<String> requested = ImmutableList.of(GapiPaths.SDK_PACKAGE_PATH);
              // TODO do we want to tell it to install a specific version?
              ModelWizardDialog dialog = SdkQuickfixUtils.createDialogForPaths(myProject, requested);
              if (dialog != null) {
                dialog.setTitle("Install Missing Components");
                if (dialog.showAndGet()) {
                  connect();
                }
              }
            }
          });
          setLoadingErrorTextOnEdt("GPU debugging SDK not installed", link);
          return;
        }

        try {
          doConnect();
          ApplicationManager.getApplication().invokeLater(() -> {
            myView.add(myMainUi, BorderLayout.CENTER);
            myLoadingPanel.stopLoading();
          });
        }
        catch (GapisInitException e) {
          setLoadingErrorTextOnEdt(e.getMessage(), null);
        }
      }
    });
  }

  private void doConnect() throws GapisInitException {
    if (!isEnabled()) {
      throw GapisInitException.notEnabled();
    }
    connectToServer();

    String status = "";
    try {
      status = "fetch schema";
      fetchSchema();

      status = "fetch feature list";
      GapisFeatures features = myGapisConnection.getFeatures();
      fetchFeatures(features);

      if (features.hasRpcStringTables()) {
        status = "fetch string table";
        fetchStringTable();
      }
    }
    catch (ExecutionException | RpcException | TimeoutException e) {
      LOG.error("Failed to " + status, e);
      throw GapisInitException.initFailed();
    }

    fetchReplayDevice();
    fetchTrace(myFile);
  }

  private static boolean installNeeded() {
    // install needed if we have no version installed
    if (!GapiPaths.isValid()) {
      return true;
    }
    // OR we do have a version installed from the SDK manager, but it does not match our required version
    LocalPackage gapi = GapiPaths.getLocalPackage();
    return gapi != null && !REQUIRED_GAPI_VERSION.isCompatible(gapi.getVersion());
  }

  /**
   * Requests and blocks for the schema from the server.
   */
  private void fetchSchema() throws ExecutionException, RpcException, TimeoutException {
    Message schema = Rpc.get(myClient.getSchema(), FETCH_SCHEMA_TIMEOUT_MS, TimeUnit.MILLISECONDS);
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

  /**
   * Requests and blocks for the features list from the server.
   */
  private void fetchFeatures(GapisFeatures features) throws ExecutionException, RpcException, TimeoutException {
    String[] list = Rpc.get(myClient.getFeatures(), FETCH_FEATURES_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    features.setFeatureList(list);
    LOG.info("GAPIS features: " + Arrays.toString(list));
  }

  /**
   * Requests, blocks, and then makes current the string table from the server.
   */
  private void fetchStringTable() throws ExecutionException, RpcException, TimeoutException {
    Info[] infos = Rpc.get(myClient.getAvailableStringTables(), FETCH_STRING_TABLE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    if (infos.length == 0) {
      LOG.warn("No string tables available");
      return;
    }
    Info info = infos[0];
    StringTable table = Rpc.get(myClient.getStringTable(info), FETCH_STRING_TABLE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    table.setCurrent();
  }

  /**
   * Requests and blocks for the schema from the server.
   */
  private void fetchReplayDevice() throws GapisInitException {
    for (int i = 0; i < FETCH_REPLAY_DEVICE_MAX_RETRIES; i++) {
      try {
        DevicePath[] devices = Rpc.get(getClient().getDevices(), FETCH_REPLAY_DEVICE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (devices != null && devices.length >= 1) {
          activatePath(devices[0], GfxTraceEditor.this);
          return;
        }
      }
      catch (RpcException | TimeoutException | ExecutionException e) {
        // Ignored, retry.
      }
      try {
        Thread.sleep(FETCH_REPLAY_DEVICE_RETRY_DELAY_MS);
      }
      catch (InterruptedException e) {
        break;
      }
    }
    throw GapisInitException.noReplayDevice();
  }

  /**
   * Uploads or requests the capture path from the server and then activates the path.
   */
  private void fetchTrace(VirtualFile file) throws GapisInitException {
    try {
      final ListenableFuture<CapturePath> captureF;
      if (file.getFileSystem().getProtocol().equals(StandardFileSystems.FILE_PROTOCOL)) {
        LOG.info("Load gfxtrace in " + file.getPresentableName());
        if (file.getLength() == 0) {
          throw GapisInitException.emptyTrace(file);
        }
        captureF = myClient.loadCapture(file.getCanonicalPath());
      }
      else {
        // Upload the trace file
        byte[] data = file.contentsToByteArray();
        LOG.info("Upload " + data.length + " bytes of gfxtrace as " + file.getPresentableName());
        if (data.length == 0) {
          throw GapisInitException.emptyTrace(file);
        }
        captureF = myClient.importCapture(file.getPresentableName(), data);
      }

      CapturePath path = Rpc.get(captureF, FETCH_TRACE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      if (path == null) {
        throw GapisInitException.invalidTrace(file);
      }

      activatePath(path, this);
    }
    catch (ExecutionException | RpcException | TimeoutException | IOException e) {
      LOG.warn("Loading trace failed", e);
      throw GapisInitException.loadTraceFailed(file);
    }
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myLoadingPanel;
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

  public String getSessionName() {
    return myFile.getName();
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
  public AtomStream getAtomStream() {
    return myAtomStream;
  }

  @NotNull
  public GpuState getGpuState() {
    return myState;
  }

  @NotNull
  public ListeningExecutorService getExecutor() {
    return myExecutor;
  }

  @Override
  public void dispose() {
    shutdown();
  }

  private void connectToServer() throws GapisInitException {
    assert !ApplicationManager.getApplication().isDispatchThread();

    myGapisConnection = GapisProcess.connect();
    if (!myGapisConnection.isConnected()) {
      throw GapisInitException.notConnected();
    }

    try {
      myClient = new ServiceClientCache(myGapisConnection.createServiceClient(myExecutor), myExecutor);
    }
    catch (IOException e) {
      LOG.error("Unable to create client from connection", e);
      throw GapisInitException.notConnected();
    }
  }

  private void setLoadingErrorTextOnEdt(@NotNull final String error, @Nullable Component errorComponent) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        myLoadingPanel.showLoadingError(error, errorComponent);
      }
    });
  }

  private void shutdown() {
    if (myGapisConnection != null) {
      myGapisConnection.close();
      myGapisConnection = null;
    }

    myExecutor.shutdown();
  }

  private static class GapisInitException extends Exception {
    public GapisInitException(String message) {
      super(message);
    }

    public static GapisInitException notEnabled() {
      return new GapisInitException("Graphics debugger not enabled on this system");
    }

    public static GapisInitException notConnected() {
      return new GapisInitException("Failed to connect to the graphics debugger");
    }

    public static GapisInitException initFailed() {
      return new GapisInitException("Failed to initialize the graphics debugger");
    }

    public static GapisInitException noReplayDevice() {
      return new GapisInitException("No usable replay device found");
    }

    public static GapisInitException emptyTrace(VirtualFile file) {
      return new GapisInitException("Empty trace file " + file.getPresentableName());
    }

    public static GapisInitException invalidTrace(VirtualFile file) {
      return new GapisInitException("Invalid/Corrupted trace file " + file.getPresentableName());
    }

    public static GapisInitException loadTraceFailed(VirtualFile file) {
      return new GapisInitException("Failed to load trace file " + file.getPresentableName());
    }
  }
}
