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

import com.android.tools.datastore.DataStoreService;
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
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Paths;

public class ProfilerService implements Disposable {

  private static final Key<Boolean> DATA_KEY = Key.create("PROJECT_PROFILER_SERVICE");

  public static ProfilerService getInstance(@NotNull Project project) {
    ProfilerService service = ServiceManager.getService(project, ProfilerService.class);
    return service;
  }

  public static boolean isServiceInitialized(@NotNull Project project) {
    return project.getUserData(DATA_KEY) != null;
  }

  private static final String DATASTORE_NAME_PREFIX = "DataStoreService";

  @NotNull
  private final StudioProfilerDeviceManager myManager;
  @NotNull
  private final ProfilerClient myClient;
  @NotNull
  private final DataStoreService myDataStoreService;

  private ProfilerService(@NotNull Project project) {
    String datastoreDirectory = Paths.get(PathManager.getSystemPath(), ".android").toString() + File.separator;

    NativeSymbolizer symbolizer = NativeSymbolizerKt.createNativeSymbolizer(project);
    Disposer.register(this, () -> symbolizer.stop());

    String datastoreName = DATASTORE_NAME_PREFIX + project.getLocationHash();
    myDataStoreService = new DataStoreService(datastoreName, datastoreDirectory, ApplicationManager.getApplication()::executeOnPooledThread,
                                              new IntellijLogService());
    Disposer.register(this, () -> myDataStoreService.shutdown());
    myDataStoreService.setNativeSymbolizer(symbolizer);

    myManager = new StudioProfilerDeviceManager(myDataStoreService);
    Disposer.register(this, myManager);
    myManager.initialize(project);
    IdeSdks.subscribe(myManager, this);

    myClient = new ProfilerClient(datastoreName);

    project.putUserData(DATA_KEY, true);
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
}
