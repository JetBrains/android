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
import com.android.tools.idea.avdmanager.LogWrapper;
import com.android.tools.idea.sdk.SdkLoggerIntegration;
import com.android.utils.ILogger;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * Installs SDK components.
 */
public final class InstallComponentsOperation {
  private final Collection<? extends InstallableComponent> myComponents;
  @Nullable private final Multimap<PkgType, RemotePkgInfo> myRemotePackages;

  public InstallComponentsOperation(@NotNull Collection<? extends InstallableComponent> components,
                                    @Nullable Multimap<PkgType, RemotePkgInfo> remotePackages) {
    myComponents = components;
    myRemotePackages = remotePackages;
  }

  private static Set<String> getPackageIds(Collection<LocalPkgInfo> packages) {
    Set<String> toUpdate = Sets.newHashSet();
    for (LocalPkgInfo localPkgInfo : packages) {
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
  public ArrayList<String> getPackagesToDownload(@Nullable SdkManager manager) {
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

  public void install(@NotNull InstallContext context, @NotNull File sdkLocation) throws WizardException {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

    SdkManager manager = SdkManager.createManager(sdkLocation.getAbsolutePath(), new LogWrapper(Logger.getInstance(getClass())));
    if (manager != null) {
      indicator.setText("Checking for updated SDK components");
      ArrayList<String> packages = getPackagesToDownload(manager);
      if (!packages.isEmpty()) {
        SdkUpdaterNoWindow updater =
          new SdkUpdaterNoWindow(manager.getLocation(), manager, createLogger(indicator, packages.size(), context),
                                 false, true, null, null);
        updater.updateAll(packages, true, false, null, true);
      }
      else {
        context.print("Android SDK is up to date.\n", ConsoleViewContentType.SYSTEM_OUTPUT);
        indicator.setFraction(1.0); // 100%
      }
    }
    else {
      throw new WizardException("Corrupt SDK installation");
    }
  }

  private static ILogger createLogger(@NotNull ProgressIndicator indicator, int itemCount, @NotNull InstallContext context) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return new LoggerForTest();
    }
    else {
      return new SdkManagerProgressIndicatorIntegration(indicator, context, itemCount);
    }
  }

  public void run(@NotNull final InstallContext installContext,
                  @NotNull final File sdkLocation, double progressRatio) throws WizardException {
    installContext.run(new ThrowableComputable<Void, WizardException>() {
      @Override
      public Void compute() throws WizardException {
        install(installContext, sdkLocation);
        return null;
      }
    }, progressRatio);
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
