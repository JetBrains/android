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

import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.sdklib.repository.local.LocalSdk;
import com.android.tools.idea.sdk.remote.RemotePkgInfo;
import com.android.tools.idea.sdk.remote.UpdatablePkgInfo;
import com.android.tools.idea.sdk.SdkPackages;
import com.android.tools.idea.sdk.remote.internal.updater.SdkUpdaterNoWindow;
import com.android.utils.ILogger;
import com.android.utils.NullLogger;
import com.google.common.base.Function;
import com.google.common.collect.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Installs SDK components.
 */
public final class ComponentInstaller {
  @Nullable private final Multimap<PkgType, RemotePkgInfo> myRemotePackages;
  private final boolean myInstallUpdates;

  public ComponentInstaller(@Nullable Multimap<PkgType, RemotePkgInfo> remotePackages, boolean installUpdates) {
    myRemotePackages = remotePackages;
    myInstallUpdates = installUpdates;
  }

  private static Set<String> getPackageIds(Iterable<LocalPkgInfo> localPackages) {
    Set<String> toUpdate = Sets.newHashSet();
    for (LocalPkgInfo localPkgInfo : localPackages) {
      toUpdate.add(localPkgInfo.getDesc().getBaseInstallId());
    }
    return toUpdate;
  }

  private static List<LocalPkgInfo> getInstalledPackages(@NotNull SdkManager manager, @NotNull Set<String> toInstall) {
    LocalSdk localSdk = manager.getLocalSdk();
    LocalPkgInfo[] installed = localSdk.getPkgsInfos(EnumSet.allOf(PkgType.class));
    List<LocalPkgInfo> toCheckForUpdate = Lists.newArrayListWithCapacity(installed.length);
    for (LocalPkgInfo info : installed) {
      if (toInstall.contains(info.getDesc().getBaseInstallId())) {
        toCheckForUpdate.add(info);
      }
    }
    return toCheckForUpdate;
  }

  private Iterable<LocalPkgInfo> getOldPackages(Collection<LocalPkgInfo> installed) {
    if (myRemotePackages != null) {
      LocalPkgInfo[] packagesArray = ArrayUtil.toObjectArray(installed, LocalPkgInfo.class);
      SdkPackages packages = new SdkPackages(packagesArray, myRemotePackages);
      List<LocalPkgInfo> result = Lists.newArrayList();
      for (UpdatablePkgInfo update : packages.getUpdatedPkgs()) {
        if (update.hasRemote(false)) {
          result.add(update.getLocalInfo());
        }
      }
      return result;
    }
    else {
      return installed; // We should try reinstalling all...
    }
  }

  private Set<String> getRequiredPackages(@NotNull Iterable<? extends InstallableComponent> components) {
    // TODO: Prompt about connection in handoff case?
    Set<String> packages = Sets.newHashSet();
    for (InstallableComponent component : components) {
      for (IPkgDesc pkg : component.getRequiredSdkPackages(myRemotePackages)) {
        if (pkg != null) {
          packages.add(pkg.getInstallId());
        }
      }
    }
    return packages;
  }

  /**
   * Returns a list of package install IDs for packages that are missing or outdated.
   *
   * @param manager SDK manager instance or <code>null</code> if this is a new install.
   * @param defaultUpdateAvailable If true, and if remote package information is not available, assume each package may have an update and
   *                               try to reinstall. If false and remote package information not available, assume no updates are available.
   */
  public ArrayList<String> getPackagesToInstall(@Nullable SdkManager manager, @NotNull Iterable<? extends InstallableComponent> components,
                                                boolean defaultUpdateAvailable) {
    Set<String> toInstall = getRequiredPackages(components);
    if (manager == null) {
      return Lists.newArrayList(toInstall);
    }
    else {
      List<LocalPkgInfo> installed = getInstalledPackages(manager, toInstall);
      if (!installed.isEmpty()) {
        toInstall.removeAll(getPackageIds(installed));
        if (myInstallUpdates && (myRemotePackages != null || defaultUpdateAvailable)) {
          toInstall.addAll(getPackageIds(getOldPackages(installed)));
        }
      }
      return Lists.newArrayList(toInstall);
    }
  }

  /**
   * Returns a collection of package info objects for packages that will be installed.
   */
  public Collection<RemotePkgInfo> getPackagesToInstallInfos(@Nullable String sdkPath,
                                                             @NotNull Iterable<? extends InstallableComponent> components) {
    if (!StringUtil.isEmptyOrSpaces(sdkPath) && myRemotePackages != null) {
      SdkManager sdkManager = SdkManager.createManager(sdkPath, new NullLogger());
      if (sdkManager != null) {
        Set<String> packagesToInstall = ImmutableSet.copyOf(getPackagesToInstall(sdkManager, components, true));
        Set<RemotePkgInfo> remotePackages = Sets.newHashSetWithExpectedSize(packagesToInstall.size());
        for (RemotePkgInfo remotePkgInfo : myRemotePackages.values()) {
          if (packagesToInstall.contains(remotePkgInfo.getPkgDesc().getInstallId())) {
            remotePackages.add(remotePkgInfo);
          }
        }
        return remotePackages;
      }
    }
    return ImmutableSet.of();
  }

  public void installPackages(@NotNull SdkManager manager, @NotNull ArrayList<String> packages, ILogger logger) throws WizardException {
    SdkUpdaterNoWindow updater = new SdkUpdaterNoWindow(manager.getLocation(), manager, logger, false, null, null);
    updater.updateAll(packages, true, false, null, false);
  }
}
