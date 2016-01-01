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
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.sdkv2.StudioDownloader;
import com.android.tools.idea.sdkv2.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdkv2.StudioSettingsController;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Installs SDK components.
 */
public final class ComponentInstaller {
  @Nullable private final Map<String, RemotePackage> myRemotePackages;
  private final boolean myInstallUpdates;
  private final AndroidSdkHandler mySdkHandler;

  public ComponentInstaller(@Nullable Map<String, RemotePackage> remotePackages,
                            boolean installUpdates,
                            @NotNull AndroidSdkHandler sdkHandler) {
    myRemotePackages = remotePackages;
    myInstallUpdates = installUpdates;
    mySdkHandler = sdkHandler;
  }

  public List<RemotePackage> getPackagesToInstall(@NotNull Iterable<? extends InstallableComponent> components) {
    // TODO: Prompt about connection in handoff case?
    Set<UpdatablePackage> requests = Sets.newHashSet();
    StudioLoggerProgressIndicator progress = new StudioLoggerProgressIndicator(getClass());
    RepoManager sdkManager = mySdkHandler.getSdkManager(progress);
    // Reload if needed (probably won't be).
    sdkManager.loadSynchronously(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, progress, new StudioDownloader(),
                                 StudioSettingsController.getInstance());
    RepositoryPackages allPackages = sdkManager.getPackages();
    for (InstallableComponent component : components) {
      for (String s : component.getRequiredSdkPackages(myRemotePackages)) {
        UpdatablePackage p = allPackages.getConsolidatedPkgs().get(s);
        if (p != null && (myInstallUpdates || !p.hasLocal())) {
          requests.add(p);
        }
      }
    }
    List<UpdatablePackage> resolved = Lists.newArrayList();
    List<String> problems = Lists.newArrayList();
    SdkQuickfixUtils.resolve(null, requests, sdkManager, resolved, problems);
    List<RemotePackage> result = Lists.newArrayList();
    for (UpdatablePackage p : resolved) {
      result.add(p.getRemote());
    }
    return result;
  }

  public void installPackages(@NotNull List<RemotePackage> packages, ProgressIndicator progress) throws WizardException {
    for (RemotePackage request : packages) {
      AndroidSdkHandler.findBestInstaller(request)
        .install(request, new StudioDownloader(), StudioSettingsController.getInstance(), progress,
                 mySdkHandler.getSdkManager(new StudioLoggerProgressIndicator(getClass())), FileOpUtils.create());
    }
  }
}
