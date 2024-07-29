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

import static com.android.sdklib.SystemImageTags.ANDROID_TV_TAG;
import static com.android.sdklib.SystemImageTags.GOOGLE_TV_TAG;

import com.android.repository.Revision;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoPackage;
import com.android.repository.impl.meta.TypeDetails;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.RemoteSystemImage;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.SystemImageTags;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.targets.PlatformTarget;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Information on a system image. Used internally by the avd manager.
 */
public final class SystemImageDescription {
  private ISystemImage mySystemImage;
  private RemotePackage myRemotePackage;

  public static final Set<IdDisplay> TV_TAGS = ImmutableSet.of(ANDROID_TV_TAG, GOOGLE_TV_TAG);

  public SystemImageDescription(@NotNull ISystemImage systemImage) {
    mySystemImage = systemImage;
  }

  public SystemImageDescription(@NotNull RemotePackage remotePackage) {
    this.myRemotePackage = remotePackage;

    assert hasSystemImage(remotePackage);
    mySystemImage = new RemoteSystemImage(remotePackage);
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
    return Objects.hashCode(mySystemImage, myRemotePackage);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof SystemImageDescription)) {
      return false;
    }
    SystemImageDescription other = (SystemImageDescription)obj;
    return Objects.equal(mySystemImage, other.mySystemImage) &&
           Objects.equal(myRemotePackage, other.myRemotePackage);
  }

  /**
   * Checks if this corresponds to the downloaded system image of obj.
   */
  public boolean downloadedFrom(@NotNull SystemImageDescription other) {
    if (other.getRemotePackage() == null || myRemotePackage != null) {
      return false;
    }
    return Objects.equal(this.getName(), other.getName()) &&
           Objects.equal(this.getVendor(), other.getVendor()) &&
           Objects.equal(this.getAbiTypes(), other.getAbiTypes()) &&
           Objects.equal(this.getTranslatedAbiTypes(), other.getTranslatedAbiTypes()) &&
           Objects.equal(this.getTags(), other.getTags());
  }

  @NotNull
  public AndroidVersion getVersion() {
    return mySystemImage.getAndroidVersion();
  }

  @Nullable
  public RepoPackage getRemotePackage() {
    return myRemotePackage;
  }

  public boolean isRemote() {
    return myRemotePackage != null;
  }

  public boolean obsolete() {
    return mySystemImage.obsolete();
  }

  public String getPrimaryAbiType() {
    return mySystemImage.getPrimaryAbiType();
  }

  @NotNull
  public List<String> getAbiTypes() {
    return mySystemImage.getAbiTypes();
  }

  public List<String> getTranslatedAbiTypes() {
    return mySystemImage.getTranslatedAbiTypes();
  }

  @NotNull
  public List<IdDisplay> getTags() {
    return mySystemImage.getTags();
  }

  public boolean hasGoogleApis() {
    return mySystemImage.hasGoogleApis();
  }

  public boolean isWearImage() {
    return SystemImageTags.isWearImage(getTags());
  }

  public boolean isTvImage() {
    return SystemImageTags.isTvImage(getTags());
  }

  public String getName() {
    String versionString = SdkVersionInfo.getVersionString(getVersion().getFeatureLevel());
    return String.format("Android %s", versionString == null ? "API " + getVersion().getApiString() : versionString);
  }

  public String getVendor() {
    if (mySystemImage.getAddonVendor() != null) {
      return mySystemImage.getAddonVendor().getDisplay();
    }
    return PlatformTarget.PLATFORM_VENDOR;
  }

  public String getVersionName() {
    return SdkVersionInfo.getVersionString(mySystemImage.getAndroidVersion().getApiLevel());
  }

  @Nullable
  public Revision getRevision() {
    return mySystemImage.getRevision();
  }

  public List<Path> getSkins() {
    return mySystemImage.getSkins();
  }

  public ISystemImage getSystemImage() {
    return mySystemImage;
  }
}
