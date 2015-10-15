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

import com.android.ide.common.repository.SdkMavenRepository;
import com.android.sdklib.SdkManager;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.tools.idea.sdk.remote.RemotePkgInfo;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

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
                               "installation (if necessary) or install a new version.");
  }

  /**
   * Find latest build tools revision. Versions compatible with the selected platforms will be installed by the platform components.
   * @return The FullRevision of the latest build tools package, or null if no remote build tools packages are available.
   */
  @Nullable
  private static FullRevision getLatestCompatibleBuildToolsRevision(@NotNull Multimap<PkgType, RemotePkgInfo> packages) {
    FullRevision revision = null;
    Collection<RemotePkgInfo> tools = packages.get(PkgType.PKG_BUILD_TOOLS);
    for (RemotePkgInfo tool : tools) {
      FullRevision fullRevision = tool.getPkgDesc().getFullRevision();
      // We never want to push preview platforms on users
      if (fullRevision == null || fullRevision.isPreview()) {
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
  public Collection<IPkgDesc> getRequiredSdkPackages(@Nullable Multimap<PkgType, RemotePkgInfo> remotePackages) {
    Collection<IPkgDesc> result = Lists.newArrayList();
    result.add(PkgDesc.Builder.newTool(FullRevision.NOT_SPECIFIED, FullRevision.NOT_SPECIFIED).create());
    result.add(PkgDesc.Builder.newPlatformTool(FullRevision.NOT_SPECIFIED).create());
    if (remotePackages != null) {
      FullRevision revision = getLatestCompatibleBuildToolsRevision(remotePackages);
      if (revision != null) {
        result.add(PkgDesc.Builder.newBuildTool(revision).create());
      }
    }

    for (SdkMavenRepository repository : SdkMavenRepository.values()) {
      result.add(repository.getPackageDescription());
    }
    return result;
  }

  @Override
  public void configure(@NotNull InstallContext installContext, @NotNull File sdk) {
    // Nothing to do, having components installed is enough
  }

  @Override
  protected boolean isOptionalForSdkLocation(@Nullable SdkManager manager) {
    return false;
  }
}
