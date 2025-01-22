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

import com.android.SdkConstants;
import com.android.repository.Revision;
import com.android.repository.api.Downloader;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.SettingsController;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.AndroidVersionUtils;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.progress.RepoProgressIndicatorAdapter;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.StudioDownloader;
import com.android.tools.idea.sdk.StudioSettingsController;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.intellij.ide.externalComponents.ExternalComponentSource;
import com.intellij.ide.externalComponents.UpdatableExternalComponent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.Pair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An {@link ExternalComponentSource} that retrieves information from the {@link RepoManager} provided
 * by the Android SDK.
 */
public class SdkComponentSource implements ExternalComponentSource {

  public static String NAME = "Android SDK";

  public static final String PREVIEW_CHANNEL = "Preview Channel";
  public static final String STABLE_CHANNEL = "Stable Channel";

  private RepositoryPackages myPackages;

  private static final StudioLoggerProgressIndicator LOGGER = new StudioLoggerProgressIndicator(SdkComponentSource.class);

  private void initIfNecessary(@Nullable ProgressIndicator indicator) {
    RepoManager mgr = getRepoManager();
    com.android.repository.api.ProgressIndicator progress;
    if (indicator != null) {
      progress = new RepoProgressIndicatorAdapter(indicator);
    }
    else {
      progress = LOGGER;
    }
    if (mgr.loadSynchronously(TimeUnit.MINUTES.toMillis(1), progress, getDownloader(), getSettingsController())) {
      myPackages = mgr.getPackages();
    }
  }

  @VisibleForTesting
  @NotNull
  SettingsController getSettingsController() {
    return StudioSettingsController.getInstance();
  }

  @VisibleForTesting
  @NotNull
  Downloader getDownloader() {
    return new StudioDownloader();
  }

  @VisibleForTesting
  @NotNull
  RepoManager getRepoManager() {
    return AndroidSdks.getInstance().tryToChooseSdkHandler().getSdkManager(LOGGER);
  }

  /**
   * Install the given new versions of components.
   *
   * @param request The components to install.
   */
  @Override
  public void installUpdates(@NotNull Collection<UpdatableExternalComponent> request) {
    final List<RemotePackage> packages = new ArrayList<>();
    for (UpdatableExternalComponent p : request) {
      packages.add((RemotePackage)p.getKey());
    }
    new UpdateInfoDialog(true, packages).show();
  }

  /**
   * Retrieves information on updates available from the remote sdk.
   *
   * @param indicator      A {@code ProgressIndicator} that can be updated to show progress, or can be used to cancel the process.
   * @param updateSettings The UpdateSettings to use.
   * @return A collection of {@link UpdatablePackage}s corresponding to the currently installed Packages.
   */
  @NotNull
  @Override
  public Collection<UpdatableExternalComponent> getAvailableVersions(@Nullable ProgressIndicator indicator,
                                                                     @Nullable UpdateSettings updateSettings) {
    if (!IdeInfo.getInstance().isAndroidStudio()) {
      List<Sdk> sdks = AndroidSdks.getInstance().getAllAndroidSdks();
      if (sdks.isEmpty()) { // do not automatically check updates of SDK on IDEA startup, this is harmful for startup performance
        return Collections.emptyList();
      }
    }

    return getComponents(indicator, updateSettings, true);
  }

  /**
   * Retrieves information on components installed locally.
   *
   * @return A collection of {@link UpdatablePackage}s corresponding to the currently installed Packages.
   */
  @NotNull
  @Override
  public Collection<UpdatableExternalComponent> getCurrentVersions() {
    return getComponents(null, null, false);
  }

  @NotNull
  private Collection<UpdatableExternalComponent> getComponents(@Nullable ProgressIndicator indicator,
                                                               @Nullable UpdateSettings settings,
                                                               boolean remote) {
    List<UpdatableExternalComponent> result = new ArrayList<>();
    initIfNecessary(indicator);

    Set<String> ignored = settings != null ? Sets.newHashSet(settings.getIgnoredBuildNumbers()) : ImmutableSet.<String>of();

    for (com.android.repository.api.UpdatablePackage p : myPackages.getConsolidatedPkgs().values()) {
      if (remote) {
        if (p.hasRemote()) {
          RemotePackage remotePackage = p.getRemote();
          if (!ignored.contains(getPackageRevisionId(remotePackage))) {
            result.add(new UpdatablePackage(remotePackage));
          }
        }
      }
      else {
        if (p.hasLocal()) {
          result.add(new UpdatablePackage(p.getLocal()));
        }
      }
    }
    return result;
  }

  @NotNull
  static String getPackageRevisionId(@NotNull RepoPackage p) {
    return String.format("%1$s#%2$s", p.getPath(), p.getVersion().toString());
  }

  @NotNull
  @Override
  public String getName() {
    return NAME;
  }

  @NotNull
  @Override
  public Collection<? extends Pair<String, String>> getStatuses() {
    // This method is not used for anything.
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public List<String> getAllChannels() {
    return ImmutableList.of(STABLE_CHANNEL, PREVIEW_CHANNEL);
  }

  @Override
  public boolean isEnabled() {
    return AndroidSdkUtils.isAndroidSdkManagerEnabled();
  }
}
