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

import static com.android.SdkConstants.FD_PLATFORMS;
import static com.android.SdkConstants.FN_FRAMEWORK_LIBRARY;
import static com.android.sdklib.IAndroidTarget.ANDROID_JAR;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.util.PathUtil.getCanonicalPath;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.OptionalLibrary;
import com.android.tools.idea.sdk.AndroidSdks;
import com.google.common.collect.Sets;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidPlatform {
  @NotNull private final AndroidSdkData mySdkData;
  @NotNull private final IAndroidTarget myTarget;

  public AndroidPlatform(@NotNull AndroidSdkData sdkData, @NotNull IAndroidTarget target) {
    mySdkData = sdkData;
    myTarget = target;
  }

  @Nullable
  public static AndroidPlatform getInstance(@NotNull Module module) {
    if (module.isDisposed()) {
      return null;
    }
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    return sdk != null ? getInstance(sdk) : null;
  }

  @Nullable
  public static AndroidPlatform getInstance(@NotNull Sdk sdk) {
    AndroidSdkAdditionalData data = AndroidSdks.getInstance().getAndroidSdkAdditionalData(sdk);
    return data != null ? data.getAndroidPlatform() : null;
  }

  @NotNull
  public AndroidSdkData getSdkData() {
    return mySdkData;
  }

  @NotNull
  public IAndroidTarget getTarget() {
    return myTarget;
  }

  @Nullable
  public static AndroidPlatform parse(@NotNull Sdk sdk) {
    if (!AndroidSdks.getInstance().isAndroidSdk(sdk)) {
      return null;
    }
    AndroidSdkData sdkData = StudioAndroidSdkData.getSdkData(sdk);
    if (sdkData != null) {
      SdkAdditionalData data = sdk.getSdkAdditionalData();
      if (data instanceof AndroidSdkAdditionalData) {
        IAndroidTarget target = ((AndroidSdkAdditionalData)data).getBuildTarget(sdkData);
        if (target != null) {
          return new AndroidPlatform(sdkData, target);
        }
      }
    }
    return null;
  }

  /** @deprecated Use only for converting. */
  @Deprecated
  @Nullable
  public static AndroidPlatform parse(@NotNull Library library,
                                      @Nullable Library.ModifiableModel model,
                                      @Nullable Map<String, AndroidSdkData> parsedSdks) {
    VirtualFile[] files = model != null ? model.getFiles(CLASSES) : library.getFiles(CLASSES);
    Set<String> jarPaths = Sets.newHashSet();
    VirtualFile frameworkLibrary = null;
    for (VirtualFile file : files) {
      VirtualFile vFile = JarFileSystem.getInstance().getVirtualFileForJar(file);
      if (vFile != null) {
        if (vFile.getName().equals(FN_FRAMEWORK_LIBRARY)) {
          frameworkLibrary = vFile;
        }
        jarPaths.add(vFile.getPath());
      }
    }
    if (frameworkLibrary != null) {
      VirtualFile sdkDir = frameworkLibrary.getParent();
      if (sdkDir != null) {
        VirtualFile platformsDir = sdkDir.getParent();
        if (platformsDir != null && platformsDir.getName().equals(FD_PLATFORMS)) {
          sdkDir = platformsDir.getParent();
          if (sdkDir == null) return null;
        }
        String sdkPath = sdkDir.getPath();
        AndroidSdkData sdkData = parsedSdks != null ? parsedSdks.get(sdkPath) : null;
        if (sdkData == null) {
          sdkData = AndroidSdkData.getSdkData(sdkPath);
          if (sdkData == null) return null;
          if (parsedSdks != null) {
            parsedSdks.put(sdkPath, sdkData);
          }
        }
        IAndroidTarget resultTarget = null;
        for (IAndroidTarget target : sdkData.getTargets()) {
          String targetsFrameworkLibPath = target.getPath(ANDROID_JAR).normalize().toString();
          if (frameworkLibrary.getPath().equals(targetsFrameworkLibPath)) {
            if (target.isPlatform()) {
              if (resultTarget == null) resultTarget = target;
            }
            else {
              boolean ok = true;
              List<OptionalLibrary> libraries = target.getAdditionalLibraries();
              if (libraries.isEmpty()) {
                // we cannot identify add-on target without optional libraries by classpath
                ok = false;
              }
              else {
                for (OptionalLibrary optionalLibrary : libraries) {
                  if (!jarPaths.contains(getCanonicalPath(optionalLibrary.getJar().toAbsolutePath().toString()))) {
                    ok = false;
                  }
                }
              }
              if (ok) {
                resultTarget = target;
              }
            }
          }
        }
        if (resultTarget != null) {
          return new AndroidPlatform(sdkData, resultTarget);
        }
      }
    }
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AndroidPlatform platform = (AndroidPlatform)o;

    if (!mySdkData.equals(platform.mySdkData)) return false;
    if (!myTarget.equals(platform.myTarget)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = mySdkData.hashCode();
    result = 31 * result + myTarget.hashCode();
    return result;
  }

  public boolean needToAddAnnotationsJarToClasspath() {
    return AndroidSdks.getInstance().needsAnnotationsJarInClasspath(myTarget);
  }

  public int getApiLevel() {
    return myTarget.getVersion().getApiLevel();
  }

  @NotNull
  public AndroidVersion getApiVersion() {
    return myTarget.getVersion();
  }
}
