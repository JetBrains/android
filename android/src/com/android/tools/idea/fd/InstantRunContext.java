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
package com.android.tools.idea.fd;

import com.android.tools.fd.client.InstantRunBuildInfo;
import com.android.tools.idea.run.InstalledPatchCache;
import com.google.common.hash.HashCode;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * {@link InstantRunContext} provides the project specific context necessary for {@link InstantRunBuilder} to perform
 * an Instant Run (IR) build.
 */
public interface InstantRunContext {
  Key<InstantRunContext> KEY = Key.create("android.instant.run.context");

  /**
   * @return the application id (package name) of the Android application in this project.
   */
  @NotNull
  String getApplicationId();

  /**
   * @return a hashcode which encapsulates the set of resources referenced from the manifest along with the values of those resources.
   * A change to the resources referenced from the manifest should result in a full build and install.
   */
  @NotNull
  HashCode getManifestResourcesHash();

  /**
   * Indicates whether the application uses multiple processes (via the :process tag inside the manifest).
   * Instant run will have to terminate and restart the app (i.e. force a cold swap, or a full install depending on API level) if the app
   * uses multiple processes
   * @return true if the manifest indicates that the application uses different processes
   */
  boolean usesMultipleProcesses();

  /**
   * Returns the changes that have happened in the IDE since the previous call to this method. If the IDE knows that only certain
   * parts of the application have changed, then it can provide that information to the build system to help speed up which tasks
   * of the build system are run. This method is optional and can return null if this information is not available.
   *
   * @return the changes that have happened in this project since the last build, or null if change information is not available.
   */
  @Nullable
  FileChangeListener.Changes getFileChangesAndReset();

  /**
   * Returns the {@link InstantRunBuildInfo} that is generated once an IR build has completed.
   */
  @Nullable
  InstantRunBuildInfo getInstantRunBuildInfo();

  /**
   * Store the build mode picked by {@link InstantRunBuilder}.
   */
  void setBuildSelection(@NotNull BuildSelection buildSelection);

  /**
   * Returns the build mode set via the call to {@link #setBuildSelection(BuildSelection)}.
   */
  @Nullable
  BuildSelection getBuildSelection();

  @NotNull
  default InstalledPatchCache getInstalledPatchCache() {
    return ServiceManager.getService(InstalledPatchCache.class);
  }

  /**
   * @return additional project specific arguments passed on to the {@link InstantRunBuilder}
   */
  @NotNull
  default List<String> getCustomBuildArguments() {
    return Collections.emptyList();
  }
}
