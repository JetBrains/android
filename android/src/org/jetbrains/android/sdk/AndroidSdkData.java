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

import com.android.prefs.AndroidLocationsSingleton;
import com.android.repository.Revision;
import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.io.FilePaths;
import com.android.tools.idea.sdk.IdeDeviceManagers;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.google.common.collect.Maps;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.reference.SoftReference;
import java.nio.file.Path;
import java.util.ArrayList;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static com.android.SdkConstants.FD_PLATFORM_TOOLS;
import static com.intellij.openapi.util.io.FileUtil.*;
import static org.jetbrains.android.sdk.AndroidSdkUtils.targetHasId;
import static org.jetbrains.android.util.AndroidBuildCommonUtils.parsePackageRevision;

public class AndroidSdkData {
  private final Map<String, SoftReference<AndroidTargetData>> myTargetDataByTarget = Maps.newHashMap();

  private final DeviceManager myDeviceManager;

  private final int myPlatformToolsRevision;

  private static final ConcurrentMap<String/* sdk path */, SoftReference<AndroidSdkData>> ourCache = Maps.newConcurrentMap();
  private final AndroidSdkHandler mySdkHandler;


  @Nullable
  public static AndroidSdkData getSdkData(@NotNull AndroidFacet facet) {
    return ModuleSdkDataHolder.getInstance(facet).getSdkData();
  }

  @NotNull
  public static AndroidSdkHandler getSdkHolder(@NotNull AndroidFacet facet) {
    return ModuleSdkDataHolder.getInstance(facet).getSdkHandler();
  }

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
    if (!IdeSdks.getInstance().isValidAndroidSdkPath(canonicalLocation)) {
      return null;
    }

    AndroidSdkData sdkData = new AndroidSdkData(canonicalLocation);
    ourCache.put(canonicalPath, new SoftReference<>(sdkData));
    return sdkData;
  }

  @Nullable
  public static AndroidSdkData getSdkData(@NotNull String sdkPath) {
    return getSdkData(FilePaths.stringToFile(sdkPath));
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
    mySdkHandler = AndroidSdkHandler.getInstance(AndroidLocationsSingleton.INSTANCE, localSdk.toPath());
    String locationPath = getLocation().toString();
    Revision platformToolsRevision = parsePackageRevision(locationPath, FD_PLATFORM_TOOLS);
    myPlatformToolsRevision = platformToolsRevision == null ? -1 : platformToolsRevision.getMajor();
    myDeviceManager = IdeDeviceManagers.getDeviceManager(mySdkHandler);
  }

  @NotNull
  public Path getLocation() {
    Path location = mySdkHandler.getLocation();
    // We only construct AndroidSdkData when we have a local SDK, which means location must not be null.
    assert location != null;
    return location;
  }

  @NotNull
  public File getLocationFile() {
    return mySdkHandler.getLocation().toFile();
  }

  @Deprecated
  @NotNull
  public String getPath() {
    return getLocation().toString();
  }

  @Nullable
  public BuildToolInfo getLatestBuildTool(boolean allowPreview) {
    return mySdkHandler.getLatestBuildTool(new StudioLoggerProgressIndicator(getClass()), allowPreview);
  }

  @NotNull
  public IAndroidTarget[] getTargets() {
    Collection<IAndroidTarget> targets = getTargetCollection();
    return targets.toArray(new IAndroidTarget[0]);
  }

  @NotNull
  private Collection<IAndroidTarget> getTargetCollection() {
    ProgressIndicator progress = new StudioLoggerProgressIndicator(getClass());
    return mySdkHandler.getAndroidTargetManager(progress).getTargets(progress);
  }

  @NotNull
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
    return pathsEqual(getLocation().toString(), sdkData.getLocation().toString());
  }

  @Override
  public int hashCode() {
    return pathHashCode(getLocation().toString());
  }

  @NotNull
  public DeviceManager getDeviceManager() {
    return myDeviceManager;
  }

  @NotNull
  public AndroidTargetData getTargetData(@NotNull IAndroidTarget target) {
    String key = target.hashString();
    final SoftReference<AndroidTargetData> targetDataRef = myTargetDataByTarget.get(key);
    AndroidTargetData targetData = targetDataRef != null ? targetDataRef.get() : null;
    if (targetData == null) {
      targetData = new AndroidTargetData(this, target);
      myTargetDataByTarget.put(key, new SoftReference<>(targetData));
    }
    return targetData;
  }

  @NotNull
  public AndroidSdkHandler getSdkHandler() {
    return mySdkHandler;
  }

  private static class ModuleSdkDataHolder implements Disposable {
    private static final Key<ModuleSdkDataHolder> KEY = Key.create(ModuleSdkDataHolder.class.getName());

    private AndroidFacet myFacet;

    @NotNull private final AndroidSdkHandler mySdkHandler;

    @Nullable private final AndroidSdkData mySdkData;

    @NotNull
    static ModuleSdkDataHolder getInstance(@NotNull AndroidFacet facet) {
      ModuleSdkDataHolder sdkDataHolder = facet.getUserData(KEY);
      if (sdkDataHolder == null) {
        sdkDataHolder = new ModuleSdkDataHolder(facet);
        facet.putUserData(KEY, sdkDataHolder);
      }
      return sdkDataHolder;
    }

    ModuleSdkDataHolder(@NotNull AndroidFacet facet) {
      myFacet = facet;
      Disposer.register(facet, this);

      AndroidPlatform platform = AndroidPlatform.getInstance(facet.getModule());
      if (platform != null) {
        mySdkData = platform.getSdkData();
        mySdkHandler = mySdkData.getSdkHandler();
      }
      else {
        mySdkData = null;
        mySdkHandler = AndroidSdkHandler.getInstance(AndroidLocationsSingleton.INSTANCE, null);
      }
    }

    @Nullable
    AndroidSdkData getSdkData() {
      return mySdkData;
    }

    @NotNull
    AndroidSdkHandler getSdkHandler() {
      return mySdkHandler;
    }

    @Override
    public void dispose() {
      myFacet.putUserData(KEY, null);
      myFacet = null;
    }
  }
}
