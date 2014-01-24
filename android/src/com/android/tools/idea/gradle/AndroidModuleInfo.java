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
package com.android.tools.idea.gradle;

import com.android.tools.idea.rendering.ManifestInfo;
import com.intellij.openapi.module.Module;
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

  @Nullable
  public String getPackage() {
    IdeaAndroidProject project = myFacet.getIdeaAndroidProject();
    if (project != null) {
      return project.computePackageName();
    }

    // Read from the manifest: Not overridden in the configuration
    // Note: The package name is always obtained from the main manifest, unless it is overridden in gradle,
    // so in this case we don't have to check the merged manifest.
    return ManifestInfo.get(myFacet.getModule()).getPackage();
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

    // Note: The min sdk version can only come from the main manifest (libraries can only have a lower minSdk),
    // so there is no need to check the merged manifest.
    return ManifestInfo.get(myFacet.getModule()).getMinSdkVersion();
  }

  public String getMinSdkName() {
    String codeName = ManifestInfo.get(myFacet.getModule()).getMinSdkCodeName();
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

    // TODO: there could be more than one manifest file; I need to look at the merged view!
    return ManifestInfo.get(myFacet.getModule()).getTargetSdkVersion();
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
}
