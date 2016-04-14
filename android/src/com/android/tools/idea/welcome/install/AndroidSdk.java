/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome.install;

import com.android.SdkConstants;
import com.android.ide.common.repository.SdkMavenRepository;
import com.android.repository.Revision;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoPackage;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Map;

/**
 * Android SDK installable component.
 */
public final class AndroidSdk extends InstallableComponent {
  public static final long SIZE = 265 * Storage.Unit.MiB.getNumberOfBytes();

  public AndroidSdk(@NotNull ScopedStateStore store) {
    super(store, "Android SDK", SIZE, "The collection of Android platform APIs, " +
                               "tools and utilities that enables you to debug, " +
                               "profile, and compile your apps.\n\n" +
                               "The setup wizard will update your current Android SDK " +
                               "installation (if necessary) or install a new version.", FileOpUtils.create());
  }

  /**
   * Find latest build tools revision. Versions compatible with the selected platforms will be installed by the platform components.
   * @return The Revision of the latest build tools package, or null if no remote build tools packages are available.
   */
  @Nullable
  private static Revision getLatestCompatibleBuildToolsRevision(@NotNull Map<String, RemotePackage> packages) {
    Revision revision = null;
    for (RemotePackage p : packages.values()) {
      if (!p.getPath().startsWith(SdkConstants.FD_BUILD_TOOLS)) {
        continue;
      }

      Revision fullRevision = p.getVersion();
      // We never want to push preview platforms on users
      if (fullRevision.isPreview()) {
        continue;
      }
      if (revision == null || fullRevision.compareTo(revision) > 0) {
        revision = fullRevision;
      }
    }
    return revision;
  }

  @NotNull
  @Override
  public Collection<String> getRequiredSdkPackages(@Nullable Map<String, RemotePackage> remotePackages) {
    Collection<String> result = Lists.newArrayList();
    result.add(SdkConstants.FD_TOOLS);
    result.add(SdkConstants.FD_PLATFORM_TOOLS);
    if (remotePackages != null) {
      Revision revision = getLatestCompatibleBuildToolsRevision(remotePackages);
      if (revision != null) {
        result.add(SdkConstants.FD_BUILD_TOOLS + RepoPackage.PATH_SEPARATOR + revision.toString());
      }
    }

    for (SdkMavenRepository repository : SdkMavenRepository.values()) {
      result.add(repository.getRepositoryLocation(new File(""), false).getPath().substring(1)
                   .replace(File.separatorChar, RepoPackage.PATH_SEPARATOR));
    }
    return result;
  }

  @Override
  public void configure(@NotNull InstallContext installContext, @NotNull AndroidSdkHandler sdkHandler) {
    // Nothing to do, having components installed is enough
  }

  @Override
  protected boolean isOptionalForSdkLocation(@Nullable AndroidSdkHandler handler) {
    return false;
  }
}
