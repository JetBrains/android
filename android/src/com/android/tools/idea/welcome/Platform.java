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
package com.android.tools.idea.welcome;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkManager;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.MajorRevision;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.sdklib.repository.local.LocalSdk;
import com.android.sdklib.repository.remote.RemotePkgInfo;
import com.android.tools.idea.wizard.ScopedStateStore;
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
  private static final long SIZE = 305 * Storage.Unit.MiB.getNumberOfBytes();
  private final AndroidVersion myVersion;
  private final boolean myIsDefaultPlatform;

  public Platform(@NotNull ScopedStateStore store, @NotNull String name, long size, @NotNull String description, AndroidVersion version, boolean isDefaultPlatform) {
    super(store, name, size, description);
    myVersion = version;
    myIsDefaultPlatform = isDefaultPlatform;
  }

  private static Platform lollipop(@NotNull ScopedStateStore store) {
    final String NAME = "Lollipop - Android 5.0 (API 21)";
    final String DESCRIPTION = "Android platform libraries for targeting Android 5.0.1 (Lollipop) platform";
    return new Platform(store, NAME, SIZE, DESCRIPTION, InstallComponentsPath.LATEST_ANDROID_VERSION, true);
  }

  private static Platform kitkat(@NotNull ScopedStateStore store) {
    final String NAME = "KitKat - Android 4.4 (API 19)";
    final String DESCRIPTION = "Android platform libraries for targeting Android 4.4.2 (KitKat) platform";
    return new Platform(store, NAME, SIZE, DESCRIPTION, new AndroidVersion(19, null), false);
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

  public static ComponentTreeNode createSubtree(@NotNull ScopedStateStore store) {
    return new ComponentCategory("Android SDK Platform", "SDK components for creating applications for different Android platforms",
                                 kitkat(store), lollipop(store));
  }

  @NotNull
  @Override
  public Collection<IPkgDesc> getRequiredSdkPackages(@Nullable Multimap<PkgType, RemotePkgInfo> remotePackages) {
    MajorRevision unspecifiedRevision = new MajorRevision(FullRevision.NOT_SPECIFIED);
    PkgDesc.Builder platform = PkgDesc.Builder.newPlatform(myVersion, unspecifiedRevision, FullRevision.NOT_SPECIFIED);
    PkgDesc.Builder platformSources = PkgDesc.Builder.newSource(myVersion, unspecifiedRevision);
    return ImmutableList.of(platform.create(), platformSources.create());
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
      com.android.sdklib.internal.repository.packages.Package packageInfo = info.getPackage();
      if (packageInfo == null) {
        continue;
      }
      IPkgDesc desc = packageInfo.getPkgDesc();
      // No unchecking if the platform is already installed. We can update but not remove existing platforms
      AndroidVersion androidVersion = desc.getAndroidVersion();
      int apiLevel = androidVersion == null ? 0 : androidVersion.getApiLevel();
      if (myVersion.getApiLevel() == apiLevel) {
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
