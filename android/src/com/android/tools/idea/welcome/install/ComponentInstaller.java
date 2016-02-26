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

import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.UpdatablePackage;
import com.android.repository.impl.installer.PackageInstaller;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.sdkv2.StudioDownloader;
import com.android.tools.idea.sdkv2.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdkv2.StudioSettingsController;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * Installs SDK components.
 */
public final class ComponentInstaller {
  private final boolean myInstallUpdates;
  private final AndroidSdkHandler mySdkHandler;

  public ComponentInstaller(boolean installUpdates,
                            @NotNull AndroidSdkHandler sdkHandler) {
    myInstallUpdates = installUpdates;
    mySdkHandler = sdkHandler;
  }

  public List<RemotePackage> getPackagesToInstall(@NotNull Iterable<? extends InstallableComponent> components) {
    // TODO: Prompt about connection in handoff case?
    Set<String> requests = Sets.newHashSet();
    StudioLoggerProgressIndicator progress = new StudioLoggerProgressIndicator(getClass());
    RepoManager sdkManager = mySdkHandler.getSdkManager(progress);
    for (InstallableComponent component : components) {
      requests.addAll(component.getPackagesToInstall());
    }
    List<UpdatablePackage> resolved = Lists.newArrayList();
    List<String> problems = Lists.newArrayList();
    SdkQuickfixUtils.resolve(requests, null, sdkManager, resolved, problems);
    List<RemotePackage> result = Lists.newArrayList();
    for (UpdatablePackage p : resolved) {
      result.add(p.getRemote());
    }
    return result;
  }

  public void installPackages(@NotNull List<RemotePackage> packages, ProgressIndicator progress) throws WizardException {
    RepoManager sdkManager = mySdkHandler.getSdkManager(progress);
    for (RemotePackage request : packages) {
      PackageInstaller bestInstaller = AndroidSdkHandler.findBestInstaller(request);
      FileOp fop = FileOpUtils.create();
      if (bestInstaller.prepareInstall(request, new StudioDownloader(), StudioSettingsController.getInstance(),
                                       progress, sdkManager, fop)) {
        bestInstaller.completeInstall(request, progress, sdkManager, fop);
      }
    }
    sdkManager.loadSynchronously(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, progress, null, null);
  }
}
