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
import static com.android.sdklib.SystemImageTags.AUTOMOTIVE_PLAY_STORE_TAG;
import static com.android.sdklib.SystemImageTags.AUTOMOTIVE_TAG;
import static com.android.sdklib.SystemImageTags.CHROMEOS_TAG;
import static com.android.sdklib.SystemImageTags.DESKTOP_TAG;
import static com.android.sdklib.SystemImageTags.GOOGLE_APIS_TAG;
import static com.android.sdklib.SystemImageTags.GOOGLE_APIS_X86_TAG;
import static com.android.sdklib.SystemImageTags.GOOGLE_TV_TAG;
import static com.android.sdklib.SystemImageTags.PLAY_STORE_TAG;
import static com.android.sdklib.SystemImageTags.WEAR_TAG;

import com.android.annotations.NonNull;
import com.android.repository.Revision;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoPackage;
import com.android.repository.impl.meta.TypeDetails;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.SystemImageTags;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.targets.PlatformTarget;
import com.android.sdklib.repository.targets.SystemImage;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Information on a system image. Used internally by the avd manager.
 */
public final class SystemImageDescription {
  private ISystemImage mySystemImage;
  private RemotePackage myRemotePackage;

  public static final Set<IdDisplay> TAGS_WITH_GOOGLE_API = ImmutableSet.of(GOOGLE_APIS_TAG, GOOGLE_APIS_X86_TAG,
                                                                            PLAY_STORE_TAG, ANDROID_TV_TAG, GOOGLE_TV_TAG,
                                                                            WEAR_TAG, DESKTOP_TAG, CHROMEOS_TAG,
                                                                            AUTOMOTIVE_TAG, AUTOMOTIVE_PLAY_STORE_TAG);

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
        TAGS_WITH_GOOGLE_API.contains(((DetailsTypes.AddonDetailsType)details).getTag()) && apiLevel <= 19) {
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
           Objects.equal(this.getAbiType(), other.getAbiType()) &&
           Objects.equal(this.getTag(), other.getTag());
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

  @NotNull
  public String getAbiType() {
    return mySystemImage.getAbiType();
  }

  @NotNull
  public IdDisplay getTag() {
    return mySystemImage.getTag();
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
  Revision getRevision() {
    return mySystemImage.getRevision();
  }

  public Path[] getSkins() {
    return mySystemImage.getSkins();
  }

  public ISystemImage getSystemImage() {
    return mySystemImage;
  }

  private static class RemoteSystemImage implements ISystemImage {
    private final RemotePackage myRemotePackage;
    private final IdDisplay myTag;
    private final IdDisplay myVendor;
    private final String myAbi;
    private final AndroidVersion myAndroidVersion;

    public RemoteSystemImage(RemotePackage p) {
      myRemotePackage = p;

      TypeDetails details = myRemotePackage.getTypeDetails();
      assert details instanceof DetailsTypes.ApiDetailsType;
      myAndroidVersion = ((DetailsTypes.ApiDetailsType)details).getAndroidVersion();

      IdDisplay tag = null;
      IdDisplay vendor = null;
      String abi = "armeabi";

      if (details instanceof DetailsTypes.AddonDetailsType) {
        tag = ((DetailsTypes.AddonDetailsType)details).getTag();
        vendor = ((DetailsTypes.AddonDetailsType)details).getVendor();
        if (SystemImageTags.GOOGLE_APIS_X86_TAG.equals(tag)) {
          abi = "x86";
        }
      }
      if (details instanceof DetailsTypes.SysImgDetailsType) {
        // TODO: support multi-tag
        tag = ((DetailsTypes.SysImgDetailsType)details).getTags().get(0);
        vendor = ((DetailsTypes.SysImgDetailsType)details).getVendor();
        abi = ((DetailsTypes.SysImgDetailsType)details).getAbi();
      }
      myTag = tag != null ? tag : SystemImageTags.DEFAULT_TAG;
      myVendor = vendor;
      myAbi = abi;
    }

    @NonNull
    @Override
    public Path getLocation() {
      assert false : "Can't get location for remote image";
      return Paths.get("");
    }

    @NonNull
    @Override
    public IdDisplay getTag() {
      return myTag;
    }

    @com.android.annotations.Nullable
    @Override
    public IdDisplay getAddonVendor() {
      return myVendor;
    }

    @NonNull
    @Override
    public String getAbiType() {
      return myAbi;
    }

    @NonNull
    @Override
    public Path[] getSkins() {
      return new Path[0];
    }

    @NonNull
    @Override
    public Revision getRevision() {
      return myRemotePackage.getVersion();
    }

    @NonNull
    @Override
    public AndroidVersion getAndroidVersion() {
      return myAndroidVersion;
    }

    @Override
    public boolean hasPlayStore() {
      if (SystemImageTags.PLAY_STORE_TAG.equals(getTag()) || AUTOMOTIVE_PLAY_STORE_TAG.equals(getTag())) {
        return true;
      }
      // A Wear system image has Play Store if it is
      // a recent API version and is NOT Wear-for-China.
      if (SystemImageTags.WEAR_TAG.equals(getTag()) &&
          myAndroidVersion.getApiLevel() >= AndroidVersion.MIN_RECOMMENDED_WEAR_API &&
          !myRemotePackage.getPath().contains(WEAR_CN_DIRECTORY)) {
        return true;
      }
      return false;
    }

    @NotNull
    @Override
    public RepoPackage getPackage() {
      return myRemotePackage;
    }

    @Override
    public boolean obsolete() {
      return myRemotePackage.obsolete();
    }

    @Override
    public int compareTo(ISystemImage o) {
      if (o instanceof RemoteSystemImage) {
        return myRemotePackage.compareTo(((RemoteSystemImage)o).myRemotePackage);
      }
      return 1;
    }

    @Override
    public int hashCode() {
      return myRemotePackage.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof RemoteSystemImage)) {
        return false;
      }
      RemoteSystemImage other = (RemoteSystemImage) o;
      return myRemotePackage.equals(other.myRemotePackage);
    }
  }
}
