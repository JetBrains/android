/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.transport;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.tools.datastore.DataStoreService;
import com.android.tools.idea.adb.AdbService;
import com.android.tools.idea.concurrent.EdtExecutor;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectLifecycleListener;
import com.intellij.util.messages.MessageBusConnection;
import java.io.File;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An application-level service for establishing a connection to a device, which can then be used to retrieve Android system and app data.
 * The service is application-level because devices/processes are accessible through multiple projects, and we want the pipeline to work
 * across projects as long as they share the same adb.
 */
public class TransportService {
  private static Logger getLogger() {
    return Logger.getInstance(TransportService.class);
  }

  public synchronized static TransportService getInstance() {
    return ServiceManager.getService(TransportService.class);
  }

  private static final String DATASTORE_NAME = "DataStoreService";

  @NotNull private final DataStoreService myDataStoreService;
  @NotNull private final TransportClient myTransportClient;
  @NotNull private final Set<Project> myProjects = new TreeSet<>();
  @Nullable private File myCurrentAdb;

  private TransportService() {
    // Hook up the listener for detecting project open and close events.
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(ProjectLifecycleListener.TOPIC, new ProjectLifecycleListener() {
      @Override
      public void projectComponentsInitialized(@NotNull Project project) {
        registerProject(project);
      }

      @Override
      public void afterProjectClosed(@NotNull Project project) {
        unregisterProject(project);
      }
    });

    String datastoreDirectory = Paths.get(PathManager.getSystemPath(), ".android").toString() + File.separator;
    myDataStoreService =
      new DataStoreService(DATASTORE_NAME, datastoreDirectory, ApplicationManager.getApplication()::executeOnPooledThread,
                           new IntellijLogService());
    myTransportClient = new TransportClient(DATASTORE_NAME);

    // TODO b/73538507 handle the device deamon/app agent initialization logic.
  }

  @Nullable
  public synchronized TransportClient getClient(@NotNull Project project) {
    if (registerProject(project)) {
      return myTransportClient;
    }
    else {
      // Technically here we can still return the client even if the project's adb is not compatible. (e.g. Caller can still have access to
      // the data already in the database). Return null for now to keep things simple.
      // TODO b/123475451 properly notify user when this happens. Perhaps show a balloon warning?
      getLogger().warn("Unable to obtain pipeline client for the project.");
      return null;
    }
  }

  /**
   * Register a newly loaded project with the pipeline. This initializes the AndroidDebugBridge if it hasn't been initialized already, so
   * that the pipeline can obtain the list of Device and Apps that it can establish connections to. Note that if the Project's SDK points
   * to a different adb than the one already initialized, the project will not be registered with the pipeline.
   */
  private synchronized boolean registerProject(@NotNull Project project) {
    if (myProjects.isEmpty() || isProjectCompatible(project)) {
      // initialize adb using the project's sdk.
      if (initializeAdb(project)) {
        myProjects.add(project);

        // TODO handle project-specific logic, if any.
        return true;
      }
    }

    return false;
  }

  /**
   * Unregister a closed project. This allows other projects using a different adb to register with the pipeline.
   */
  private synchronized void unregisterProject(@NotNull Project project) {
    myProjects.remove(project);
    if (myProjects.isEmpty()) {
      myCurrentAdb = null;
    }
  }

  private boolean initializeAdb(@NotNull Project project) {
    if (myCurrentAdb != null) {
      return true;
    }

    File adb = AndroidSdkUtils.getAdb(project);
    if (adb == null) {
      getLogger().warn("No adb available");
      return false;
    }

    Futures.addCallback(AdbService.getInstance().getDebugBridge(adb), new FutureCallback<AndroidDebugBridge>() {
      @Override
      public void onSuccess(@NotNull AndroidDebugBridge result) {
        myCurrentAdb = adb;
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        getLogger().warn(String.format("getDebugBridge %s failed", adb.getAbsolutePath()));
      }
    }, EdtExecutor.INSTANCE);

    return true;
  }

  private boolean isProjectCompatible(@NotNull Project project) {
    File adb = AndroidSdkUtils.getAdb(project);
    return myCurrentAdb == null || (adb != null && adb.getAbsolutePath().equals(myCurrentAdb.getAbsolutePath()));
  }
}
