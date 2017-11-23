/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.instantapp;

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

import static com.android.builder.model.AndroidProject.*;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class InstantApps {

  /**
   * This method will find and return all feature modules associated with the facet of an instant app application module.
   *
   * @param facet the {@link AndroidFacet} for the Instant App application module whose feature modules you want to find.
   * @return The {@link List} of {@link Module}s corresponding to all found features.
   */
  @NotNull
  public static List<Module> findFeatureModules(@NotNull AndroidFacet facet) {
    // TODO - this doesn't work for now as we have no dependencies for instantapp modules. However can only build one instantapp in a
    // project at the moment, so just getting all feature modules is exactly equivalent.
    /*List<Module> featureModules = new ArrayList<>();
    AndroidModuleModel androidModuleModel = AndroidModuleModel.get(facet);
    if (facet.getProjectType() == PROJECT_TYPE_INSTANTAPP && androidModuleModel != null) {
      Collection<AndroidLibrary> androidLibraries = androidModuleModel.getMainArtifact().getDependencies().getLibraries();
      List<String> gradlePaths = new ArrayList<>();
      for (AndroidLibrary library : androidLibraries) {
        if (isNotEmpty(library.getProject()) && library instanceof AndroidModuleModel) {
          AndroidModuleModel dependencyModel = (AndroidModuleModel)library;
          assert dependencyModel.getProjectType() == PROJECT_TYPE_FEATURE;
          gradlePaths.add(library.getProject());
        }
      }

      ApplicationManager.getApplication().runReadAction(() -> {
        for (Module module : ModuleManager.getInstance(facet.getModule().getProject()).getModules()) {
          GradleFacet facetToCheck = GradleFacet.getInstance(module);
          if (facetToCheck != null) {
            GradleModuleModel gradleModuleModel = facetToCheck.getGradleModuleModel();
            if (gradleModuleModel != null && gradlePaths.contains(gradleModuleModel.getGradlePath())) {
              featureModules.add(module);
            }
          }
        }
      });
    }
    return featureModules;*/
    return AndroidProjectInfo.getInstance(facet.getModule().getProject()).getAllModulesOfProjectType(PROJECT_TYPE_FEATURE);
  }

  /**
   * This method will find and return a base feature if one exists and is associated with the given facet.
   *
   * @param facet the {@link AndroidFacet} for the Instant App application module whose base feature module you want to find.
   * @return The {@link Module} corresponding with the base feature module or {@code null} if none is found.
   */
  @Nullable
  public static Module findBaseFeature(@NotNull AndroidFacet facet) {
    return findBaseFeature(findFeatureModules(facet));
  }

  /**
   * This method will find and return a base feature if one exists in the given project.
   *
   * @param project the {@link Project} for the Instant App application module whose base feature module you want to find.
   * @return The {@link Module} corresponding with the base feature module or {@code null} if none is found.
   */
  @Nullable
  public static Module findBaseFeature(@NotNull Project project) {
    return findBaseFeature(AndroidProjectInfo.getInstance(project).getAllModulesOfProjectType(PROJECT_TYPE_FEATURE));
  }

  /**
   * This method will find and return the monolithic app, if one exists, in the given project.
   *
   * @param project the {@link Project} whose Monolithic module you want to find.
   * @return The Monolithic {@link Module} name or {@code null} if none is found.
   */
  @Nullable
  public static String findMonolithicModuleName(@NotNull Project project) {
    List<Module> moduleList = AndroidProjectInfo.getInstance(project).getAllModulesOfProjectType(PROJECT_TYPE_APP);
    return moduleList.isEmpty() ? null : moduleList.get(0).getName();
  }

  @Nullable
  private static Module findBaseFeature(@NotNull List<Module> featureModules) {
    Module baseFeature = null;
    for (Module module : featureModules) {
      AndroidModuleModel androidModel = AndroidModuleModel.get(module);
      if (androidModel != null && androidModel.getAndroidProject().isBaseSplit()) {
        baseFeature = module;
        break;
      }
    }
    return baseFeature;
  }

  /**
   * Finds the default URL to use for a modules.
   *
   * @param facet the {@link AndroidFacet} of the module.
   * @return The URL to launch the instant app as a {@link String}.
   */
  @NotNull
  public static String getDefaultInstantAppUrl(@NotNull AndroidFacet facet) {
    String defaultUrl = "<<ERROR - NO URL SET>>";
    List<Module> featureModules = findFeatureModules(facet);
    for (Module module : featureModules) {
      String foundUrl = new InstantAppUrlFinder(MergedManifest.get(module)).getDefaultUrl();
      if (isNotEmpty(foundUrl)) {
        defaultUrl = foundUrl;
        break;
      }
    }
    return defaultUrl;
  }

  public static boolean isInstantAppSdkEnabled() {
    return InstantAppSdks.getInstance().isInstantAppSdkEnabled();
  }

  @NotNull
  public static File getInstantAppSdk() throws FileNotFoundException {
    File sdk = InstantAppSdks.getInstance().getInstantAppSdk(true);
    if (sdk == null) {
      throw new FileNotFoundException("Instant App SDK couldn't be found.");
    }

    return sdk;
  }

  public static long getCompatApiMinVersion() {
    return InstantAppSdks.getInstance().getCompatApiMinVersion();
  }

  public static boolean isInstantAppApplicationModule(@NotNull Module module) {
    AndroidModuleModel model = AndroidModuleModel.get(module);
    return model != null && model.getAndroidProject().getProjectType() == PROJECT_TYPE_INSTANTAPP;
  }

  public static boolean isPostO(IDevice device) {
    AndroidVersion version = device.getVersion();

    // Previews of O have api level 25, so comparing with #isGreaterOrEqualThan(apiLevel) doesn't work here.
    return version.compareTo(25, "O") >= 0;
  }
}
