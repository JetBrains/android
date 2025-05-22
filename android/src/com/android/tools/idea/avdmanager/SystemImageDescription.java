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
package com.android.tools.idea.avdmanager;

import com.android.repository.Revision;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoPackage;
import com.android.repository.impl.meta.TypeDetails;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.AndroidVersionUtils;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.RemoteSystemImage;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.SystemImageTags;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.targets.PlatformTarget;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import java.nio.file.Path;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Information on a system image. Used internally by the avd manager.
 */
public final class SystemImageDescription {
  private final @NotNull ISystemImage systemImage;
  private final @Nullable RemotePackage remotePackage;

  private SystemImageDescription(@NotNull ISystemImage systemImage, @Nullable RemotePackage remotePackage) {
    Preconditions.checkArgument(remotePackage == null || hasSystemImage(remotePackage));
    this.systemImage = systemImage;
    this.remotePackage = remotePackage;
  }

  public SystemImageDescription(@NotNull ISystemImage systemImage) {
    this(systemImage, null);
  }

  public SystemImageDescription(@NotNull RemotePackage remotePackage) {
    this(new RemoteSystemImage(remotePackage), remotePackage);
  }

  public static boolean hasSystemImage(RepoPackage p) {
    TypeDetails details = p.getTypeDetails();
    if (!(details instanceof DetailsTypes.ApiDetailsType)) {
      return false;
    }
    int apiLevel = ((DetailsTypes.ApiDetailsType)details).getApiLevel();
    if (details instanceof DetailsTypes.SysImgDetailsType) {
      return true;
    }
    // Platforms up to 13 included a bundled system image
    if (details instanceof DetailsTypes.PlatformDetailsType && apiLevel <= 13) {
      return true;
    }
    // Google APIs addons up to 19 included a bundled system image
    if (details instanceof DetailsTypes.AddonDetailsType && ((DetailsTypes.AddonDetailsType)details).getVendor().getId().equals("google") &&
        SystemImageTags.TAGS_WITH_GOOGLE_API.contains(((DetailsTypes.AddonDetailsType)details).getTag()) && apiLevel <= 19) {
      return true;
    }

    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(systemImage, remotePackage);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof SystemImageDescription other)) {
      return false;
    }
    return Objects.equal(systemImage, other.systemImage) &&
           Objects.equal(remotePackage, other.remotePackage);
  }

  /**
   * Checks if this corresponds to the downloaded system image of obj.
   */
  public boolean downloadedFrom(@NotNull SystemImageDescription other) {
    if (other.getRemotePackage() == null || remotePackage != null) {
      return false;
    }
    return Objects.equal(this.getName(), other.getName()) &&
           Objects.equal(this.getVendor(), other.getVendor()) &&
           Objects.equal(this.getAbiTypes(), other.getAbiTypes()) &&
           Objects.equal(this.getTranslatedAbiTypes(), other.getTranslatedAbiTypes()) &&
           Objects.equal(this.getTags(), other.getTags());
  }

  public @NotNull AndroidVersion getVersion() {
    return systemImage.getAndroidVersion();
  }

  public @Nullable RepoPackage getRemotePackage() {
    return remotePackage;
  }

  public boolean isRemote() {
    return remotePackage != null;
  }

  public boolean obsolete() {
    return systemImage.obsolete();
  }

  public @NotNull String getPrimaryAbiType() {
    return systemImage.getPrimaryAbiType();
  }

  public @NotNull List<@NotNull String> getAbiTypes() {
    return systemImage.getAbiTypes();
  }

  public @NotNull List<@NotNull String> getTranslatedAbiTypes() {
    return systemImage.getTranslatedAbiTypes();
  }

  public @NotNull List<@NotNull IdDisplay> getTags() {
    return systemImage.getTags();
  }

  public boolean hasGoogleApis() {
    return systemImage.hasGoogleApis();
  }

  public boolean isWearImage() {
    return SystemImageTags.isWearImage(getTags());
  }

  public boolean isTvImage() {
    return SystemImageTags.isTvImage(getTags());
  }

  public @NotNull String getName() {
    String versionString = SdkVersionInfo.getVersionString(getVersion().getFeatureLevel());
    return String.format("Android %s", versionString == null ? "API " + AndroidVersionUtils.getDisplayApiString(getVersion()): versionString);
  }

  public @NotNull String getVendor() {
    if (systemImage.getAddonVendor() != null) {
      return systemImage.getAddonVendor().getDisplay();
    }
    return PlatformTarget.PLATFORM_VENDOR;
  }

  public @NotNull String getVersionName() {
    return SdkVersionInfo.getVersionString(systemImage.getAndroidVersion().getApiLevel());
  }

  public @NotNull Revision getRevision() {
    return systemImage.getRevision();
  }

  public @NotNull List<@NotNull Path> getSkins() {
    return systemImage.getSkins();
  }

  public @NotNull ISystemImage getSystemImage() {
    return systemImage;
  }
}
