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

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

import com.android.AndroidProjectTypes;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class InstantApps {

  /**
   * This method will find and return all Instant App Feature modules associated with the facet of an instant app application module.
   *
   * @param facet the {@link AndroidFacet} for the Instant App application module whose feature modules you want to find.
   * @return The {@link List} of {@link Module}s corresponding to all found features.
   */
  @NotNull
  public static List<Module> findFeatureModules(@NotNull AndroidFacet facet) {
    return AndroidProjectInfo.getInstance(facet.getModule().getProject()).getAllModulesOfProjectType(
      AndroidProjectTypes.PROJECT_TYPE_FEATURE);
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
    return findBaseFeature(AndroidProjectInfo.getInstance(project).getAllModulesOfProjectType(AndroidProjectTypes.PROJECT_TYPE_FEATURE));
  }

  @Nullable
  private static Module findBaseFeature(@NotNull List<Module> featureModules) {
    Module baseFeature = null;
    for (Module module : featureModules) {
      AndroidModel androidModel = AndroidModel.get(module);
      if (androidModel != null && androidModel.isBaseSplit()) {
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
    String defaultUrl = "";

    List<Module> featureModules = findFeatureModules(facet);
    for (Module module : featureModules) {
      String foundUrl = new InstantAppUrlFinder(module).getDefaultUrl();
      if (isNotEmpty(foundUrl)) {
        defaultUrl = foundUrl;
        break;
      }
    }
    return defaultUrl;
  }

  public static boolean isInstantAppApplicationModule(@NotNull AndroidFacet androidFacet) {
    return androidFacet.getProperties().PROJECT_TYPE  == AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP;
  }

  public static boolean isPostO(IDevice device) {
    AndroidVersion version = device.getVersion();

    // Previews of O have api level 25, so comparing with #isGreaterOrEqualThan(apiLevel) doesn't work here.
    return version.compareTo(25, "O") >= 0;
  }
}
