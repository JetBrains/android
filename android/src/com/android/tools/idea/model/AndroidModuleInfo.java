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

import com.android.builder.model.BuildTypeContainer;
import com.android.tools.idea.gradle.IdeaAndroidProject;
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
 * (e.g. minSdk, targetSdk, packageName, etc), or use {@link ManifestInfo#get(Module, boolean)}.
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

  /** Obtains the package name for the current variant, or if not specified, from the primary manifest. */
  @Nullable
  public String getPackage() {
    IdeaAndroidProject project = myFacet.getIdeaAndroidProject();
    if (project != null) {
      return project.computePackageName();
    }

    // Read from the manifest: Not overridden in the configuration
    return ManifestInfo.get(myFacet.getModule(), false).getPackage();
  }

  public int getMinSdkVersion() {
    IdeaAndroidProject project = myFacet.getIdeaAndroidProject();
    if (project != null) {
      int minSdkVersion = project.getSelectedVariant().getMergedFlavor().getMinSdkVersion();
      if (minSdkVersion >= 1) {
        return minSdkVersion;
      }
      // Else: not specified in gradle files; fall back to manifest
    }

    return ManifestInfo.get(myFacet.getModule(), false).getMinSdkVersion();
  }

  public String getMinSdkName() {
    String codeName = ManifestInfo.get(myFacet.getModule(), false).getMinSdkCodeName();
    if (codeName != null) {
      return codeName;
    }

    return Integer.toString(getMinSdkVersion());
  }

  public int getTargetSdkVersion() {
    IdeaAndroidProject project = myFacet.getIdeaAndroidProject();
    if (project != null) {
      int targetSdkVersion = project.getSelectedVariant().getMergedFlavor().getTargetSdkVersion();
      if (targetSdkVersion >= 1) {
        return targetSdkVersion;
      }
      // Else: not specified in gradle files; fall back to manifest
    }

    return ManifestInfo.get(myFacet.getModule(), false).getTargetSdkVersion();
  }

  public int getBuildSdkVersion() {
    // TODO: Get this from the model! For now, we take advantage of the fact that
    // the model should have synced the right type of Android SDK to the IntelliJ facet.
    AndroidPlatform platform = AndroidPlatform.getPlatform(myFacet.getModule());
    if (platform != null) {
      return platform.getApiLevel();
    }

    return -1;
  }

  /**
   * Returns whether the application is debuggable. For Gradle projects, this is a boolean value.
   * For non Gradle projects, this returns a boolean value if the flag is set, or null if the flag unspecified in the manifest.
   */
  @Nullable
  public Boolean isDebuggable() {
    IdeaAndroidProject project = myFacet.getIdeaAndroidProject();
    if (project != null) {
      BuildTypeContainer buildTypeContainer = project.findBuildType(project.getSelectedVariant().getBuildType());
      if (buildTypeContainer != null) {
        return buildTypeContainer.getBuildType().isDebuggable();
      }
    }

    return ManifestInfo.get(myFacet.getModule(), false).getApplicationDebuggable();
  }

  public static int getBuildSdkVersion(@Nullable Module module) {
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        AndroidModuleInfo moduleInfo = get(facet.getModule());
        if (moduleInfo != null) {
          return moduleInfo.getBuildSdkVersion();
        }
      }
    }

    return -1;
  }

  public static int getTargetSdkVersion(@Nullable Module module) {
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        AndroidModuleInfo moduleInfo = get(facet.getModule());
        if (moduleInfo != null) {
          return moduleInfo.getTargetSdkVersion();
        }
      }
    }

    return -1;
  }

  public static int getMinSdkVersion(@Nullable Module module) {
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        AndroidModuleInfo moduleInfo = get(facet.getModule());
        if (moduleInfo != null) {
          return moduleInfo.getMinSdkVersion();
        }
      }
    }

    return -1;
  }
}
