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
package com.android.tools.idea.welcome;

import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.repository.updater.SdkUpdaterNoWindow;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.sdklib.repository.local.LocalSdk;
import com.android.sdklib.repository.local.Update;
import com.android.sdklib.repository.local.UpdateResult;
import com.android.sdklib.repository.remote.RemotePkgInfo;
import com.android.utils.ILogger;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Installs SDK components.
 */
public final class ComponentInstaller {
  private final Collection<? extends InstallableComponent> myComponents;
  @Nullable private final Multimap<PkgType, RemotePkgInfo> myRemotePackages;

  public ComponentInstaller(@NotNull Collection<? extends InstallableComponent> components,
                            @Nullable Multimap<PkgType, RemotePkgInfo> remotePackages) {
    myComponents = components;
    myRemotePackages = remotePackages;
  }

  private static Set<String> getPackageIds(Collection<LocalPkgInfo> pkgs) {
    Set<String> toUpdate = Sets.newHashSet();
    for (LocalPkgInfo localPkgInfo : pkgs) {
      toUpdate.add(localPkgInfo.getDesc().getInstallId());
    }
    return toUpdate;
  }

  private static List<LocalPkgInfo> getInstalledPackages(@NotNull SdkManager manager, @NotNull Set<String> toInstall) {
    LocalSdk localSdk = manager.getLocalSdk();
    LocalPkgInfo[] installed = localSdk.getPkgsInfos(EnumSet.allOf(PkgType.class));
    List<LocalPkgInfo> toCheckForUpdate = Lists.newArrayListWithCapacity(installed.length);
    for (LocalPkgInfo info : installed) {
      if (toInstall.contains(info.getDesc().getInstallId())) {
        toCheckForUpdate.add(info);
      }
    }
    return toCheckForUpdate;
  }

  private Collection<LocalPkgInfo> getOldPackages(Collection<LocalPkgInfo> installed) {
    if (myRemotePackages != null) {
      LocalPkgInfo[] packagesArray = ArrayUtil.toObjectArray(installed, LocalPkgInfo.class);
      UpdateResult result = Update.computeUpdates(packagesArray, myRemotePackages);
      return result.getUpdatedPkgs();
    }
    else {
      return installed; // We should try reinstalling all...
    }
  }

  private Set<String> getRequiredPackages() {
    // TODO: Prompt about connection in handoff case?
    Set<String> packages = Sets.newHashSet();
    for (InstallableComponent component : myComponents) {
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
   */
  public ArrayList<String> getPackagesToInstall(@Nullable SdkManager manager) {
    Set<String> toInstall = getRequiredPackages();
    if (manager == null) {
      return Lists.newArrayList(toInstall);
    }
    else {
      List<LocalPkgInfo> installed = getInstalledPackages(manager, toInstall);
      if (!installed.isEmpty()) {
        toInstall.removeAll(getPackageIds(installed));
        toInstall.addAll(getPackageIds(getOldPackages(installed)));
      }
      return Lists.newArrayList(toInstall);
    }
  }

  public void installPackages(@NotNull SdkManager manager, @NotNull ArrayList<String> packages, ILogger logger) throws WizardException {
    SdkUpdaterNoWindow updater = new SdkUpdaterNoWindow(manager.getLocation(), manager, logger, false, true, null, null);
    updater.updateAll(packages, true, false, null, false);
  }
}
