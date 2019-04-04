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

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.FN_BUILD_GRADLE_KTS;
import static java.util.Objects.requireNonNull;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.level2.GlobalLibraryMap;
import com.android.java.model.GradlePluginModel;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.gradle.model.KotlinProject;

public class SyncProjectModels implements Serializable {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 4L;

  @NotNull private final Set<Class<?>> myExtraAndroidModelTypes;
  @NotNull private final Set<Class<?>> myExtraJavaModelTypes;
  @NotNull private final SyncActionOptions myOptions;
  @NotNull private final SelectedVariantChooser myVariantChooser;

  // List of SyncModuleModels for modules in root build and included builds.
  @NotNull private final List<SyncModuleModels> myModuleModels = new ArrayList<>();
  @NotNull private final List<GlobalLibraryMap> myGlobalLibraryMaps = new ArrayList<>();
  private BuildIdentifier myRootBuildId;
  private String myProjectName;

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
    // set project name used by Gradle.
    myProjectName = rootBuild.getRootProject().getName();
    List<GradleBuild> gradleBuilds = new ArrayList<>();
    // add the root builds.
    gradleBuilds.add(rootBuild);
    // add the included builds.
    gradleBuilds.addAll(rootBuild.getIncludedBuilds());

    // Fail early if Kotlin plugin is applied to any of the sub-projects, and they don't contain a KotlinProject model.
    // This implies that the Gradle project applies a version of the Kotlin plugin that doesn't support the new Sync models.
    for (GradleBuild gradleBuild : gradleBuilds) {
      GradleProject gradleProject = controller.findModel(gradleBuild.getRootProject(), GradleProject.class);

      failIfKotlinPluginAppliedAndKotlinModelIsMissing(controller, gradleProject);
      // fail if any project contains buildSrc module.
      if (gradleProject != null && hasBuildSrcModule(gradleProject.getProjectDirectory().getPath())) {
        throw new NewGradleSyncNotSupportedException("containing buildSrc module");
      }
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
    // Request for GlobalLibraryMap model at last, when all of other models have been built.
    populateGlobalLibraryMap(controller);
  }

  /**
   * Returns {@code true} if the given path has subfolder named "buildSrc", and buildSrc folder contains
   * build.gradle or build.gradle.kts file.
   *
   * @param projectPath directory of root project
   */
  public static boolean hasBuildSrcModule(@NotNull String projectPath) {
    File buildSrcDir = new File(projectPath, "buildSrc");
    File buildFile = new File(buildSrcDir, FN_BUILD_GRADLE);
    File ktsBuildFile = new File(buildSrcDir, FN_BUILD_GRADLE_KTS);
    return buildFile.isFile() || ktsBuildFile.isFile();
  }

  private void failIfKotlinPluginAppliedAndKotlinModelIsMissing(@NotNull BuildController controller,
                                                                @Nullable GradleProject gradleProject) {
    if (gradleProject == null) {
      return;
    }

    GradlePluginModel pluginModel = controller.findModel(gradleProject, GradlePluginModel.class);
    // We MUST NOT query for the Kotlin model as doing so will throw a java.util.NoSuchElementException: List is empty.
    // This is due to a bug in the model builder. When there are no variants Gradle has no tasks, the model builder does not
    // handle this case correctly.
    // Here we check the GradlePluginModel to see if this is the case.
    if (pluginModel != null) {
      if (pluginModel.areVariantsEmpty()) {
        myExtraAndroidModelTypes.remove(KotlinProject.class);
        return;
      }

      KotlinProject kotlinProject = controller.findModel(gradleProject, KotlinProject.class);
      if ((kotlinProject == null && isKotlinProject(pluginModel)) || isMPPProject(pluginModel)) {
        throw new NewGradleSyncNotSupportedException("containing Kotlin modules using an unsupported plugin version");
      }
    }

    for (GradleProject child : gradleProject.getChildren()) {
      failIfKotlinPluginAppliedAndKotlinModelIsMissing(controller, child);
    }
  }

  private static boolean isKotlinProject(@NotNull GradlePluginModel pluginModel) {
    return pluginModel.getGradlePluginList().stream().anyMatch(p -> p.startsWith("org.jetbrains.kotlin"));
  }

  private static boolean isMPPProject(@NotNull GradlePluginModel pluginModel) {
    return pluginModel.getGradlePluginList().stream().anyMatch(p -> p.startsWith("org.jetbrains.kotlin.multiplatform"));
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

  /**
   * @return the name of root project assigned by Gradle.
   */
  @NotNull
  public String getProjectName() {
    return myProjectName;
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
