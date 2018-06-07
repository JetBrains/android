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
package com.android.tools.idea.gradle.project.sync.ng.variantonly;

import com.android.annotations.VisibleForTesting;
import com.android.builder.model.*;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.GlobalLibraryMap;
import com.android.builder.model.level2.GraphItem;
import com.google.common.collect.ImmutableList;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import static com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId;
import static com.android.tools.idea.gradle.project.sync.ng.AndroidModule.MODULE_ARTIFACT_ADDRESS_PATTERN;
import static java.nio.file.Files.isSameFile;

public class VariantOnlyProjectModels implements Serializable {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 2L;

  @NotNull private final Map<String, VariantOnlyModuleModel> myModuleModelsById = new HashMap<>();
  @NotNull private final Map<File, GlobalLibraryMap> myLibraryMapsByBuildId = new HashMap<>();

  public void populate(@NotNull BuildController controller, @NotNull VariantOnlySyncOptions syncOptions) {
    populateModelsForModule(controller, syncOptions.myBuildId, syncOptions.myGradlePath, syncOptions.myVariantName);
    // Request for GlobalLibraryMap model at last, when all of other models have been built.
    populateGlobalLibraryMap(controller);
  }

  private void populateModelsForModule(@NotNull BuildController controller,
                                       @NotNull File buildId,
                                       @NotNull String gradlePath,
                                       @NotNull String variantName) {
    String moduleId = createUniqueModuleId(buildId, gradlePath);
    VariantOnlyModuleModel moduleModel = myModuleModelsById.get(moduleId);
    // The variant of this module has been requested before.
    if (moduleModel != null && moduleModel.containsVariant(variantName)) {
      return;
    }
    // This module has been requested with a different variant before, only request for Variant model.
    if (moduleModel != null) {
      syncAndAddVariant(moduleModel, variantName, controller);
      return;
    }
    // This module was not requested before, request for AndroidProject, Variant, and also process its dependency modules.
    // Find the GradleProject that represents current module.
    GradleProject gradleProject = findGradleProject(controller, buildId, gradlePath);
    if (gradleProject != null) {
      AndroidProject androidProject = controller.findModel(gradleProject, AndroidProject.class, ModelBuilderParameter.class,
                                                           parameter -> parameter.setShouldBuildVariant(false));
      if (androidProject != null) {
        moduleModel = new VariantOnlyModuleModel(androidProject, gradleProject, moduleId, buildId);
        myModuleModelsById.put(moduleId, moduleModel);
        Variant variant = syncAndAddVariant(moduleModel, variantName, controller);
        if (variant != null) {
          // Populate models for dependency modules.
          populateForDependencyModules(controller, variant);
        }
      }
    }
  }

  private void populateForDependencyModules(@NotNull BuildController controller, @NotNull Variant variant) {
    AndroidArtifact artifact = variant.getMainArtifact();
    Dependencies dependencies = artifact.getDependencies();
    if (!dependencies.getLibraries().isEmpty()) {
      populateForDependencyModules(controller, dependencies);
    }
    else {
      populateForDependencyModules(controller, artifact.getDependencyGraphs());
    }
  }

  private void populateForDependencyModules(@NotNull BuildController controller,
                                            @NotNull DependencyGraphs dependencyGraphs) {
    for (GraphItem item : dependencyGraphs.getCompileDependencies()) {
      String address = item.getArtifactAddress();
      Matcher matcher = MODULE_ARTIFACT_ADDRESS_PATTERN.matcher(address);
      if (matcher.matches()) {
        String dependencyBuildId = matcher.group(1);
        String projectPath = matcher.group(2);
        String variantToSelect = matcher.group(4);
        if (projectPath != null && dependencyBuildId != null && variantToSelect != null) {
          populateModelsForModule(controller, new File(dependencyBuildId), projectPath, variantToSelect);
        }
      }
    }
  }

  private void populateForDependencyModules(@NotNull BuildController controller, @NotNull Dependencies dependencies) {
    for (AndroidLibrary library : dependencies.getLibraries()) {
      String projectPath = library.getProject();
      String dependencyBuildId = library.getBuildId();
      String variantToSelect = library.getProjectVariant();
      if (projectPath != null && dependencyBuildId != null && variantToSelect != null) {
        populateModelsForModule(controller, new File(dependencyBuildId), projectPath, variantToSelect);
      }
    }
  }

