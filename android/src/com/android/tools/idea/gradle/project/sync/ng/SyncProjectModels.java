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
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.level2.GlobalLibraryMap;
import com.android.java.model.GradlePluginModel;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.*;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

public class SyncProjectModels implements Serializable {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 3L;

  @NotNull private final Set<Class<?>> myExtraAndroidModelTypes;
  @NotNull private final Set<Class<?>> myExtraJavaModelTypes;
  @NotNull private final SyncActionOptions myOptions;
  @NotNull private final SelectedVariantChooser myVariantChooser;

  // List of SyncModuleModels for modules in root build and included builds.
  @NotNull private final List<SyncModuleModels> myModuleModels = new ArrayList<>();
  @NotNull private final List<GlobalLibraryMap> myGlobalLibraryMaps = new ArrayList<>();
  private BuildIdentifier myRootBuildId;

  public SyncProjectModels(@NotNull Set<Class<?>> extraAndroidModelTypes,
                           @NotNull Set<Class<?>> extraJavaModelTypes,
                           @NotNull SyncActionOptions options) {
    this(extraAndroidModelTypes, extraJavaModelTypes, options, new SelectedVariantChooser());
  }

  @VisibleForTesting
  SyncProjectModels(@NotNull Set<Class<?>> extraAndroidModelTypes,
                    @NotNull Set<Class<?>> extraJavaModelTypes,
                    @NotNull SyncActionOptions options,
                    @NotNull SelectedVariantChooser variantChooser) {
    myExtraAndroidModelTypes = extraAndroidModelTypes;
    myExtraJavaModelTypes = extraJavaModelTypes;
    myOptions = options;
    myVariantChooser = variantChooser;
  }

  public void populate(@NotNull BuildController controller) {
    GradleBuild rootBuild = controller.getBuildModel();
    myRootBuildId = rootBuild.getBuildIdentifier();
    List<GradleBuild> gradleBuilds = new ArrayList<>();
    // add the root builds.
    gradleBuilds.add(rootBuild);
    // add the included builds.
    gradleBuilds.addAll(rootBuild.getIncludedBuilds());

    for (GradleBuild gradleBuild : gradleBuilds) {
      failIfKotlinPluginApplied(gradleBuild, controller);
    }

    for (GradleBuild gradleBuild : gradleBuilds) {
      GradleProject gradleProject = controller.findModel(gradleBuild.getRootProject(), GradleProject.class);
      populateModelsForModule(gradleProject, controller, gradleBuild.getBuildIdentifier());
    }

    if (myOptions.isSingleVariantSyncEnabled()) {
      SelectedVariants variants = myOptions.getSelectedVariants();
      requireNonNull(variants);
      myVariantChooser.chooseSelectedVariants(myModuleModels, controller, variants, myOptions.shouldGenerateSources());
    }
    // Ensure unique module names.
    deduplicateModuleNames();
    // Request for GlobalLibraryMap model at last, when all of other models have been built.
    populateGlobalLibraryMap(controller);
  }

  private static void failIfKotlinPluginApplied(@NotNull GradleBuild gradleBuild, @NotNull BuildController controller) {
    GradleProject gradleProject = controller.findModel(gradleBuild.getRootProject(), GradleProject.class);
    GradlePluginModel pluginModel = controller.findModel(gradleProject, GradlePluginModel.class);
    if (pluginModel != null && pluginModel.getGraldePluginList()
                                          .stream()
                                          .anyMatch(p -> p.startsWith("org.jetbrains.kotlin"))) {
      throw new NewGradleSyncNotSupportedException("containing Kotlin modules");
    }
  }

  private void populateModelsForModule(@Nullable GradleProject project,
                                       @NotNull BuildController controller,
                                       @NotNull BuildIdentifier buildId) {
    if (project == null) {
      return;
    }
    SyncModuleModels models = new SyncModuleModels(project, buildId, myExtraAndroidModelTypes, myExtraJavaModelTypes, myOptions);
    models.populate(project, controller);
    myModuleModels.add(models);

    for (GradleProject child : project.getChildren()) {
      populateModelsForModule(child, controller, buildId);
    }
  }

  // If there are duplicated module names, update module name to include gradle path and project name.
  private void deduplicateModuleNames() {
    Map<String, Long> nameCount = myModuleModels.stream().collect(groupingBy(m -> m.getModuleName(), counting()));
    // Deduplicate module names in the same project.
    for (SyncModuleModels moduleModel : myModuleModels) {
      String moduleName = moduleModel.getModuleName();
      if (nameCount.get(moduleName) > 1) {
        moduleModel.includeGradlePathInModuleName();
      }
    }
    // Deduplicate module names across multiple projects.
    nameCount = myModuleModels.stream().collect(groupingBy(m -> m.getModuleName(), counting()));
    for (SyncModuleModels moduleModel : myModuleModels) {
      String moduleName = moduleModel.getModuleName();
      if (nameCount.get(moduleName) > 1) {
        moduleModel.includeProjectNameInModuleName();
      }
    }
  }

  private void populateGlobalLibraryMap(@NotNull BuildController controller) {
    // GlobalLibraryMap can only be requested by android module.
    // Each included project has an instance of GlobalLibraryMap, so the model needs to be requested once for each included project.
    Set<BuildIdentifier> visitedBuildId = new HashSet<>();
    for (SyncModuleModels moduleModels : myModuleModels) {
      AndroidProject androidProject = moduleModels.findModel(AndroidProject.class);
      BuildIdentifier buildId = moduleModels.getBuildId();
      if (androidProject != null && !visitedBuildId.contains(buildId)) {
        GlobalLibraryMap map = controller.findModel(moduleModels.findModel(GradleProject.class), GlobalLibraryMap.class);
        if (map != null) {
          visitedBuildId.add(buildId);
          myGlobalLibraryMaps.add(map);
        }
      }
    }
  }

  @NotNull
  public List<SyncModuleModels> getModuleModels() {
    return ImmutableList.copyOf(myModuleModels);
  }

  /**
   * @return A list of {@link GlobalLibraryMap} retrieved from android plugin.
   * <br/>
   * The returned list could be empty in two cases:
   * <ol>
   * <li>The version of Android plugin doesn't support GlobalLibraryMap. i.e. pre 3.0.0 plugin.</li>
   * <li>There is no Android module in this project.</li>
   * </ol>
   */
  @NotNull
  public List<GlobalLibraryMap> getGlobalLibraryMap() {
    return ImmutableList.copyOf(myGlobalLibraryMaps);
  }

  /**
   * @return the build identifier of root project.
   */
  @NotNull
  public BuildIdentifier getRootBuildId() {
    return myRootBuildId;
  }

  public static class Factory implements Serializable {
    @NotNull
    public SyncProjectModels create(@NotNull Set<Class<?>> extraAndroidModelTypes,
                                    @NotNull Set<Class<?>> extraJavaModelTypes,
                                    @NotNull SyncActionOptions options) {
      return new SyncProjectModels(extraAndroidModelTypes, extraJavaModelTypes, options);
    }
  }
}
