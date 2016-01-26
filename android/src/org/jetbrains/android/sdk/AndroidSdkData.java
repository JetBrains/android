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

import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.tools.idea.sdkv2.StudioLoggerProgressIndicator;
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
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static com.android.SdkConstants.FD_PLATFORM_TOOLS;
import static com.android.tools.idea.sdk.IdeSdks.isValidAndroidSdkPath;
import static com.intellij.openapi.util.io.FileUtil.*;
import static org.jetbrains.android.sdk.AndroidSdkUtils.targetHasId;
import static org.jetbrains.android.util.AndroidCommonUtils.parsePackageRevision;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSdkData {
  private final Map<IAndroidTarget, SoftReference<AndroidTargetData>> myTargetDataByTarget = Maps.newHashMap();

  private final DeviceManager myDeviceManager;

  private final int myPlatformToolsRevision;

  private static final ConcurrentMap<String/* sdk path */, SoftReference<AndroidSdkData>> ourCache = Maps.newConcurrentMap();
  private AndroidSdkHandler mySdkHandler;

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

    AndroidSdkData sdkData = new AndroidSdkData(canonicalLocation);
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

  private AndroidSdkData(@NotNull File localSdk) {
    mySdkHandler = AndroidSdkHandler.getInstance(localSdk);
    File location = getLocation();
    String locationPath = location.getPath();
    myPlatformToolsRevision = parsePackageRevision(locationPath, FD_PLATFORM_TOOLS);
    myDeviceManager = DeviceManager.createInstance(location, new MessageBuildingSdkLog());
  }

  @NotNull
  public File getLocation() {
    File location = mySdkHandler.getLocation();

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
    return mySdkHandler.getLatestBuildTool(new StudioLoggerProgressIndicator(getClass()));
  }

  @NotNull
  public IAndroidTarget[] getTargets() {
    Collection<IAndroidTarget> targets = getTargetCollection();
    return targets.toArray(new IAndroidTarget[targets.size()]);
  }

  @NotNull
  private Collection<IAndroidTarget> getTargetCollection() {
    ProgressIndicator progress = new StudioLoggerProgressIndicator(getClass());
    return mySdkHandler.getAndroidTargetManager(progress).getTargets(progress);
  }

  @NotNull
  public IAndroidTarget[] getTargets(boolean includeAddOns) {
    Collection<IAndroidTarget> targets = getTargetCollection();
    Collection<IAndroidTarget> result = Lists.newArrayList();
    if (!includeAddOns) {
      for (IAndroidTarget target : targets) {
        if (target.isPlatform()) {
          result.add(target);
        }
      }
    }
    else {
      result.addAll(targets);
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
    ProgressIndicator progress = new StudioLoggerProgressIndicator(getClass());
    return mySdkHandler.getAndroidTargetManager(progress).getTargetFromHashString(hashString, progress);
  }

  public int getPlatformToolsRevision() {
    return myPlatformToolsRevision;
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

  @NotNull
  public AndroidSdkHandler getSdkHandler() {
    return mySdkHandler;
  }
}
