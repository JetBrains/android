// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.android.sdk;

import static com.android.SdkConstants.FD_PLATFORM_TOOLS;
import static com.android.tools.idea.io.FilePaths.toSystemDependentPath;
import static com.intellij.openapi.util.io.FileUtil.fileHashCode;
import static com.intellij.openapi.util.io.FileUtil.filesEqual;
import static com.intellij.openapi.util.io.FileUtil.toCanonicalPath;
import static org.jetbrains.android.sdk.AndroidSdkUtils.targetHasId;
import static org.jetbrains.android.util.AndroidBuildCommonUtils.parsePackageRevision;

import com.android.repository.Revision;
import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.google.common.collect.Maps;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.reference.SoftReference;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public final class AndroidSdkData {
  private final Map<String, SoftReference<AndroidTargetData>> myTargetDataByTarget = Maps.newHashMap();

  private final DeviceManager myDeviceManager;

  private final int myPlatformToolsRevision;

  private static final ConcurrentMap<String/* sdk path */, SoftReference<AndroidSdkData>> ourCache = Maps.newConcurrentMap();
  private AndroidSdkHandler mySdkHandler;


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
    File file = toSystemDependentPath(sdkPath);
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
    Revision platformToolsRevision = parsePackageRevision(locationPath, FD_PLATFORM_TOOLS);
    myPlatformToolsRevision = platformToolsRevision == null ? -1 : platformToolsRevision.getMajor();
    myDeviceManager = DeviceManager.createInstance(mySdkHandler, new MessageBuildingSdkLog());
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

  /**
   * @link Use {#getLatestBuildTool(boolean)}
   */
  @Deprecated
  @Nullable
  public BuildToolInfo getLatestBuildTool() {
    return getLatestBuildTool(false);
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
        mySdkHandler = AndroidSdkHandler.getInstance(null);
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
