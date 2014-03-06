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

import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.Task;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Android information about a module, such as its application package, its minSdkVersion, and so on. This
 * is derived from a combination of gradle files and manifest files.
 * <p>
 * NOTE: The build process allows for specifying and overriding different components in either the Gradle build scripts,
 * the main manifest file, or a variant specific manifest file. These values should always be obtained from the final
 * merged manifest file for a particular variant. However, there are two issues:
 * <ol>
 *   <li>The merged manifest file may not always be available, since it is present within the build folder
 * and that might have been cleaned.</li>
 *   <li>Reading/Parsing a file is higher overhead that simply querying the Gradle model.</li>
 * </ol>
 * As a result, the code below uses the following sequence to obtain required information:
 * <ol>
 *   <li>Query the gradle model for the current variant. If the value is specified/overridden in
 *   gradle build scripts, then this should be sufficient.</li>
 *   <li>The model won't have information if it is not specified in a build script. In such a case,
 *   the merged manifest file should be parsed for the information</li>
 *   <li>If the merged manifest file is not available, then the final fallback is to parse the main manifest.</li>
 * </ol>
 * <p>
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

  /** @deprecated Use {@link #getPackage(boolean)} which is explicit about whether the merged manifest should be used. */
  @Nullable
  public String getPackage() {
    return getPackage(false);
  }

  public int getMinSdkVersion() {
    return getMinSdkVersion(false);
  }

  public String getMinSdkName() {
    return getMinSdkName(false);
  }

  public int getTargetSdkVersion() {
    return getTargetSdkVersion(false);
  }

  @Nullable
  public String getPackage(boolean preferMergedManifest) {
    IdeaAndroidProject project = myFacet.getIdeaAndroidProject();
    if (project != null) {
      return project.computePackageName();
    }

    // Read from the manifest: Not overridden in the configuration
    return ManifestInfo.get(myFacet.getModule(), preferMergedManifest).getPackage();
  }

  public int getMinSdkVersion(boolean preferMergedManifest) {
    IdeaAndroidProject project = myFacet.getIdeaAndroidProject();
    if (project != null) {
      int minSdkVersion = project.getSelectedVariant().getMergedFlavor().getMinSdkVersion();
      if (minSdkVersion >= 1) {
        return minSdkVersion;
      }
      // Else: not specified in gradle files; fall back to manifest
    }

    return ManifestInfo.get(myFacet.getModule(), preferMergedManifest).getMinSdkVersion();
  }

  public String getMinSdkName(boolean preferMergedManifest) {
    String codeName = ManifestInfo.get(myFacet.getModule(), preferMergedManifest).getMinSdkCodeName();
    if (codeName != null) {
      return codeName;
    }

    return Integer.toString(getMinSdkVersion(preferMergedManifest));
  }

  public int getTargetSdkVersion(boolean preferMergedManifest) {
    IdeaAndroidProject project = myFacet.getIdeaAndroidProject();
    if (project != null) {
      int targetSdkVersion = project.getSelectedVariant().getMergedFlavor().getTargetSdkVersion();
      if (targetSdkVersion >= 1) {
        return targetSdkVersion;
      }
      // Else: not specified in gradle files; fall back to manifest
    }

    return ManifestInfo.get(myFacet.getModule(), preferMergedManifest).getTargetSdkVersion();
  }

  @Nullable
  public Manifest getManifest(boolean preferMergedManifest) {
    return ManifestInfo.get(myFacet.getModule(), preferMergedManifest).getManifest();
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

  @Nullable
  public static Manifest getManifest(@Nullable Module module, boolean preferMergedManifest) {
    if (module == null) {
      return null;
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return null;
    }

    AndroidModuleInfo moduleInfo = get(facet.getModule());
    return moduleInfo == null ? null : moduleInfo.getManifest(preferMergedManifest);
  }
}
