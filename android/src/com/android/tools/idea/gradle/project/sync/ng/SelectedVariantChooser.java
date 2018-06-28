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
 * <li>For Android app modules, the "debug" variant is selected. If the module doesn't have a variant named "debug", it sorts all the
 * variant names alphabetically and picks the first one.</li>
 * <li>For Android library modules, it chooses the variant needed by dependent modules. For example, if variant "debug" in module "app"
 * depends on module "lib" - variant "freeDebug", the selected variant in "lib" will be "freeDebug". If a library module is a leaf
 * (i.e. no other modules depend on it) a variant will be picked as if the module was an app module.</li>
 * </ol>
 * </p>
 */
class SelectedVariantChooser implements Serializable {
  void chooseSelectedVariants(@NotNull List<SyncModuleModels> projectModels,
                              @NotNull BuildController controller,
                              @NotNull SelectedVariants selectedVariants) {
    Map<String, AndroidModule> modulesById = new HashMap<>();
    LinkedList<String> allModules = new LinkedList<>();
    Set<String> visitedModules = new HashSet<>();

    for (SyncModuleModels moduleModels : projectModels) {
      AndroidProject androidProject = moduleModels.findModel(AndroidProject.class);
      GradleProject gradleProject = moduleModels.findModel(GradleProject.class);

      if (gradleProject != null && androidProject != null && androidProject.getVariants().isEmpty()) {
        AndroidModule module = new AndroidModule(androidProject, moduleModels);
        String id = createUniqueModuleId(gradleProject);
        modulesById.put(id, module);
        if (androidProject.getProjectType() == PROJECT_TYPE_APP) {
          // Ensure the app modules are in the front, and will be evaluated before library modules.
          allModules.addFirst(id);
        }
        else {
          allModules.addLast(id);
        }
      }
    }

    for (String moduleId : allModules) {
      if (!visitedModules.contains(moduleId)) {
        visitedModules.add(moduleId);
        AndroidModule module = modulesById.get(moduleId);
        requireNonNull(module);
        Variant variant = selectVariantForAppOrLeaf(module, controller, selectedVariants);
        if (variant != null) {
          module.addSelectedVariant(variant);
          selectVariantForDependencyModules(module, controller, modulesById, visitedModules);
        }
      }
    }
  }

  private static void selectVariantForDependencyModules(@NotNull AndroidModule androidModule,
                                                        @NotNull BuildController controller,
                                                        @NotNull Map<String, AndroidModule> libModulesById,
                                                        @NotNull Set<String> visitedModules) {
    for (ModuleDependency dependency : androidModule.getModuleDependencies()) {
      String dependencyModuleId = dependency.id;
      visitedModules.add(dependencyModuleId);
      String variantName = dependency.variant;
      if (variantName != null) {
        AndroidModule dependencyModule = libModulesById.get(dependencyModuleId);
        if (dependencyModule != null && !dependencyModule.containsVariant(variantName)) {
          Variant dependencyVariant = syncAndAddVariant(variantName, dependencyModule.getModuleModels(), controller);
          if (dependencyVariant != null) {
            dependencyModule.addSelectedVariant(dependencyVariant);
            selectVariantForDependencyModules(dependencyModule, controller, libModulesById, visitedModules);
          }
        }
      }
    }
  }

  @Nullable
  private static Variant selectVariantForAppOrLeaf(@NotNull AndroidModule androidModule,
                                                   @NotNull BuildController controller,
                                                   @NotNull SelectedVariants selectedVariants) {
    SyncModuleModels moduleModels = androidModule.getModuleModels();
    GradleProject gradleProject = moduleModels.findModel(GradleProject.class);
    requireNonNull(gradleProject);
    String moduleId = createUniqueModuleId(gradleProject);
    String variant = selectedVariants.getSelectedVariant(moduleId);

    Collection<String> variantNames = androidModule.getAndroidProject().getVariantNames();
    // Selected variant is null means that this is the very first sync, choose debug or the first one.
    // Also, make sure the variant in SelectedVariants exists - this can be false if the variants in build files are modified from the last sync.
    if (variant == null || !variantNames.contains(variant)) {
      variant = getDebugOrFirstVariant(variantNames);
    }
    return (variant != null) ? syncAndAddVariant(variant, androidModule.getModuleModels(), controller) : null;
  }

  @Nullable
  private static String getDebugOrFirstVariant(@NotNull Collection<String> names) {
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

    Variant variant = controller.findModel(gradleProject, Variant.class, ModelBuilderParameter.class,
                                           parameter -> parameter.setVariantName(variantName));
    if (variant != null) {
      moduleModels.addModel(Variant.class, variant);
    }
    return variant;
  }
}
