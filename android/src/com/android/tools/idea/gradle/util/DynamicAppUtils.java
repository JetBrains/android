/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.util;

import static com.android.tools.idea.projectsystem.ModuleSystemUtil.getHolderModule;
import static com.android.tools.idea.projectsystem.ProjectSystemUtil.getModuleSystem;

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.ModuleSystemUtil;
import com.android.tools.idea.run.ApkFileUnit;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.text.StringUtil;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Various utility methods to navigate through various parts (dynamic features, base split, etc.)
 * of dynamic apps.
 */
public final class DynamicAppUtils {

  /**
   * Returns the Base Module of the specified dynamic feature {@link Module module}, or null if none is found.
   */
  @Nullable
  public static Module getBaseFeature(@NotNull Module module) {
    return getModuleSystem(module).getBaseFeatureModule();
  }

  /**
   * Returns the list of {@link Module} instances corresponding to feature modules (legacy or dynamic)
   * that depend on the given module.
   *
   * <p>Returns an empty list if feature-on-feature support is disabled.
   *
   * @param featureModule an instant or dynamic feature
   */
  @NotNull
  public static List<Module> getFeatureModulesDependingOnFeature(@NotNull Module featureModule) {
    if (!StudioFlags.SUPPORT_FEATURE_ON_FEATURE_DEPS.get()) {
      return ImmutableList.of();
    }

    // We need to remove modules that belong to the same Gradle project as the feature module e.g the  androidTest and unitTest modules
    return selectFeatureModules(removeModulesIntheSameGradleProject(
      ModuleManager.getInstance(featureModule.getProject()).getModuleDependentModules(ModuleSystemUtil.getMainModule(featureModule))
        .stream(), featureModule));
  }

  /**
   * Returns the list of {@link Module} instances corresponding to feature modules (legacy or dynamic)
   * on which the given feature module depends.
   *
   * <p>Returns an empty list if feature-on-feature support is disabled.
   *
   * @param featureModule an instant or dynamic feature
   */
  @NotNull
  public static List<Module> getFeatureModuleDependenciesForFeature(@NotNull Module featureModule) {
    if (!StudioFlags.SUPPORT_FEATURE_ON_FEATURE_DEPS.get()) {
      return ImmutableList.of();
    }

    // We need to remove modules that belong to the same Gradle project as the feature module e.g the  androidTest and unitTest modules
    return selectFeatureModules(removeModulesIntheSameGradleProject(
      Stream.of(ModuleRootManager.getInstance(ModuleSystemUtil.getMainModule(featureModule)).getDependencies()), featureModule));
  }

  @NotNull
  public static Stream<Module> removeModulesIntheSameGradleProject(@NotNull Stream<Module> modules, @NotNull Module moduleOfProjectToRemove) {
    return modules.filter(m -> getHolderModule(m) != getHolderModule(moduleOfProjectToRemove));
  }

  public static boolean useSelectApksFromBundleBuilder(@NotNull Module module,
                                                       boolean alwaysDeployApkFromBundle,
                                                       boolean deployForTests,
                                                       @Nullable AndroidVersion minTargetDeviceVersion) {
    if (alwaysDeployApkFromBundle) {
      return true;
    }
    // If any device is pre-L *and* module has a dynamic feature, we need to use the bundle tool
    if (minTargetDeviceVersion != null && minTargetDeviceVersion.getFeatureLevel() < AndroidVersion.VersionCodes.LOLLIPOP &&
        !getModuleSystem(module).getDynamicFeatureModules().isEmpty()) {
      return true;
    }

    // Instrumented test support for Dynamic Features
    if (deployForTests) {
      AndroidModuleSystem moduleSystem = getModuleSystem(module);
      AndroidModuleSystem.Type type = moduleSystem.getType();
      if (type == AndroidModuleSystem.Type.TYPE_DYNAMIC_FEATURE) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the list of dynamic feature {@link Module modules} that depend on this base module and are instant app compatible.
   */
  @NotNull
  public static List<Module> getDependentInstantFeatureModules(@NotNull Module module) {
    return getModuleSystem(module)
      .getDynamicFeatureModules()
      .stream()
      .filter(it -> {
                AndroidModel model = AndroidModel.get(it);
                return model != null && model.isInstantAppCompatible();
              }
      )
      .collect(Collectors.toList());
  }

  public static boolean isFeatureEnabled(@NotNull List<String> myDisabledFeatures, @NotNull ApkFileUnit apkFileUnit) {
    return myDisabledFeatures.stream().noneMatch(m -> featureNameEquals(apkFileUnit, m));
  }

  public static boolean featureNameEquals(@NotNull ApkFileUnit apkFileUnit, @NotNull String featureName) {
    return StringUtil.equals(featureName.replace('-', '_'), apkFileUnit.getModuleName());
  }

  /**
   * Finds the modules in a stream that are either legacy or dynamic features. If there are multiple modules belonging to the same
   * dynamic feature (i.e Gradle Project) this method will only return the holder modules.
   */
  @NotNull
  private static List<Module> selectFeatureModules(Stream<Module> moduleStream) {
    return moduleStream.map(ModuleSystemUtil::getHolderModule).distinct().filter(module -> {
      AndroidModuleSystem moduleSystem = getModuleSystem(module);
      AndroidModuleSystem.Type type = moduleSystem.getType();
      return type == AndroidModuleSystem.Type.TYPE_FEATURE || // Legacy
             type == AndroidModuleSystem.Type.TYPE_DYNAMIC_FEATURE;
    }).collect(Collectors.toList());
  }
}
