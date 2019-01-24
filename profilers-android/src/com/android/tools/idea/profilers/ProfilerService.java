/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.profilers;

import com.android.annotations.concurrency.GuardedBy;
import com.android.tools.datastore.DataStoreService;
import com.android.tools.idea.transport.IntellijLogService;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.nativeSymbolizer.NativeSymbolizer;
import com.android.tools.nativeSymbolizer.NativeSymbolizerKt;
import com.android.tools.profilers.ProfilerClient;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Paths;

public class ProfilerService implements Disposable {
  /**
   * Currently Profiler needs to be run as a singleton. Keeps track of the Project the ProfilerService is initialized in to prevent users
   * from creating multiple.
   * TODO b\79772836: make ProfilerService an application-level service in order to remove these constraints.
   */
  @GuardedBy("ourServiceLock")
  private static Project ourInitializedProject = null;
  private static final Object ourServiceLock = new Object();

  /**
   * @return The ProfilerService if one is available. Note that at most one project's ProfilerService can be alive at a time. If another
   * project attempts to initialize its ProfilerService, this method returns null.
   */
  @Nullable
  public static ProfilerService getInstance(@NotNull Project project) {
    synchronized (ourServiceLock) {
      if (ourInitializedProject == null || ourInitializedProject == project) {
        return ServiceManager.getService(project, ProfilerService.class);
      } else {
        return null;
      }
    }
  }

  public static boolean isServiceInitialized(@NotNull Project project) {
    synchronized (ourServiceLock) {
      return ourInitializedProject == project;
    }
  }

  private static final String DATASTORE_NAME_PREFIX = "DataStoreService";

  @NotNull
  private final StudioProfilerDeviceManager myManager;
  @NotNull
  private final ProfilerClient myClient;
  @NotNull
  private final DataStoreService myDataStoreService;
  @NotNull
  private final NativeSymbolizer myNativeSymbolizer;

  private ProfilerService(@NotNull Project project) {
    String datastoreDirectory = Paths.get(PathManager.getSystemPath(), ".android").toString() + File.separator;

    myNativeSymbolizer = NativeSymbolizerKt.createNativeSymbolizer(project);
    Disposer.register(this, () -> myNativeSymbolizer.stop());

    String datastoreName = DATASTORE_NAME_PREFIX + project.getLocationHash();
    myDataStoreService = new DataStoreService(datastoreName, datastoreDirectory, ApplicationManager.getApplication()::executeOnPooledThread,
                                              new IntellijLogService());
    Disposer.register(this, () -> myDataStoreService.shutdown());
    myDataStoreService.setNativeSymbolizer(myNativeSymbolizer);

    myManager = new StudioProfilerDeviceManager(myDataStoreService);
    Disposer.register(this, myManager);
    myManager.initialize(project);
    IdeSdks.subscribe(myManager, this);

    myClient = new ProfilerClient(datastoreName);

    ourInitializedProject = project;
    Disposer.register(this, () -> {
      synchronized (ourServiceLock) {
        ourInitializedProject = null;
      }
    });
  }

  @Override
  public void dispose() {
    // All actual disposing is done via Disposer.register in the constructor.
  }

  @NotNull
  public ProfilerClient getProfilerClient() {
    return myClient;
  }

  @NotNull
  public DataStoreService getDataStoreService() {
    return myDataStoreService;
  }

  @NotNull
  public NativeSymbolizer getNativeSymbolizer() {
    return myNativeSymbolizer;
  }
}
