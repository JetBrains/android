/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android.sdk;

import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.sdklib.repository.local.LocalPlatformPkgInfo;
import com.android.sdklib.repository.local.LocalSdk;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static com.android.SdkConstants.FD_PLATFORM_TOOLS;
import static com.android.SdkConstants.FD_TOOLS;
import static com.android.tools.idea.sdk.IdeSdks.isValidAndroidSdkPath;
import static com.intellij.openapi.util.io.FileUtil.*;
import static org.jetbrains.android.sdk.AndroidSdkUtils.targetHasId;
import static org.jetbrains.android.util.AndroidCommonUtils.parsePackageRevision;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSdkData {
  private final Map<IAndroidTarget, SoftReference<AndroidTargetData>> myTargetDataByTarget = Maps.newHashMap();

  private final LocalSdk myLocalSdk;
  private final DeviceManager myDeviceManager;

  private final int myPlatformToolsRevision;
  private final int mySdkToolsRevision;

  private static final ConcurrentMap<String/* sdk path */, SoftReference<AndroidSdkData>> ourCache = Maps.newConcurrentMap();

  @Nullable
  public static AndroidSdkData getSdkData(@NotNull File sdkLocation) {
    return getSdkData(sdkLocation, false);
  }

  @Nullable
  public static AndroidSdkData getSdkData(@NotNull File sdkLocation, boolean forceReparse) {
    String canonicalPath = toCanonicalPath(sdkLocation.getPath());

    // Try to use cached data.
    if (!forceReparse) {
      SoftReference<AndroidSdkData> cachedRef = ourCache.get(canonicalPath);
      if (cachedRef != null) {
        AndroidSdkData cachedData = cachedRef.get();
        if (cachedData == null) {
          ourCache.remove(canonicalPath, cachedRef);
        }
        else {
          return cachedData;
        }
      }
    }

    File canonicalLocation = new File(canonicalPath);
    if (!isValidAndroidSdkPath(canonicalLocation)) {
      return null;
    }

    AndroidSdkData sdkData = new AndroidSdkData(new LocalSdk(canonicalLocation));
    ourCache.put(canonicalPath, new SoftReference<AndroidSdkData>(sdkData));
    return sdkData;
  }

  @Nullable
  public static AndroidSdkData getSdkData(@NotNull String sdkPath) {
    File file = new File(toSystemDependentName(sdkPath));
    return getSdkData(file);
  }

  @Nullable
  public static AndroidSdkData getSdkData(@NotNull Sdk sdk) {
    String sdkHomePath = sdk.getHomePath();
    if (sdkHomePath != null) {
      return getSdkData(sdk.getHomePath());
    }
    return null;
  }

  @Nullable
  public static AndroidSdkData getSdkData(@NotNull Project project) {
    Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
    if (sdk != null) {
      return getSdkData(sdk);
    }
    return null;
  }

  @Nullable
  public static AndroidSdkData getSdkData(@NotNull Module module) {
    return getSdkData(module.getProject());
  }

  private AndroidSdkData(@NotNull LocalSdk localSdk) {
    myLocalSdk = localSdk;
    File location = getLocation();
    String locationPath = location.getPath();
    myPlatformToolsRevision = parsePackageRevision(locationPath, FD_PLATFORM_TOOLS);
    mySdkToolsRevision = parsePackageRevision(locationPath, FD_TOOLS);
    myDeviceManager = DeviceManager.createInstance(location, new MessageBuildingSdkLog());
  }

  @NotNull
  public File getLocation() {
    File location = myLocalSdk.getLocation();

    // The LocalSdk should always have been initialized.
    assert location != null;
    return location;
  }

  @Deprecated
  @NotNull
  public String getPath() {
    return getLocation().getPath();
  }

  @Nullable
  public BuildToolInfo getLatestBuildTool() {
    return myLocalSdk.getLatestBuildTool();
  }

  @NotNull
  public IAndroidTarget[] getTargets() {
    return myLocalSdk.getTargets();
  }

  @NotNull
  public IAndroidTarget[] getTargets(boolean includeAddOns) {
    if (includeAddOns) {
      return myLocalSdk.getTargets();
    }
    List<IAndroidTarget> result = Lists.newArrayList();
    LocalPkgInfo[] pkgsInfos = myLocalSdk.getPkgsInfos(EnumSet.of(PkgType.PKG_PLATFORM));
    for (LocalPkgInfo info : pkgsInfos) {
      if (info instanceof LocalPlatformPkgInfo) {
        IAndroidTarget target = ((LocalPlatformPkgInfo) info).getAndroidTarget();
        if (target != null) {
          result.add(target);
        }
      }
    }
    return result.toArray(new IAndroidTarget[result.size()]);
  }

  // be careful! target name is NOT unique

  @Nullable
  public IAndroidTarget findTargetByName(@NotNull String name) {
    for (IAndroidTarget target : getTargets()) {
      if (target.getName().equals(name)) {
        return target;
      }
    }
    return null;
  }

  @Nullable
  public IAndroidTarget findTargetByApiLevel(@NotNull String apiLevel) {
    for (IAndroidTarget target : getTargets()) {
      if (targetHasId(target, apiLevel)) {
        return target;
      }
    }
    return null;
  }

  @Nullable
  public IAndroidTarget findTargetByHashString(@NotNull String hashString) {
    return myLocalSdk.getTargetFromHashString(hashString);
  }

  public int getPlatformToolsRevision() {
    return myPlatformToolsRevision;
  }

  public int getSdkToolsRevision() {
    return mySdkToolsRevision;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) return false;
    if (obj.getClass() != getClass()) return false;
    AndroidSdkData sdkData = (AndroidSdkData)obj;
    return filesEqual(getLocation(), sdkData.getLocation());
  }

  @Override
  public int hashCode() {
    return fileHashCode(getLocation());
  }

  @NotNull
  public LocalSdk getLocalSdk() {
    return myLocalSdk;
  }

  @NotNull
  public DeviceManager getDeviceManager() {
    return myDeviceManager;
  }

  @NotNull
  public AndroidTargetData getTargetData(@NotNull IAndroidTarget target) {
    final SoftReference<AndroidTargetData> targetDataRef = myTargetDataByTarget.get(target);
    AndroidTargetData targetData = targetDataRef != null ? targetDataRef.get() : null;
    if (targetData == null) {
      targetData = new AndroidTargetData(this, target);
      myTargetDataByTarget.put(target, new SoftReference<AndroidTargetData>(targetData));
    }
    return targetData;
  }
}
