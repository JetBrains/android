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

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.IdDisplay;
import com.android.sdklib.repository.descriptors.PkgType;
import com.google.common.base.Objects;
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

  @Nullable
  public String getAbiType() {
    if (mySystemImage != null) {
      return mySystemImage.getAbiType();
    }
    else if (myRemotePackage.getType() == PkgType.PKG_SYS_IMAGE || myRemotePackage.getType() == PkgType.PKG_ADDON_SYS_IMAGE) {
      return myRemotePackage.getPath();
    }
    else {
      return "";
    }
  }

  @Nullable
  public IdDisplay getTag() {
    if (mySystemImage != null) {
      return mySystemImage.getTag();
    }
    return myRemotePackage.getTag();
  }

  public String getName() {
    if (myTarget != null) {
      return myTarget.getFullName();
    }
    if (myRemotePackage != null) {
      return String.format("%s not installed", myRemotePackage.getAndroidVersion());
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
