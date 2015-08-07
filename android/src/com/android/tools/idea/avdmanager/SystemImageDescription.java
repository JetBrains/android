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

import com.android.sdklib.*;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.IPkgDescAddon;
import com.android.sdklib.repository.descriptors.IdDisplay;
import com.android.sdklib.repository.descriptors.PkgType;
import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Information on a system image. Used internally by the avd manager.
 */
public final class SystemImageDescription {
  private IAndroidTarget myTarget;
  private ISystemImage mySystemImage;
  private IPkgDesc myRemotePackage;

  public SystemImageDescription(IAndroidTarget target, ISystemImage systemImage) {
    this.myTarget = target;
    this.mySystemImage = systemImage;
  }

  public SystemImageDescription(IPkgDesc remotePackage, IAndroidTarget target) {
    this.myRemotePackage = remotePackage;
    this.myTarget = target;
  }

  static boolean hasSystemImage(IPkgDesc desc) {
    if (desc.getType().equals(PkgType.PKG_SYS_IMAGE) ||
        desc.getType().equals(PkgType.PKG_ADDON_SYS_IMAGE)) {
      return true;
    }
    // Platforms up to 13 included a bundled system image
    if (desc.getType().equals(PkgType.PKG_PLATFORM) && desc.getAndroidVersion().getApiLevel() <= 13) {
      return true;
    }
    // Google APIs addons up to 18 included a bundled system image
    if (desc.getType().equals(PkgType.PKG_ADDON) && desc.hasVendor() && desc.getVendor().getId().equals("google") &&
        ((IPkgDescAddon)desc).getName().getId().equals("google_apis") && desc.getAndroidVersion().getApiLevel() <= 18) {
      return true;
    }

    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myTarget, mySystemImage, myRemotePackage);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof SystemImageDescription)) {
      return false;
    }
    SystemImageDescription other = (SystemImageDescription)obj;
    return Objects.equal(myTarget, other.myTarget) && Objects.equal(mySystemImage, other.mySystemImage) &&
           Objects.equal(myRemotePackage, other.myRemotePackage);
  }

  @Nullable
  public AndroidVersion getVersion() {
    if (myTarget != null) {
      return myTarget.getVersion();
    }
    else if (myRemotePackage != null) {
      return myRemotePackage.getAndroidVersion();
    }
    return null;
  }

  public IPkgDesc getRemotePackage() {
    return myRemotePackage;
  }

  public boolean isRemote() {
    return myRemotePackage != null;
  }

  @NotNull
  public String getAbiType() {
    if (mySystemImage != null) {
      return mySystemImage.getAbiType();
    }
    PkgType type = myRemotePackage.getType();
    if (type == PkgType.PKG_SYS_IMAGE || type == PkgType.PKG_ADDON_SYS_IMAGE) {
      return myRemotePackage.getPath();
    }
    else if (type == PkgType.PKG_PLATFORM || type == PkgType.PKG_ADDON) {
      // Bundled images don't specify the abi, but in practice they're all arm.
      return "armeabi";
    }
    else {
      return "";
    }
  }

  @NotNull
  public IdDisplay getTag() {
    if (mySystemImage != null) {
      return mySystemImage.getTag();
    }
    // for normal system images, the tag will be e.g. google_apis. Bundled images don't have a tag; instead use the name.
    if (myRemotePackage.getType() == PkgType.PKG_ADDON) {
      return ((IPkgDescAddon)myRemotePackage).getName();
    }
    IdDisplay tag = myRemotePackage.getTag();
    if (tag != null) {
      return tag;
    }
    return SystemImage.DEFAULT_TAG;
  }

  public String getName() {
    if (getVersion() != null) {
      return String.format("Android %s", SdkVersionInfo.getVersionString(getVersion().getFeatureLevel()));
    }
    return "Unknown platform";
  }

  public String getVendor() {
    if (myTarget != null) {
      return myTarget.getVendor();
    }
    if (mySystemImage != null && mySystemImage.getAddonVendor() != null) {
      return mySystemImage.getAddonVendor().getDisplay();
    }
    if (myRemotePackage != null) {
      IdDisplay vendor = myRemotePackage.getVendor();
      if (vendor != null) {
        return vendor.getDisplay();
      }
    }
    return "";
  }

  public String getVersionName() {
    if (myTarget != null) {
      return myTarget.getVersionName();
    }
    return "";
  }

  public IAndroidTarget getTarget() {
    return myTarget;
  }

  public File[] getSkins() {
    if (myTarget != null) {
      return myTarget.getSkins();
    }
    return new File[0];
  }
}
