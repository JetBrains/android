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
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.ModelBuilderParameter;
import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.project.sync.ng.AndroidModule.ModuleDependency;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.GradleProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.*;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_APP;
import static com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId;
import static java.util.Objects.requireNonNull;

/**
 * Chooses the build variant that will be selected in Android Studio (the IDE can only work with one variant at a time.)
 * <p>
 * It works as follows:
 * <ol>
 *   <li>For Android app modules, the "debug" variant is selected. If the module doesn't have a variant named "debug", it sorts all the
 *   variant names alphabetically and picks the first one.</li>
 *   <li>For Android library modules, it chooses the variant needed by dependent modules. For example, if variant "debug" in module "app"
 *   depends on module "lib" - variant "freeDebug", the selected variant in "lib" will be "freeDebug". If a library module is a leaf
 *   (i.e. no other modules depend on it) a variant will be picked as if the module was an app module.</li>
 * </ol>
 * </p>
 */
class SelectedVariantChooser implements Serializable {
  void chooseSelectedVariants(@NotNull List<SyncModuleModels> projectModels, @NotNull BuildController controller) {
    List<AndroidModule> appModules = new ArrayList<>();
    Map<String, AndroidModule> leafModulesById = new HashMap<>();

    for (SyncModuleModels moduleModels : projectModels) {
      AndroidProject androidProject = moduleModels.findModel(AndroidProject.class);
      GradleProject gradleProject = moduleModels.findModel(GradleProject.class);

      if (androidProject != null && gradleProject != null) {
        AndroidModule module = new AndroidModule(androidProject, moduleModels);

        if (androidProject.getProjectType() == PROJECT_TYPE_APP) {
          // App Module
          Variant variant = selectVariantForAppOrLeaf(androidProject, moduleModels, controller);
          module.setSelectedVariant(variant);
          appModules.add(module);
        }
        else {
          // Library Module
          String id = createUniqueModuleId(gradleProject);
          leafModulesById.put(id, module);
        }
      }
    }

    for (AndroidModule appModule : appModules) {
      for (ModuleDependency dependency : appModule.getModuleDependencies()) {
        if (dependency.variant != null) {
          AndroidModule module = leafModulesById.get(dependency.id);
          if (module != null && module.getSelectedVariant() == null) {
            Variant variant = syncAndAddVariant(dependency.variant, module.getModuleModels(), controller);
            module.setSelectedVariant(variant);
            leafModulesById.remove(dependency.id);
          }
        }
      }
    }

    for (AndroidModule module : leafModulesById.values()) {
      Variant variant = selectVariantForAppOrLeaf(module.getAndroidProject(), module.getModuleModels(), controller);
      module.setSelectedVariant(variant);
    }
  }

  @Nullable
  private static Variant selectVariantForAppOrLeaf(@NotNull AndroidProject androidProject,
                                                   @NotNull SyncModuleModels moduleModels,
                                                   @NotNull BuildController controller) {
    String variant = getDebugOrFirstVariant(androidProject);
    return (variant != null) ? syncAndAddVariant(variant, moduleModels, controller) : null;
  }

  @Nullable
  private static String getDebugOrFirstVariant(@NotNull AndroidProject androidProject) {
    Collection<String> names = androidProject.getVariantNames();
    int nameCount = names.size();
    if (nameCount == 0) {
      return null;
    }
    String debugVariant = "debug";
    if (names.contains(debugVariant)) {
      return debugVariant;
    }
    if (nameCount > 1) {
      List<String> sortedNames = new ArrayList<>(names);
      sortedNames.sort(String::compareTo);
      names = sortedNames;
    }
    return names instanceof List ? ((List<String>)names).get(0) : names.iterator().next();
  }

  @Nullable
  private static Variant syncAndAddVariant(@NotNull String variantName,
                                           @NotNull SyncModuleModels moduleModels,
                                           @NotNull BuildController controller) {
    GradleProject gradleProject = moduleModels.findModel(GradleProject.class);
    requireNonNull(gradleProject);

    Variant variant = controller.getModel(gradleProject, Variant.class, ModelBuilderParameter.class,
                                          parameter -> parameter.setVariantName(variantName));
    if (variant != null) {
      moduleModels.addVariant(variant);
    }
    return variant;
  }
}
