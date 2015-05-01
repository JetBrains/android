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
package com.android.tools.idea.updater;

import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.local.LocalSdk;
import com.android.tools.idea.sdk.SdkState;
import com.android.tools.idea.sdk.remote.RemoteSdk;
import com.android.tools.idea.sdk.remote.UpdatablePkgInfo;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSources;
import com.android.tools.idea.sdk.wizard.SdkQuickfixWizard;
import com.android.tools.idea.wizard.DialogWrapperHost;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import com.google.common.collect.Lists;
import com.intellij.ide.externalComponents.ExternalComponentSource;
import com.intellij.ide.externalComponents.UpdatableExternalComponent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * An {@link ExternalComponentSource} that retrieves information from the {@link LocalSdk} and {@link RemoteSdk} provided
 * by the Android SDK.
 */
public class SdkComponentSource implements ExternalComponentSource {
  SdkSources mySources;
  SdkState mySdkState;
  private static final ILogger ILOG = new StdLogger(StdLogger.Level.ERROR);

  private void initIfNecessary() {
    if (mySdkState != null) {
      return;
    }
    AndroidSdkData data = AndroidSdkUtils.tryToChooseAndroidSdk();
    assert data != null;
    mySdkState = SdkState.getInstance(data);

    mySources = mySdkState.getRemoteSdk().fetchSources(RemoteSdk.DEFAULT_EXPIRATION_PERIOD_MS, ILOG);
  }

  /**
   * Install the given new versions of components using the {@link SdkQuickfixWizard}.
   *
   * @param request The components to install.
   */
  @Override
  public void installUpdates(@NotNull Collection<UpdatableExternalComponent> request) {
    final List<IPkgDesc> packages = Lists.newArrayList();
    for (UpdatableExternalComponent p : request) {
      packages.add((IPkgDesc)p.getKey());
    }
    SdkQuickfixWizard sdkQuickfixWizard =
      new SdkQuickfixWizard(null, null, packages, new DialogWrapperHost(null, DialogWrapper.IdeModalityType.PROJECT));
    sdkQuickfixWizard.init();
    sdkQuickfixWizard.show();
  }

  /**
   * Retrieves information on updates available from the {@link RemoteSdk}.
   *
   * @param indicator A {@code ProgressIndicator} that can be updated to show progress, or can be used to cancel the process.
   * @return A collection of {@link UpdatablePackage}s corresponding to the currently installed Packages.
   */
  @NotNull
  @Override
  public Collection<UpdatableExternalComponent> getAvailableVersions(ProgressIndicator indicator) {
    return getComponents(indicator, true);
  }

  /**
   * Retrieves information on updates installed using the {@link LocalSdk}.
   *
   * @return A collection of {@link UpdatablePackage}s corresponding to the currently installed Packages.
   */
  @NotNull
  @Override
  public Collection<UpdatableExternalComponent> getCurrentVersions() {
    return getComponents(null, false);
  }

  private Collection<UpdatableExternalComponent> getComponents(ProgressIndicator indicator, boolean remote) {
    initIfNecessary();
    List<UpdatableExternalComponent> result = Lists.newArrayList();
    mySdkState.loadSynchronously(SdkState.DEFAULT_EXPIRATION_PERIOD_MS, true, null, null, null, false);
    for (UpdatablePkgInfo info : mySdkState.getPackages().getConsolidatedPkgs()) {
      if (remote) {
        if (info.hasRemote()) {
          result.add(new UpdatablePackage(info.getRemote().getPkgDesc()));
        }
      }
      else {
        if (info.hasLocal()) {
          result.add(new UpdatablePackage(info.getLocalInfo().getDesc()));
        }
      }
    }
    return result;
  }

  @NotNull
  @Override
  public String getName() {
    return "Android SDK";
  }
}
