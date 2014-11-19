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
import com.android.sdklib.internal.repository.sources.SdkSources;
import com.android.sdklib.internal.repository.updater.SdkUpdaterNoWindow;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.sdklib.repository.local.LocalSdk;
import com.android.sdklib.repository.local.Update;
import com.android.sdklib.repository.local.UpdateResult;
import com.android.sdklib.repository.remote.RemotePkgInfo;
import com.android.sdklib.repository.remote.RemoteSdk;
import com.android.tools.idea.avdmanager.LogWrapper;
import com.android.tools.idea.sdk.SdkLoggerIntegration;
import com.android.utils.ILogger;
import com.android.utils.NullLogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.*;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * Installs SDK components.
 */
public final class InstallComponentsOperation implements ThrowableComputable<Void, WizardException> {
  private final InstallContext myContext;
  private final File mySdkLocation;
  private final Collection<? extends InstallableComponent> myComponents;

  public InstallComponentsOperation(@NotNull InstallContext context,
                                    @NotNull File sdkLocation,
                                    @NotNull Collection<? extends InstallableComponent> components) {
    myContext = context;
    mySdkLocation = sdkLocation;
    myComponents = components;
  }

  private static Set<String> getPackageIds(Collection<LocalPkgInfo> pkgs) {
    Set<String> toUpdate = Sets.newHashSet();
    for (LocalPkgInfo localPkgInfo : pkgs) {
      toUpdate.add(localPkgInfo.getDesc().getInstallId());
    }
    return toUpdate;
  }

  @Nullable
  private static Multimap<PkgType, RemotePkgInfo> loadRemotePackageRevisions(@NotNull SdkManager manager) {
    AndroidSdkData sdkData = AndroidSdkData.getSdkData(manager.getLocation());
    if (sdkData != null) {
      RemoteSdk remoteSdk = sdkData.getRemoteSdk();
      SdkSources sdkSources = remoteSdk.fetchSources(RemoteSdk.DEFAULT_EXPIRATION_PERIOD_MS, new NullLogger());
      return remoteSdk.fetch(sdkSources, new NullLogger());
    }
    else {
      return null;
    }
  }

  private static List<LocalPkgInfo> getInstalledPackages(SdkManager manager, Set<String> toInstall) {
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

  private static Collection<LocalPkgInfo> getOldPackages(SdkManager manager, Collection<LocalPkgInfo> installed) {
    Multimap<PkgType, RemotePkgInfo> remotePackages = loadRemotePackageRevisions(manager);
    if (remotePackages != null) {
      LocalPkgInfo[] packagesArray = ArrayUtil.toObjectArray(installed, LocalPkgInfo.class);
      UpdateResult result = Update.computeUpdates(packagesArray, remotePackages);
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
      for (PkgDesc.Builder pkg : component.getRequiredSdkPackages()) {
        if (pkg != null) {
          packages.add(pkg.create().getInstallId());
        }
      }
    }
    return packages;
  }

  @VisibleForTesting
  ArrayList<String> getPackagesToDownload(@NotNull SdkManager manager) {
    Set<String> toInstall = getRequiredPackages();
    List<LocalPkgInfo> installed = getInstalledPackages(manager, toInstall);
    if (!installed.isEmpty()) {
      toInstall.removeAll(getPackageIds(installed));
      toInstall.addAll(getPackageIds(getOldPackages(manager, installed)));
    }
    return Lists.newArrayList(toInstall);
  }

  @Override
  public Void compute() throws WizardException {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

    SdkManager manager = SdkManager.createManager(mySdkLocation.getAbsolutePath(), new LogWrapper(Logger.getInstance(getClass())));
    if (manager != null) {
      indicator.setText("Checking for updated SDK components");
      ArrayList<String> packages = getPackagesToDownload(manager);
      if (!packages.isEmpty()) {
        SdkUpdaterNoWindow updater =
          new SdkUpdaterNoWindow(manager.getLocation(), manager, createLogger(indicator, packages.size()), false, true, null, null);
        updater.updateAll(packages, true, false, null);
      }
      else {
        myContext.print("Android SDK is up to date", ConsoleViewContentType.SYSTEM_OUTPUT);
        indicator.setFraction(1.0); // 100%
      }
      return null;
    }
    else {
      throw new WizardException("Corrupt SDK installation");
    }
  }

  private ILogger createLogger(@NotNull ProgressIndicator indicator, int itemCount) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return new LoggerForTest();
    }
    else {
      return new SdkManagerProgressIndicatorIntegration(indicator, myContext, itemCount);
    }
  }

  /**
   * Logger implementation to use during tests
   */
  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static class LoggerForTest extends SdkLoggerIntegration {
    private String myTitle = null;

    @Override
    protected void setProgress(int progress) {
      // No need.
    }

    @Override
    protected void setDescription(String description) {
      // No spamming
    }

    @Override
    protected void setTitle(String title) {
      if (!StringUtil.isEmptyOrSpaces(title) && !Objects.equal(title, myTitle)) {
        System.out.println(title);
        myTitle = title;
      }
    }

    @Override
    protected void lineAdded(String string) {
      System.out.println(string);
    }
  }
}
