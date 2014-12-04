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

import com.android.SdkConstants;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.internal.repository.updater.SettingsController;
import com.android.sdklib.repository.local.LocalSdk;
import com.android.sdklib.repository.remote.RemoteSdk;
import com.android.tools.idea.sdk.DefaultSdks;
import com.android.utils.NullLogger;
import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSdkData {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.sdk.AndroidSdkData");

  private final Map<IAndroidTarget, SoftReference<AndroidTargetData>> myTargetDatas =
    new HashMap<IAndroidTarget, SoftReference<AndroidTargetData>>();

  private final LocalSdk myLocalSdk;
  private final RemoteSdk myRemoteSdk;
  private final SettingsController mySettingsController;
  private final DeviceManager myDeviceManager;

  private final int myPlatformToolsRevision;
  private final int mySdkToolsRevision;

  private static final ConcurrentMap<String/* sdk path */, SoftReference<AndroidSdkData>> ourCache = Maps.newConcurrentMap();

  /** Singleton access classes */

  @Nullable
  public static AndroidSdkData getSdkData(@NotNull File sdkLocation) {
    return getSdkData(sdkLocation, false);
  }

  @Nullable
  public static AndroidSdkData getSdkData(@NotNull File sdkLocation, boolean forceReparse) {
    String canonicalPath = FileUtil.toCanonicalPath(sdkLocation.getPath());

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
    if (!DefaultSdks.isValidAndroidSdkPath(canonicalLocation)) {
      return null;
    }

    LocalSdk localSdk = new LocalSdk(canonicalLocation);
    AndroidSdkData sdkData = new AndroidSdkData((localSdk));
    ourCache.put(canonicalPath, new SoftReference<AndroidSdkData>(sdkData));
    return sdkData;
  }

  @Nullable
  public static AndroidSdkData getSdkData(@NotNull String sdkPath) {
    File file = new File(FileUtil.toSystemDependentName(sdkPath));
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
    mySettingsController = new SettingsController(new NullLogger() /* TODO */);
    myRemoteSdk = new RemoteSdk(mySettingsController);
    String path = localSdk.getPath();
    assert path != null;
    myPlatformToolsRevision = AndroidCommonUtils.parsePackageRevision(path, SdkConstants.FD_PLATFORM_TOOLS);
    mySdkToolsRevision = AndroidCommonUtils.parsePackageRevision(path, SdkConstants.FD_TOOLS);
    myDeviceManager = DeviceManager.createInstance(localSdk.getLocation(), new MessageBuildingSdkLog());
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
    IAndroidTarget candidate = null;
    for (IAndroidTarget target : getTargets()) {
      if (AndroidSdkUtils.targetHasId(target, apiLevel)) {
        if (target.isPlatform()) {
          return target;
        }
        else if (candidate == null) {
          candidate = target;
        }
      }
    }
    return candidate;
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
    return FileUtil.filesEqual(getLocation(), sdkData.getLocation());
  }

  @Override
  public int hashCode() {
    return FileUtil.fileHashCode(getLocation());
  }

  @NotNull
  public LocalSdk getLocalSdk() {
    return myLocalSdk;
  }

  @NotNull
  public RemoteSdk getRemoteSdk() {
    return myRemoteSdk;
  }

  @NotNull
  public SettingsController getSettingsController() {
    return mySettingsController;
  }

  @NotNull
  public DeviceManager getDeviceManager() {
    return myDeviceManager;
  }

  @NotNull
  public AndroidTargetData getTargetData(@NotNull IAndroidTarget target) {
    final SoftReference<AndroidTargetData> targetDataRef = myTargetDatas.get(target);
    AndroidTargetData targetData = targetDataRef != null ? targetDataRef.get() : null;
    if (targetData == null) {
      targetData = new AndroidTargetData(this, target);
      myTargetDatas.put(target, new SoftReference<AndroidTargetData>(targetData));
    }
    return targetData;
  }
}
