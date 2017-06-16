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

import com.android.repository.api.*;
import com.android.repository.impl.installer.BasicInstallerFactory;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.progress.ThrottledProgressWrapper;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Installs SDK components.
 */
public final class ComponentInstaller {
  private final AndroidSdkHandler mySdkHandler;

  public ComponentInstaller(@NotNull AndroidSdkHandler sdkHandler) {
    mySdkHandler = sdkHandler;
  }

  public List<RemotePackage> getPackagesToInstall(@NotNull Iterable<? extends InstallableComponent> components)
    throws SdkQuickfixUtils.PackageResolutionException {
    // TODO: Prompt about connection in handoff case?
    Set<UpdatablePackage> requests = Sets.newHashSet();
    StudioLoggerProgressIndicator progress = new StudioLoggerProgressIndicator(getClass());
    RepoManager sdkManager = mySdkHandler.getSdkManager(progress);
    for (InstallableComponent component : components) {
      requests.addAll(component.getPackagesToInstall());
    }
    List<UpdatablePackage> resolved = Lists.newArrayList();
    resolved.addAll(SdkQuickfixUtils.resolve(requests, sdkManager.getPackages()));

    List<RemotePackage> result = Lists.newArrayList();
    for (UpdatablePackage p : resolved) {
      result.add(p.getRemote());
    }
    return result;
  }

  public void installPackages(@NotNull List<RemotePackage> packages, @NotNull Downloader downloader, @NotNull ProgressIndicator progress)
    throws WizardException {
    progress = new ThrottledProgressWrapper(progress);
    RepoManager sdkManager = mySdkHandler.getSdkManager(progress);
    double progressMax = 0;
    double progressIncrement = 0.9 / (packages.size() * 2.);
    for (RemotePackage request : packages) {
      // Intentionally don't register any listeners on the installer, so we don't recurse on haxm
      // TODO: This is a hack. Any future rewrite of this shouldn't require this behavior.
      InstallerFactory factory = new BasicInstallerFactory();
      Installer installer = factory.createInstaller(request, sdkManager, downloader, mySdkHandler.getFileOp());
      progressMax += progressIncrement;
      if (installer.prepare(progress.createSubProgress(progressMax))) {
        installer.complete(progress.createSubProgress(progressMax + progressIncrement));
      }
      progressMax += progressIncrement;
      progress.setFraction(progressMax);
    }
    sdkManager.loadSynchronously(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, progress.createSubProgress(1), null, null);
  }

  public void ensureSdkPackagesUninstalled(@NotNull Collection<String> packageNames, @NotNull ProgressIndicator progress)
    throws WizardException {
    RepoManager sdkManager = mySdkHandler.getSdkManager(progress);
    RepositoryPackages packages = sdkManager.getPackages();
    Map<String, LocalPackage> localPackages = packages.getLocalPackages();
    List<LocalPackage> packagesToUninstall = new ArrayList<>();

    for (String packageName : packageNames) {
      LocalPackage p = localPackages.get(packageName);
      if (p != null) {
        packagesToUninstall.add(p);
      }
      else {
        progress.logInfo(String.format("Package '%1$s' does not appear to be installed - ignoring", packageName));
      }
    }
    double progressMax = 0;
    double progressIncrement = 0.9 / (packagesToUninstall.size() * 2.);
    for (LocalPackage request : packagesToUninstall) {
      // This is pretty much symmetric to the installPackages() method above, so the same comments apply.
      // Should we have registered listeners, HaxmInstallListener would have invoked another instance of HaxmWizard.
      // The good news is that as of writing this,
      // this class is used in Welcome and Haxm wizards only, and plays the role of a utility class.
      // If we have more packages which require custom pre- and post-installation steps like Haxm,
      // then we might still need a way to invoke non-recursive / listener-free uninstall operations for cleanup purposes
      // It's possible that a change in packaging API would make sense to support that later -
      // there is already some cleanup() support in operation chain implementation, but its limitation is that cleanup()
      // is executed unconditionally, whereas in most cases it should be dependent on the next operation success status -
      // like stack unwinding after an exception.
      InstallerFactory factory = new BasicInstallerFactory();
      Uninstaller uninstaller = factory.createUninstaller(request, sdkManager, mySdkHandler.getFileOp());
      progressMax += progressIncrement;
      if (uninstaller.prepare(progress.createSubProgress(progressMax))) {
        uninstaller.complete(progress.createSubProgress(progressMax + progressIncrement));
      }
      progressMax += progressIncrement;
      progress.setFraction(progressMax);
    }
    sdkManager.loadSynchronously(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, progress.createSubProgress(1), null, null);
    progress.setFraction(1);
  }
}