  private void populateGlobalLibraryMap(@NotNull BuildController controller) {
    // Each included build has an instance of GlobalLibraryMap, so the model needs to be requested once for each included build.
    for (VariantOnlyModuleModel moduleModel : myModuleModelsById.values()) {
      File buildId = moduleModel.getBuildId();
      if (!myLibraryMapsByBuildId.containsKey(buildId)) {
        GlobalLibraryMap map = controller.findModel(moduleModel.getGradleProject(), GlobalLibraryMap.class);
        if (map != null) {
          myLibraryMapsByBuildId.put(buildId, map);
        }
      }
    }
  }

  @Nullable
  private static Variant syncAndAddVariant(@NotNull VariantOnlyModuleModel moduleModel,
                                           @NotNull String variantName,
                                           @NotNull BuildController controller) {
    GradleProject gradleProject = moduleModel.getGradleProject();
    Variant variant = controller.findModel(gradleProject, Variant.class, ModelBuilderParameter.class,
                                           parameter -> parameter.setVariantName(variantName));
    if (variant != null) {
      moduleModel.addVariant(variant);
    }
    return variant;
  }

  @Nullable
  private static GradleProject findGradleProject(@NotNull BuildController controller, @NotNull File buildId, @NotNull String gradlePath) {
    GradleBuild rootBuild = controller.getBuildModel();
    // Find the build that contains target module.
    GradleBuild gradleBuild = findGradleBuild(rootBuild, buildId);
    if (gradleBuild != null) {
      GradleProject gradleProject = controller.findModel(gradleBuild.getRootProject(), GradleProject.class);
      if (gradleProject != null) {
        // Find the GradleProject that represents target module.
        return findGradleProject(gradleProject, gradlePath);
      }
    }
    return null;
  }

  @Nullable
  private static GradleBuild findGradleBuild(@NotNull GradleBuild gradleBuild, @NotNull File buildId) {
    Path projectRootDir = gradleBuild.getBuildIdentifier().getRootDir().toPath();
    try {
      if (isSameFile(projectRootDir, buildId.toPath())) {
        return gradleBuild;
      }
    }
    catch (IOException e) {
      return null;
    }
    for (GradleBuild childBuild : gradleBuild.getIncludedBuilds()) {
      GradleBuild build = findGradleBuild(childBuild, buildId);
      if (build != null) {
        return build;
      }
    }
    return null;
  }

  @Nullable
  private static GradleProject findGradleProject(@NotNull GradleProject gradleProject, @NotNull String gradlePath) {
    if (gradleProject.getPath().equals(gradlePath)) {
      return gradleProject;
    }
    for (GradleProject child : gradleProject.getChildren()) {
      GradleProject project = findGradleProject(child, gradlePath);
      if (project != null) {
        return project;
      }
    }
    return null;
  }

  @NotNull
  public List<GlobalLibraryMap> getGlobalLibraryMap() {
    return ImmutableList.copyOf(myLibraryMapsByBuildId.values());
  }

  @NotNull
  public List<VariantOnlyModuleModel> getModuleModels() {
    return ImmutableList.copyOf(myModuleModelsById.values());
  }

  public static class VariantOnlyModuleModel implements Serializable {
    @NotNull private final AndroidProject myAndroidProject;
    @NotNull private final GradleProject myGradleProject;
    @NotNull private final String myModuleId;
    @NotNull private final File myBuildId;
    @NotNull private final Map<String, Variant> myVariantsByName = new HashMap<>();

    public VariantOnlyModuleModel(@NotNull AndroidProject androidProject,
                                  @NotNull GradleProject gradleProject,
                                  @NotNull String moduleId,
                                  @NotNull File buildId) {
      myAndroidProject = androidProject;
      myGradleProject = gradleProject;
      myModuleId = moduleId;
      myBuildId = buildId;
    }

    @NotNull
    public AndroidProject getAndroidProject() {
      return myAndroidProject;
    }

    @NotNull
    public GradleProject getGradleProject() {
      return myGradleProject;
    }

    @NotNull
    public String getModuleId() {
      return myModuleId;
    }

    @NotNull
    public File getBuildId() {
      return myBuildId;
    }

    @NotNull
    public List<Variant> getVariants() {
      return ImmutableList.copyOf(myVariantsByName.values());
    }

    private void addVariant(@NotNull Variant variant) {
      myVariantsByName.put(variant.getName(), variant);
    }

    @VisibleForTesting
    public boolean containsVariant(@NotNull String variantName) {
      return myVariantsByName.containsKey(variantName);
    }
  }
}
