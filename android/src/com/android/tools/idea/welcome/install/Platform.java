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
package com.android.tools.idea.welcome.install;

import com.android.SdkConstants;
import com.android.repository.Revision;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RemotePackage;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.welcome.wizard.InstallComponentsPath;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * <p>Install Android SDK components for developing apps targeting Lollipop
 * platform.</p>
 * <p>Default selection logic:
 * <ol>
 * <li>If the component of this kind are already installed, they cannot be
 * unchecked (e.g. the wizard will not uninstall them)</li>
 * <li>If SDK does not have any platforms installed (or this is a new
 * SDK installation), then only the latest platform will be installed.</li>
 * </ol></p>
 */
public class Platform extends InstallableComponent {
  private final AndroidVersion myVersion;
  private final boolean myIsDefaultPlatform;

  public Platform(@NotNull ScopedStateStore store,
                  @NotNull String name,
                  @NotNull String description,
                  AndroidVersion version,
                  boolean isDefaultPlatform,
                  boolean installUpdates) {
    super(store, name, description, installUpdates, FileOpUtils.create());
    myVersion = version;
    myIsDefaultPlatform = isDefaultPlatform;
  }

  @Nullable
  private static Platform getLatestPlatform(@NotNull ScopedStateStore store,
                                            @Nullable Map<String, RemotePackage> remotePackages,
                                            boolean installUpdates) {
    RemotePackage latest = InstallComponentsPath.findLatestPlatform(remotePackages);
    if (latest != null) {
      AndroidVersion version = DetailsTypes.getAndroidVersion(((DetailsTypes.PlatformDetailsType)latest.getTypeDetails()));
      String versionName = SdkVersionInfo.getAndroidName(version.getFeatureLevel());
      final String description = "Android platform libraries for targeting " + versionName + " platform";
      return new Platform(store, versionName, description, version, !version.isPreview(), installUpdates);
    }
    return null;
  }

  @NotNull
  private static List<AndroidVersion> getInstalledPlatformVersions(@Nullable AndroidSdkHandler handler) {
    List<AndroidVersion> result = Lists.newArrayList();
    if (handler != null) {
      RepositoryPackages packages = handler.getSdkManager(new StudioLoggerProgressIndicator(Platform.class)).getPackages();
      for (LocalPackage p : packages.getLocalPackages().values()) {
        if (p.getTypeDetails() instanceof DetailsTypes.PlatformDetailsType) {
          result.add(DetailsTypes.getAndroidVersion((DetailsTypes.PlatformDetailsType)p.getTypeDetails()));
        }
      }
    }
    return result;
  }

  @Nullable
  public static ComponentTreeNode createSubtree(@NotNull ScopedStateStore store, @Nullable Map<String, RemotePackage> remotePackages,
                                                boolean installUpdates) {
    // Previously we also installed a preview platform, but no longer (see http://b.android.com/175343 for more).
    ComponentTreeNode latestPlatform = getLatestPlatform(store, remotePackages, installUpdates);
    if (latestPlatform != null) {
      return new ComponentCategory("Android SDK Platform", "SDK components for creating applications for different Android platforms",
                                   latestPlatform);
    }
    return null;
  }

  @NotNull
  @Override
  protected Collection<String> getRequiredSdkPackages() {
    List<String> requests = Lists.newArrayList(DetailsTypes.getPlatformPath(myVersion), DetailsTypes.getSourcesPath(myVersion));
    String buildTool = findLatestCompatibleBuildTool();
    if (buildTool != null) {
      requests.add(buildTool);
    }
    return requests;
  }

  private String findLatestCompatibleBuildTool() {
    Revision revision = null;
    String path = null;
    for (RemotePackage remote : getRepositoryPackages().getRemotePackages().values()) {
      if (!remote.getPath().startsWith(SdkConstants.FD_BUILD_TOOLS)) {
        continue;
      }
      Revision testRevision = remote.getVersion();
      if (testRevision.getMajor() == myVersion.getApiLevel() && (revision == null || testRevision.compareTo(revision) > 0)) {
        revision = testRevision;
        path = remote.getPath();
      }
    }
    return path;
  }

  @Override
  public void configure(@NotNull InstallContext installContext, @NotNull AndroidSdkHandler sdkHandler) {
  }

  @Override
  public boolean isOptionalForSdkLocation() {
    List<AndroidVersion> locals = getInstalledPlatformVersions(mySdkHandler);
    if (locals.isEmpty()) {
      return !myIsDefaultPlatform;
    }
    for (AndroidVersion androidVersion : locals) {
      // No unchecking if the platform is already installed. We can update but not remove existing platforms
      int apiLevel = androidVersion == null ? 0 : androidVersion.getApiLevel();
      if (myVersion.getFeatureLevel() == apiLevel) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean isSelectedByDefault() {
    return false;
  }
}
