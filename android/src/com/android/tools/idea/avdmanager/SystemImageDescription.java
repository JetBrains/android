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
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
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

  public Path[] getSkins() {
    return mySystemImage.getSkins();
  }

  public ISystemImage getSystemImage() {
    return mySystemImage;
  }

  private static class RemoteSystemImage implements ISystemImage {
    private final RemotePackage myRemotePackage;
    private final ImmutableList<IdDisplay> myTags;
    private final IdDisplay myVendor;
    private final List<String> myAbis;
    private final List<String> myTranslatedAbis;
    private final AndroidVersion myAndroidVersion;

    public RemoteSystemImage(RemotePackage p) {
      myRemotePackage = p;

      TypeDetails details = myRemotePackage.getTypeDetails();
      assert details instanceof DetailsTypes.ApiDetailsType;
      myAndroidVersion = ((DetailsTypes.ApiDetailsType)details).getAndroidVersion();

      IdDisplay vendor = null;
      List<String> abis = Collections.singletonList("armeabi");
      List<String> translatedAbis = Collections.emptyList();

      myTags = SystemImageTags.getTags(p);

      if (details instanceof DetailsTypes.AddonDetailsType) {
        vendor = ((DetailsTypes.AddonDetailsType)details).getVendor();
        if (myTags.contains(SystemImageTags.GOOGLE_APIS_X86_TAG)) {
          abis = Collections.singletonList("x86");
        }
      }
      if (details instanceof DetailsTypes.SysImgDetailsType) {
        vendor = ((DetailsTypes.SysImgDetailsType)details).getVendor();
        abis = ((DetailsTypes.SysImgDetailsType)details).getAbis();
        translatedAbis = ((DetailsTypes.SysImgDetailsType)details).getTranslatedAbis();
      }
      myVendor = vendor;
      myAbis = abis;
      myTranslatedAbis = translatedAbis;
    }

    @NonNull
    @Override
    public Path getLocation() {
      assert false : "Can't get location for remote image";
      return Paths.get("");
    }

    @NonNull
    @Override
    public List<IdDisplay> getTags() {
      return myTags;
    }

    @com.android.annotations.Nullable
    @Override
    public IdDisplay getAddonVendor() {
      return myVendor;
    }

    @NotNull
    @Override
    public String getPrimaryAbiType() {
      return myAbis.get(0);
    }

    @NonNull
    @Override
    public List<String> getAbiTypes() {
      return myAbis;
    }

    @NotNull
    @Override
    public List<String> getTranslatedAbiTypes() {
      return myTranslatedAbis;
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
