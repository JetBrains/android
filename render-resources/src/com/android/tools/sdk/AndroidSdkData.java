/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.sdk;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.prefs.AndroidLocationsSingleton;
import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

public class AndroidSdkData {
  private final DeviceManager myDeviceManager;
  private static final ConcurrentMap<String/* sdk path */, SoftReference<AndroidSdkData>> ourCache = Maps.newConcurrentMap();
  private final AndroidSdkHandler mySdkHandler;

  @Nullable
  public static AndroidSdkData getSdkData(@NonNull File sdkLocation) {
    return getSdkData(sdkLocation, false);
  }

  @Nullable
  public static AndroidSdkData getSdkData(@NonNull File sdkLocation, boolean forceReparse) {
    return getSdkData(sdkLocation, forceReparse, true);
  }

  @NonNull
  public static AndroidSdkData getSdkDataWithoutValidityCheck(@NonNull File sdkLocation) {
    return Objects.requireNonNull(getSdkData(sdkLocation, false, false));
  }

  @Nullable
  private static AndroidSdkData getSdkData(@NonNull File sdkLocation, boolean forceReparse, boolean checkValidity) {
    String canonicalPath;
    try {
      canonicalPath = sdkLocation.getCanonicalPath();
    } catch (IOException ignore) {
      if (checkValidity) {
        return null;
      } else {
        // We do not care about whether sdk exists or not, we are using the path as a key
        canonicalPath = sdkLocation.getPath();
      }
    }

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
    if (checkValidity && !AndroidSdkPath.isValid(canonicalLocation)) {
      return null;
    }

    AndroidSdkData sdkData = new AndroidSdkData(canonicalLocation);
    ourCache.put(canonicalPath, new SoftReference<>(sdkData));
    return sdkData;
  }

  @Nullable
  public static AndroidSdkData getSdkData(@NonNull String sdkPath) {
    return getSdkData(new File(sdkPath));
  }

  private AndroidSdkData(@NonNull File localSdk) {
    mySdkHandler = AndroidSdkHandler.getInstance(AndroidLocationsSingleton.INSTANCE, localSdk.toPath());
    myDeviceManager = DeviceManagers.getDeviceManager(mySdkHandler);
  }

  @NonNull
  public Path getLocation() {
    Path location = mySdkHandler.getLocation();
    // We only construct AndroidSdkData when we have a local SDK, which means location must not be null.
    assert location != null;
    return location;
  }

  @NonNull
  public File getLocationFile() {
    return mySdkHandler.getLocation().toFile();
  }

  @Deprecated
  @NonNull
  public String getPath() {
    return getLocation().toString();
  }

  @Nullable
  public BuildToolInfo getLatestBuildTool(boolean allowPreview) {
    return mySdkHandler.getLatestBuildTool(new LoggerProgressIndicator(getClass()), allowPreview);
  }

  @NonNull
  public IAndroidTarget[] getTargets() {
    Collection<IAndroidTarget> targets = getTargetCollection();
    return targets.toArray(new IAndroidTarget[0]);
  }

  @NonNull
  private Collection<IAndroidTarget> getTargetCollection() {
    ProgressIndicator progress = new LoggerProgressIndicator(getClass());
    return mySdkHandler.getAndroidTargetManager(progress).getTargets(progress);
  }

  @NonNull
  public IAndroidTarget[] getTargets(boolean includeAddOns) {
    Collection<IAndroidTarget> targets = getTargetCollection();
    Collection<IAndroidTarget> result = new ArrayList<>();
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
    return result.toArray(new IAndroidTarget[0]);
  }

  private static boolean targetHasId(@NonNull IAndroidTarget target, @NonNull String id) {
    return id.equals(target.getVersion().getApiString()) || id.equals(target.getVersionName());
  }

  @Nullable
  public IAndroidTarget findTargetByApiLevel(@NonNull String apiLevel) {
    for (IAndroidTarget target : getTargets()) {
      if (targetHasId(target, apiLevel)) {
        return target;
      }
    }
    return null;
  }

  @Nullable
  public IAndroidTarget findTargetByHashString(@NonNull String hashString) {
    ProgressIndicator progress = new LoggerProgressIndicator(getClass());
    return mySdkHandler.getAndroidTargetManager(progress).getTargetFromHashString(hashString, progress);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) return false;
    if (obj.getClass() != getClass()) return false;
    AndroidSdkData sdkData = (AndroidSdkData)obj;
    return getLocation().normalize().toAbsolutePath().toString().equals(sdkData.getLocation().normalize().toAbsolutePath().toString());
  }

  @Override
  public int hashCode() {
    return getLocation().normalize().toAbsolutePath().toString().hashCode();
  }

  @NonNull
  public DeviceManager getDeviceManager() {
    return myDeviceManager;
  }

  @NonNull
  public AndroidSdkHandler getSdkHandler() {
    return mySdkHandler;
  }
}
