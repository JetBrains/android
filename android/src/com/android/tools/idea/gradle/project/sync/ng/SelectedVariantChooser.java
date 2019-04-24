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

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_APP;
import static com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId;
import static java.util.Objects.requireNonNull;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.ModelBuilderParameter;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.NativeVariantAbi;
import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.project.sync.ng.AndroidModule.ModuleDependency;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Chooses the build variant that will be selected in Android Studio (the IDE can only work with one variant at a time.)
 * <p>
 * It works as follows:
 * <ol>
 * <li>For Android app modules, the default variant from the model is selected.
 * The logic for this is implemented in the Android Gradle Plugin and in
 * {@link com.android.ide.common.gradle.model.IdeAndroidProject IdeAndroidProject} for backwards compatibility.</li>
 * <li>For Android library modules, it chooses the variant needed by dependent modules. For example, if variant "debug" in module "app"
 * depends on module "lib" - variant "freeDebug", the selected variant in "lib" will be "freeDebug". If a library module is a root
 * (i.e. no other modules depend on it) a variant will be picked as if the module was an app module.</li>
 * </ol>
 * </p>
 */
public class SelectedVariantChooser implements Serializable {
  void chooseSelectedVariants(@NotNull List<SyncModuleModels> projectModels,
                              @NotNull BuildController controller,
                              @NotNull SelectedVariants selectedVariants,
                              boolean shouldGenerateSources) {
    Map<String, AndroidModule> modulesById = new HashMap<>();
    LinkedList<String> allModules = new LinkedList<>();
    Set<String> visitedModules = new HashSet<>();

    for (SyncModuleModels moduleModels : projectModels) {
      AndroidProject androidProject = moduleModels.findModel(AndroidProject.class);
      GradleProject gradleProject = moduleModels.findModel(GradleProject.class);

      if (gradleProject != null && androidProject != null && androidProject.getVariants().isEmpty()) {
        NativeAndroidProject nativeAndroidProject = moduleModels.findModel(NativeAndroidProject.class);
        AndroidModule module = new AndroidModule(androidProject, moduleModels, nativeAndroidProject);
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
        Variant variant = selectVariantForAppOrLeaf(module, controller, selectedVariants, moduleId, shouldGenerateSources);
        if (variant != null) {
          String abi = syncAndAddNativeVariantAbi(module, controller, variant.getName(), selectedVariants.getSelectedAbi(moduleId));
          module.addSelectedVariant(variant, abi);
          selectVariantForChildModules(module, controller, modulesById, visitedModules, shouldGenerateSources);
        }
      }
    }
  }

  private static void selectVariantForChildModules(@NotNull AndroidModule parentModule,
                                                   @NotNull BuildController controller,
                                                   @NotNull Map<String, AndroidModule> libModulesById,
                                                   @NotNull Set<String> visitedModules,
                                                   boolean shouldGenerateSources) {
    for (ModuleDependency dependency : parentModule.getModuleDependencies()) {
      String childModuleId = dependency.id;
      visitedModules.add(childModuleId);
      String childVariantName = dependency.inheritedVariant;
      if (childVariantName == null) {
        continue;
      }
      AndroidModule childModule = libModulesById.get(childModuleId);
      if (childModule == null || childModule.containsVariant(childVariantName)) {
        // continue if the child module does not exist or its variant has already been chosen
        continue;
      }
      String childAbiName = null;
      if (childModule.getNativeAndroidProject() != null) {
        childAbiName = syncAndAddNativeVariantAbi(childModule, controller, childVariantName, dependency.inheritedAbi);
      }
      Variant childVariant = syncAndAddVariant(childVariantName, childModule.getModuleModels(), controller, shouldGenerateSources);
      if (childVariant != null) {
        childModule.addSelectedVariant(childVariant, childAbiName);
        selectVariantForChildModules(childModule, controller, libModulesById, visitedModules, shouldGenerateSources);
      }
    }
  }

  @Nullable
  private static Variant selectVariantForAppOrLeaf(@NotNull AndroidModule androidModule,
                                                   @NotNull BuildController controller,
                                                   @NotNull SelectedVariants selectedVariants,
                                                   @NotNull String moduleId,
                                                   boolean shouldGenerateSources) {
    Collection<String> variantNames;
    try {
      variantNames = androidModule.getAndroidProject().getVariantNames();
    }
    catch (UnsupportedMethodException ignore) {
      return null;
    }
    String variant = selectedVariants.getSelectedVariant(moduleId);
    // If this is the first sync (variant == null), or the previously selected variant no longer exists then choose the default variant.
    if (variant == null || !variantNames.contains(variant)) {
      try {
        variant = androidModule.getAndroidProject().getDefaultVariant();
      }
      catch (UnsupportedMethodException e) {
        variant = getDefaultOrFirstItem(variantNames, "debug");
      }
    }
    return (variant != null) ? syncAndAddVariant(variant, androidModule.getModuleModels(), controller, shouldGenerateSources) : null;
  }

  @Nullable
  public static String getDefaultOrFirstItem(@NotNull Collection<String> names, @NotNull String defaultValue) {
    if (names.isEmpty()) {
      return null;
    }
    return names.contains(defaultValue) ? defaultValue : Collections.min(names, String::compareTo);
  }

  @Nullable
  private static Variant syncAndAddVariant(@NotNull String variantName,
                                           @NotNull SyncModuleModels moduleModels,
                                           @NotNull BuildController controller,
                                           boolean shouldGenerateSources) {
    GradleProject gradleProject = moduleModels.findModel(GradleProject.class);
    requireNonNull(gradleProject);

    Variant variant = controller.findModel(gradleProject, Variant.class, ModelBuilderParameter.class,
                                           parameter -> {
                                             parameter.setVariantName(variantName);
                                             parameter.setShouldGenerateSources(shouldGenerateSources);
                                           });
    if (variant != null) {
      moduleModels.addModel(Variant.class, variant);
    }
    return variant;
  }

  @Nullable
  private static String syncAndAddNativeVariantAbi(@NotNull AndroidModule androidModule,
                                                   @NotNull BuildController controller,
                                                   @NotNull String variant,
                                                   @Nullable String abi) {
    NativeAndroidProject nativeAndroidProject = androidModule.getNativeAndroidProject();
    if (nativeAndroidProject != null) {
      Collection<String> abiNames;
      try {
        abiNames = nativeAndroidProject.getVariantInfos().get(variant).getAbiNames();
      }
      catch (UnsupportedMethodException e) {
        return null;
      }
      if (abi == null || !abiNames.contains(abi)) {
        abi = getDefaultOrFirstItem(abiNames, "x86");
      }
      if (abi != null) {
        String abiToSelect = abi;
        SyncModuleModels moduleModels = androidModule.getModuleModels();
        GradleProject gradleProject = moduleModels.findModel(GradleProject.class);
        requireNonNull(gradleProject);
        NativeVariantAbi variantAbi = controller.findModel(gradleProject, NativeVariantAbi.class, ModelBuilderParameter.class,
                                                           parameter -> {
                                                             parameter.setVariantName(variant);
                                                             parameter.setAbiName(abiToSelect);
                                                           });
        if (variantAbi != null) {
          moduleModels.addModel(NativeVariantAbi.class, variantAbi);
        }
        return abiToSelect;
      }
    }
    return null;
  }
}
