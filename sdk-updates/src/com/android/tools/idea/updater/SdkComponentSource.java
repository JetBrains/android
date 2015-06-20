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

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.repository.PreciseRevision;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.sdklib.repository.local.LocalSdk;
import com.android.tools.idea.sdk.SdkLoadedCallback;
import com.android.tools.idea.sdk.SdkPackages;
import com.android.tools.idea.sdk.SdkState;
import com.android.tools.idea.sdk.remote.RemoteSdk;
import com.android.tools.idea.sdk.remote.UpdatablePkgInfo;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSources;
import com.android.tools.idea.sdk.wizard.SdkQuickfixWizard;
import com.android.utils.ILogger;
import com.android.utils.StdLogger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.ide.externalComponents.ExternalComponentSource;
import com.intellij.ide.externalComponents.UpdatableExternalComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.Pair;
import com.intellij.util.concurrency.FutureResult;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * An {@link ExternalComponentSource} that retrieves information from the {@link LocalSdk} and {@link RemoteSdk} provided
 * by the Android SDK.
 */
public class SdkComponentSource implements ExternalComponentSource {
  SdkSources mySources;
  SdkState mySdkState;
  public static String NAME = "Android SDK";
  private static final ILogger ILOG = new StdLogger(StdLogger.Level.ERROR);

  public static final String PREVIEW_CHANNEL = "Preview Channel";
  public static final String STABLE_CHANNEL = "Stable Channel";

  private boolean initIfNecessary() {
    if (mySdkState != null) {
      return true;
    }
    AndroidSdkData data = AndroidSdkUtils.tryToChooseAndroidSdk();
    if (data == null) {
      Logger.getInstance(getClass()).warn("Couldn't find existing SDK");
      return false;
    }
    mySdkState = SdkState.getInstance(data);

    mySources = mySdkState.getRemoteSdk().fetchSources(RemoteSdk.DEFAULT_EXPIRATION_PERIOD_MS, ILOG);
    return true;
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
    new UpdateInfoDialog(true, packages).show();
  }

  /**
   * Retrieves information on updates available from the {@link RemoteSdk}.
   *
   * @param indicator      A {@code ProgressIndicator} that can be updated to show progress, or can be used to cancel the process.
   * @param updateSettings The UpdateSettings to use.
   * @return A collection of {@link UpdatablePackage}s corresponding to the currently installed Packages.
   */
  @NotNull
  @Override
  public Collection<UpdatableExternalComponent> getAvailableVersions(@Nullable ProgressIndicator indicator,
                                                                     @Nullable UpdateSettings updateSettings) {
    return getComponents(indicator, updateSettings, true);
  }

  /**
   * Retrieves information on updates installed using the {@link LocalSdk}.
   *
   * @return A collection of {@link UpdatablePackage}s corresponding to the currently installed Packages.
   */
  @NotNull
  @Override
  public Collection<UpdatableExternalComponent> getCurrentVersions() {
    return getComponents(null, null, false);
  }

  private Collection<UpdatableExternalComponent> getComponents(ProgressIndicator indicator, UpdateSettings settings, boolean remote) {
    List<UpdatableExternalComponent> result = Lists.newArrayList();
    if (!initIfNecessary()) {
      return result;
    }

    Set<String> ignored = settings != null ? Sets.newHashSet(settings.getIgnoredBuildNumbers()) : ImmutableSet.<String>of();
    mySdkState.loadSynchronously(SdkState.DEFAULT_EXPIRATION_PERIOD_MS, true, null, null, null, false);
    boolean previewChannel = settings != null && PREVIEW_CHANNEL.equals(settings.getExternalUpdateChannels().get(getName()));
    for (UpdatablePkgInfo info : mySdkState.getPackages().getConsolidatedPkgs().values()) {
      if (remote) {
        if (info.hasRemote(previewChannel)) {
          IPkgDesc desc = info.getRemote(previewChannel).getPkgDesc();
          if (!ignored.contains(desc.getInstallId())) {
            result.add(new UpdatablePackage(desc));
          }
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
    return NAME;
  }

  @NotNull
  @Override
  public Collection<? extends Pair<String, String>> getStatuses() {
    if (!initIfNecessary()) {
      return ImmutableList.of();
    }
    final FutureResult<List<Pair<String, String>>> resultFuture = new FutureResult<List<Pair<String, String>>>();
    mySdkState.loadAsync(SdkState.DEFAULT_EXPIRATION_PERIOD_MS, false, new SdkLoadedCallback(false) {
      @Override
      public void doRun(@NotNull SdkPackages packages) {
        PreciseRevision toolsRevision = null;
        PreciseRevision platformRevision = null;
        AndroidVersion platformVersion = null;
        for (LocalPkgInfo info : packages.getLocalPkgInfos()) {
          if (info.getDesc().getType() == PkgType.PKG_TOOLS &&
              (toolsRevision == null || toolsRevision.compareTo(info.getDesc().getPreciseRevision()) < 0)) {
            toolsRevision = info.getDesc().getPreciseRevision();
          }
          if (info.getDesc().getType() == PkgType.PKG_PLATFORM &&
              (platformVersion == null || platformVersion.compareTo(info.getDesc().getAndroidVersion()) < 0)) {
            platformRevision = info.getDesc().getPreciseRevision();
            platformVersion = info.getDesc().getAndroidVersion();
          }
        }
        List<Pair<String, String>> result = Lists.newArrayList();
        if (toolsRevision != null) {
          result.add(Pair.create("Android SDK Tools:", toolsRevision.toString()));
        }
        if (platformVersion != null) {
          result.add(Pair.create("Android Platform Version:", String.format("%1$s revision %2$s",
                                                                            platformVersion.getCodename() != null ?
                                                                            platformVersion.getCodename() :
                                                                            SdkVersionInfo.getAndroidName(platformVersion.getApiLevel()),
                                                                            platformRevision)));
        }
        resultFuture.set(result);
      }
    }, null, null, false);
    try {
      return resultFuture.get();
    }
    catch (InterruptedException e) {
      return ImmutableList.of();
    }
    catch (ExecutionException e) {
      return ImmutableList.of();
    }
  }

  @Nullable
  @Override
  public List<String> getAllChannels() {
    return ImmutableList.of(STABLE_CHANNEL, PREVIEW_CHANNEL);
  }
}
