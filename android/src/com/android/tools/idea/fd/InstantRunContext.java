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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link InstantRunContext} provides the project specific context necessary for {@link InstantRunBuilder} to perform
 * an Instant Run (IR) build.
 */
public interface InstantRunContext {
  /**
   * @return the application id (package name) of the Android application in this project.
   */
  @NotNull
  String getApplicationId();

  /**
   * An IR enabled app has a server embedded within it that listens for changes sent from the host. In order to restrict who
   * can communicate to that server, a secret token is embedded within the app, and anyone who wishes to communicate must send
   * this token.
   *
   * Typically, this token is the project path on disk, which is only available on the host where the build takes place.
   *
   * NOTE: See b/28200898. Currently Gradle doesn't inject this and assumes that the key is the Gradle project path.
   *
   * @return a secret token that will be embedded within the IR capable app and will be necessary to communicate to the app.
   */
  long getSecretToken();

  /**
   * @return the hash of the current manifest. A change to the manifest will result in a full build and install.
   */
  @NotNull
  HashCode getManifestHash();

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

  @NotNull
  default InstalledPatchCache getInstalledPatchCache() {
    return ServiceManager.getService(InstalledPatchCache.class);
  }
}
