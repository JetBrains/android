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

import com.android.tools.idea.editors.gfxtrace.controllers.MainController;
import com.android.tools.idea.editors.gfxtrace.gapi.GapiPaths;
import com.android.tools.idea.editors.gfxtrace.gapi.GapisConnection;
import com.android.tools.idea.editors.gfxtrace.gapi.GapisProcess;
import com.android.tools.idea.editors.gfxtrace.service.ServiceClient;
import com.android.tools.idea.editors.gfxtrace.service.ServiceClientCache;
import com.android.tools.idea.editors.gfxtrace.service.atom.AtomMetadata;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.android.tools.rpclib.binary.BinaryObject;
import com.android.tools.rpclib.schema.ConstantSet;
import com.android.tools.rpclib.schema.Dynamic;
import com.android.tools.rpclib.schema.Entity;
import com.android.tools.rpclib.schema.Message;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class GfxTraceEditor extends UserDataHolderBase implements FileEditor {
  @NotNull public static final String LOADING_CAPTURE = "Loading capture...";
  @NotNull public static final String SELECT_ATOM = "Select a frame or command";
  @NotNull public static final String SELECT_MEMORY = "Select a memory range in the command list";
  @NotNull public static final String SELECT_TEXTURE = "Select a texture";
  @NotNull public static final String NO_TEXTURES = "No textures have been created by this point";


  @NotNull private static final Logger LOG = Logger.getInstance(GfxTraceEditor.class);

  @NotNull private final Project myProject;
  @NotNull private LoadingDecorator myLoadingDecorator;
  @NotNull private JBPanel myView = new JBPanel(new BorderLayout());
  @NotNull private final ListeningExecutorService myExecutor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
  private GapisConnection myGapisConnection;
  private ServiceClient myClient;

  @NotNull private List<PathListener> myPathListeners = new ArrayList<PathListener>();
  @NotNull private PathStore<Path> myLastActivatadPath = new PathStore<Path>();

  public static boolean isEnabled() {
    return true;
  }

  public GfxTraceEditor(@NotNull final Project project, @SuppressWarnings("UnusedParameters") @NotNull final VirtualFile file) {
    myProject = project;
    myLoadingDecorator = new LoadingDecorator(myView, this, 0);
    myLoadingDecorator.setLoadingText("Initializing GFX Trace System");
    myLoadingDecorator.startLoading(false);

    final JComponent mainUi = MainController.createUI(GfxTraceEditor.this);

    // Attempt to start/connect to the server on a separate thread to reduce the IDE from stalling.
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        if (!isEnabled()) {
          setLoadingErrorTextOnEdt("GFX Trace System not enabled on this host");
          return;
        }

        if (!GapiPaths.isValid()) {
          setLoadingErrorTextOnEdt("GPU debugging SDK not installed");
          return;
        }

        if (!connectToServer()) {
          setLoadingErrorTextOnEdt("Unable to connect to server");
          return;
        }

        try {
          myClient = new ServiceClientCache(myGapisConnection.createServiceClient(myExecutor));
        }
        catch (IOException e) {
          setLoadingErrorTextOnEdt("Unable to talk to server");
          return;
        }

        loadReplayDevice();

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

              ApplicationManager.getApplication().invokeLater(new Runnable() {
                @Override
                public void run() {
                  myView.add(mainUi, BorderLayout.CENTER);
                  myLoadingDecorator.stopLoading();
                }
              });
            }
          });
        }
        catch (IOException e) {
          setLoadingErrorTextOnEdt("Error reading gfxtrace file");
          return;
        }
      }
    });
  }

  public void loadReplayDevice() {
    Futures.addCallback(getClient().getDevices(), new LoadingCallback<DevicePath[]>(LOG) {
      @Override
      public void onSuccess(@Nullable DevicePath[] devices) {
        if (devices != null && devices.length >= 1) {
          activatePath(devices[0], GfxTraceEditor.this);
        }
        else {
          JobScheduler.getScheduler().schedule(new Runnable() {
            @Override
            public void run() {
              loadReplayDevice();
            }
          }, 500, TimeUnit.MILLISECONDS);
        }
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
    return myLoadingDecorator.getComponent();
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

    myGapisConnection = GapisProcess.connect();
    return myGapisConnection.isConnected();
  }

  private void setLoadingErrorTextOnEdt(@NotNull final String error) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        myLoadingDecorator.setLoadingText(error);
        myLoadingDecorator.startLoading(false);
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
}
