/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.model;

import com.android.sdklib.AndroidVersion;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Android information about a module, such as its application package, its minSdkVersion, and so on. This
 * is derived by querying the gradle model, or the manifest file if the model doesn't exist (not constructed, or
 * not a Gradle project).
 *
 * Note that in some cases you may need to obtain information from the merged manifest file. In such a case,
 * either obtain it from {@link AndroidModuleInfo} if the information is also available in the gradle model
 * (e.g. minSdk, targetSdk, packageName, etc), or use {@link ManifestInfo#get(Module)}.
 */
public class AndroidModuleInfo {
  private final @NotNull AndroidFacet myFacet;

  private AndroidModuleInfo(@NotNull AndroidFacet facet) {
    myFacet = facet;
  }

  @NotNull
  public static AndroidModuleInfo create(@NotNull AndroidFacet facet) {
    return new AndroidModuleInfo(facet);
  }

  @NotNull
  public static AndroidModuleInfo get(@NotNull AndroidFacet facet) {
    return facet.getAndroidModuleInfo();
  }

  @Nullable
  public static AndroidModuleInfo get(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet != null ? facet.getAndroidModuleInfo() : null;
  }

  /**
   * Obtains the applicationId name for the current variant, or if not specified, from the primary manifest.
   * This method will return the applicationId from gradle, even if the manifest merger fails.
   */
  @Nullable
  public String getPackage() {
    AndroidModel androidModel = myFacet.getAndroidModel();
    if (androidModel != null) {
      return androidModel.getApplicationId();
    }

    // Read from the manifest: Not overridden in the configuration
    return ManifestInfo.get(myFacet).getApplicationId();
  }

  /**
   * Returns the minSdkVersion that we pass to the runtime. This is normally the same as
   * {@link #getMinSdkVersion()}, but with preview platforms the minSdkVersion, targetSdkVersion
   * and compileSdkVersion are all coerced to the same preview platform value. This method
   * should be used by launch code for example or packaging code.
   */
  @NotNull
  public AndroidVersion getRuntimeMinSdkVersion() {
    AndroidModel androidModel = myFacet.getAndroidModel();
    if (androidModel != null) {
      AndroidVersion minSdkVersion = androidModel.getRuntimeMinSdkVersion();
      if (minSdkVersion != null) {
        return minSdkVersion;
      }
      // Else: not specified in gradle files; fall back to manifest
    }

    return ManifestInfo.get(myFacet).getMinSdkVersion();
  }

  @NotNull
  public AndroidVersion getMinSdkVersion() {
    AndroidModel androidModel = myFacet.getAndroidModel();
    if (androidModel != null) {
      AndroidVersion minSdkVersion = androidModel.getMinSdkVersion();
      if (minSdkVersion != null) {
        return minSdkVersion;
      }
      // Else: not specified in gradle files; fall back to manifest
    }

    return ManifestInfo.get(myFacet).getMinSdkVersion();
  }

  @NotNull
  public AndroidVersion getTargetSdkVersion() {
    AndroidModel androidModel = myFacet.getAndroidModel();
    if (androidModel != null) {
      AndroidVersion targetSdkVersion = androidModel.getTargetSdkVersion();
      if (targetSdkVersion != null) {
        return targetSdkVersion;
      }
      // Else: not specified in gradle files; fall back to manifest
    }

    return ManifestInfo.get(myFacet).getTargetSdkVersion();
  }

  @Nullable
  public AndroidVersion getBuildSdkVersion() {
    // TODO: Get this from the model! For now, we take advantage of the fact that
    // the model should have synced the right type of Android SDK to the IntelliJ facet.
    AndroidPlatform platform = AndroidPlatform.getInstance(myFacet.getModule());
    if (platform != null) {
      return platform.getApiVersion();
    }

    return null;
  }

  /**
   * Returns whether the application is debuggable. For Gradle projects, this is a boolean value.
   * For non Gradle projects, this returns a boolean value if the flag is set, or null if the flag unspecified in the manifest.
   */
  @Nullable
  public Boolean isDebuggable() {
    AndroidModel androidModel = myFacet.getAndroidModel();
    if (androidModel != null) {
      Boolean debuggable = androidModel.isDebuggable();
      if (debuggable != null) {
        return debuggable;
      }
    }

    return ManifestInfo.get(myFacet).getApplicationDebuggable();
  }

  @Nullable
  public static AndroidVersion getBuildSdkVersion(@Nullable Module module) {
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        AndroidModuleInfo moduleInfo = get(facet.getModule());
        if (moduleInfo != null) {
          return moduleInfo.getBuildSdkVersion();
        }
      }
    }

    return null;
  }

  public static int getBuildSdkApiLevel(@Nullable Module module) {
    AndroidVersion version = getBuildSdkVersion(module);
    return version != null ? version.getApiLevel() : -1;
  }

  @NotNull
  public static AndroidVersion getTargetSdkVersion(@Nullable Module module) {
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        AndroidModuleInfo moduleInfo = get(facet.getModule());
        if (moduleInfo != null) {
          return moduleInfo.getTargetSdkVersion();
        }
      }
    }

    return AndroidVersion.DEFAULT;
  }

  @NotNull
  public static AndroidVersion getMinSdkVersion(@Nullable Module module) {
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        AndroidModuleInfo moduleInfo = get(facet.getModule());
        if (moduleInfo != null) {
          return moduleInfo.getMinSdkVersion();
        }
      }
    }

    return AndroidVersion.DEFAULT;
  }
}
