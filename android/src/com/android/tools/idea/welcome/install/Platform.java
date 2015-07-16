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
package com.android.tools.idea.welcome.install;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkManager;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.MajorRevision;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.sdklib.repository.local.LocalSdk;
import com.android.tools.idea.sdk.remote.RemotePkgInfo;
import com.android.tools.idea.welcome.wizard.InstallComponentsPath;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

/**
 * <p>Install Android SDK components for developing apps targeting Lollipop
 * platform.</p>
 * <p>Default selection logic:
 * <ol>
 * <li>If the component of this kind are already installed, they cannot be
 * unchecked (e.g. the wizard will not uninstall them)</li>
 * <li>If SDK does not have any platforms installed (or this is a new
 * SDK installation), then only the latest platform will be installed.</li>
 * </ol></p>
 */
public class Platform extends InstallableComponent {
  private final AndroidVersion myVersion;
  private final boolean myIsDefaultPlatform;

  public Platform(@NotNull ScopedStateStore store,
                  @NotNull String name,
                  long size,
                  @NotNull String description,
                  AndroidVersion version,
                  boolean isDefaultPlatform) {
    super(store, name, size, description);
    myVersion = version;
    myIsDefaultPlatform = isDefaultPlatform;
  }

  private static Platform getLatestPlatform(@NotNull ScopedStateStore store,
                                            Multimap<PkgType, RemotePkgInfo> remotePackages,
                                            boolean preview) {
    RemotePkgInfo latest = InstallComponentsPath.findLatestPlatform(remotePackages, preview);
    if (latest != null) {
      AndroidVersion version = latest.getPkgDesc().getAndroidVersion();
      String versionName = SdkVersionInfo.getAndroidName(version.getFeatureLevel());
      final String description = "Android platform libraries for targeting " + versionName + " platform";
      return new Platform(store, versionName, latest.getDownloadSize(), description, version, !version.isPreview());
    }
    return null;
  }

  @NotNull
  private static LocalPkgInfo[] getPlatformPackages(@Nullable SdkManager manager) {
    if (manager != null) {
      LocalSdk localSdk = manager.getLocalSdk();
      return localSdk.getPkgsInfos(PkgType.PKG_PLATFORM);
    }
    else {
      return new LocalPkgInfo[0];
    }
  }

  public static ComponentTreeNode createSubtree(@NotNull ScopedStateStore store, Multimap<PkgType, RemotePkgInfo> remotePackages) {
    ComponentTreeNode latestPlatform = getLatestPlatform(store, remotePackages, false);
    ComponentTreeNode previewPlatform = null;
    // We never want to push preview platforms on our users (see http://b.android.com/175343 for more)
    //   ComponentTreeNode previewPlatform = getLatestPlatform(store, remotePackages, true);
    //noinspection ConstantConditions
    if (previewPlatform != null) {
      if (latestPlatform != null) {
        return new ComponentCategory("Android SDK Platform", "SDK components for creating applications for different Android platforms",
                                     latestPlatform, previewPlatform);
      }
      latestPlatform = previewPlatform;  // in case somehow we have a preview but no non-preview
    }
    if (latestPlatform != null) {
      return new ComponentCategory("Android SDK Platform", "SDK components for creating applications for different Android platforms",
                                   latestPlatform);
    }
    return null;
  }

  @NotNull
  @Override
  public Collection<IPkgDesc> getRequiredSdkPackages(@Nullable Multimap<PkgType, RemotePkgInfo> remotePackages) {
    MajorRevision unspecifiedRevision = new MajorRevision(FullRevision.NOT_SPECIFIED);
    PkgDesc.Builder platform = PkgDesc.Builder.newPlatform(myVersion, unspecifiedRevision, FullRevision.NOT_SPECIFIED);
    PkgDesc.Builder platformSources = PkgDesc.Builder.newSource(myVersion, unspecifiedRevision);
    PkgDesc.Builder buildTool = PkgDesc.Builder.newBuildTool(findLatestCompatibleBuildTool(remotePackages, myVersion));
    return ImmutableList.of(platform.create(), platformSources.create(), buildTool.create());
  }

  private static FullRevision findLatestCompatibleBuildTool(@Nullable Multimap<PkgType, RemotePkgInfo> remotePackages,
                                                            AndroidVersion version) {
    FullRevision revision = null;
    if (remotePackages != null) {
      for (RemotePkgInfo remotePkgInfo : remotePackages.get(PkgType.PKG_BUILD_TOOLS)) {
        FullRevision testRevision = remotePkgInfo.getPkgDesc().getFullRevision();
        if (testRevision != null &&
            testRevision.getMajor() == version.getApiLevel() && (revision == null || testRevision.compareTo(revision) > 0)) {
          revision = testRevision;
        }
      }
    }
    return revision;
  }

  @Override
  public void configure(@NotNull InstallContext installContext, @NotNull File sdk) {
  }

  @Override
  public boolean isOptionalForSdkLocation(@Nullable SdkManager manager) {
    LocalPkgInfo[] infos = getPlatformPackages(manager);
    if (infos.length == 0) {
      return !myIsDefaultPlatform;
    }
    for (LocalPkgInfo info : infos) {
      IPkgDesc desc = info.getDesc();
      // No unchecking if the platform is already installed. We can update but not remove existing platforms
      AndroidVersion androidVersion = desc.getAndroidVersion();
      int apiLevel = androidVersion == null ? 0 : androidVersion.getApiLevel();
      if (myVersion.getFeatureLevel() == apiLevel) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean isSelectedByDefault(@Nullable SdkManager sdkManager) {
    return false;
  }
}
