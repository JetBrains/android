/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.prototype.model;

import com.android.builder.model.*;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ModuleDependencyModel;
import com.google.common.base.Objects;
import com.google.common.collect.*;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.ide.common.repository.GradleCoordinate.parseCoordinateString;
import static com.android.tools.idea.gradle.structure.prototype.model.Coordinates.areEqual;
import static com.android.tools.idea.gradle.util.GradleUtil.getModuleIcon;
import static com.intellij.icons.AllIcons.Nodes.Module;
import static com.intellij.openapi.util.text.StringUtil.capitalize;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

/**
 * Represents a merged view of the project model obtained from Gradle and the model obtained by parsing a module's build.gradle file.
 */
public class ModuleMergedModel {
  @NotNull private static final Map<String, String> ARTIFACT_TO_SCOPE_MAP = Maps.newHashMap();
  static {
    ARTIFACT_TO_SCOPE_MAP.put(AndroidProject.ARTIFACT_MAIN, "compile");
    ARTIFACT_TO_SCOPE_MAP.put(AndroidProject.ARTIFACT_ANDROID_TEST, "androidTest");
    ARTIFACT_TO_SCOPE_MAP.put(AndroidProject.ARTIFACT_UNIT_TEST, "testCompile");
  }

  @NotNull final GradleBuildModel buildModel;
  @NotNull final AndroidProject androidProject;
  @NotNull final Module module;

  @NotNull private final List<DependencyMergedModel> myDependencyModels = Lists.newArrayList();

  @Nullable
  public static ModuleMergedModel get(@NotNull Module module) {
    GradleBuildModel buildModel = GradleBuildModel.get(module);
    if (buildModel != null) {
      AndroidGradleModel androidModel = AndroidGradleModel.get(module);
      if (androidModel != null) {
        return new ModuleMergedModel(buildModel, androidModel.getAndroidProject(), module);
      }
    }
    return null;
  }

  private ModuleMergedModel(@NotNull GradleBuildModel buildModel, @NotNull AndroidProject androidProject, @NotNull Module module) {
    this.buildModel = buildModel;
    this.androidProject = androidProject;
    this.module = module;

    populate();
  }

  private void populate() {
    GradleArtifactDependencies artifactDependencies = new GradleArtifactDependencies();
    GradleModuleDependencies moduleDependencies = new GradleModuleDependencies();

    for (Variant variant : androidProject.getVariants()) {
      BaseArtifact mainArtifact = variant.getMainArtifact();
      collectDependencies(variant, mainArtifact, artifactDependencies, moduleDependencies);

      for (BaseArtifact artifact : variant.getExtraAndroidArtifacts()) {
        collectDependencies(variant, artifact, artifactDependencies, moduleDependencies);
      }

      for (BaseArtifact artifact : variant.getExtraJavaArtifacts()) {
        collectDependencies(variant, artifact, artifactDependencies, moduleDependencies);
      }
    }

    DependenciesModel dependenciesModel = buildModel.dependencies();
    if (dependenciesModel != null) {
      for (DependencyModel dependencyModel : dependenciesModel.all()) {
        if (dependencyModel instanceof ArtifactDependencyModel) {
          addDependency((ArtifactDependencyModel)dependencyModel, artifactDependencies);
        }
        else if (dependencyModel instanceof ModuleDependencyModel) {
          addDependency((ModuleDependencyModel)dependencyModel, moduleDependencies);
        }
      }
    }

    Set<GradleArtifactDependency> notFoundInBuildFile = artifactDependencies.getNotFoundInBuildFile();
    if (!notFoundInBuildFile.isEmpty()) {
      for (GradleArtifactDependency dependency : notFoundInBuildFile) {
        ArtifactDependencyMergedModel model = ArtifactDependencyMergedModel.create(this, dependency);
        myDependencyModels.add(model);
      }
    }
  }

  private void collectDependencies(@NotNull Variant variant,
                                   @NotNull BaseArtifact artifact,
                                   @NotNull GradleArtifactDependencies artifactDependencies,
                                   @NotNull GradleModuleDependencies moduleDependencies) {
    String scopeName = getScopeName(artifact);
    if (isEmpty(scopeName)) {
      return;
    }
    Dependencies dependencies = artifact.getDependencies();
    for (AndroidLibrary library : dependencies.getLibraries()) {
      GradleArtifactDependency artifactDependency = GradleArtifactDependency.create(library);
      if (artifactDependency != null) {
        artifactDependencies.add(variant, scopeName, artifactDependency);
      }
      else {
        GradleModuleDependency moduleDependency = GradleModuleDependency.create(library, module.getProject());
        if (moduleDependency != null) {
          moduleDependencies.add(variant, scopeName, moduleDependency);
        }
      }
    }
    for (JavaLibrary library : dependencies.getJavaLibraries()) {
      GradleArtifactDependency artifactDependency = GradleArtifactDependency.create(library);
      if (artifactDependency != null) {
        artifactDependencies.add(variant, scopeName, artifactDependency);
      }
    }
  }

  @Nullable
  private static String getScopeName(@NotNull BaseArtifact artifact) {
    return ARTIFACT_TO_SCOPE_MAP.get(artifact.getName());
  }

  private void addDependency(@NotNull ArtifactDependencyModel parsedDependency, @NotNull GradleArtifactDependencies artifactDependencies) {
    GradleCoordinate parsedCoordinate = parseCoordinateString(parsedDependency.getSpec().compactNotation());
    if (parsedCoordinate != null) {
      String configurationName = parsedDependency.configurationName();
      Collection<GradleArtifactDependency> dependenciesInConfiguration = artifactDependencies.getByConfigurationName(configurationName);
      if (!dependenciesInConfiguration.isEmpty()) {
        List<GradleArtifactDependency> fromGradleModel = Lists.newArrayList();
        for (GradleArtifactDependency dependency : dependenciesInConfiguration) {
          GradleCoordinate logicalCoordinate = dependency.coordinate;
          if (areEqual(logicalCoordinate, parsedCoordinate)) {
            fromGradleModel.add(dependency);
            artifactDependencies.markAsFoundInBuildFile(dependency);
          }
        }
        if (!fromGradleModel.isEmpty()) {
          ArtifactDependencyMergedModel model = ArtifactDependencyMergedModel.create(this, fromGradleModel, parsedDependency);
          if (model != null) {
            myDependencyModels.add(model);
          }
          return;
        }
      }
    }
    ArtifactDependencyMergedModel model = ArtifactDependencyMergedModel.create(this, parsedDependency);
    if (model != null) {
      myDependencyModels.add(model);
    }
  }

  private void addDependency(@NotNull ModuleDependencyModel parsedDependency, @NotNull GradleModuleDependencies moduleDependencies) {
    String parsedPath = parsedDependency.path();
    if (isNotEmpty(parsedPath)) {
      String configurationName = parsedDependency.configurationName();
      Collection<GradleModuleDependency> dependenciesInConfiguration = moduleDependencies.getByConfigurationName(configurationName);
      if (!dependenciesInConfiguration.isEmpty()) {
        List<GradleModuleDependency> fromGradleModel = Lists.newArrayList();
        for (GradleModuleDependency dependency : dependenciesInConfiguration) {
          String logicalPath = dependency.gradlePath;
          if (Objects.equal(logicalPath, parsedPath)) {
            fromGradleModel.add(dependency);
            moduleDependencies.markAsFoundInBuildFile(dependency);
          }
        }
        if (!fromGradleModel.isEmpty()) {
          ModuleDependencyMergedModel model = ModuleDependencyMergedModel.create(this, fromGradleModel, parsedDependency);
          if (model != null) {
            myDependencyModels.add(model);
          }
        }
      }
    }
  }

  @NotNull
  public String getModuleName() {
    return module.getName();
  }

  @NotNull
  public Icon getIcon() {
    if (!module.isDisposed()) {
      return getModuleIcon(module);
    }
    return Module;
  }

  @NotNull
  public AndroidProject getAndroidProject() {
    return androidProject;
  }

  @NotNull
  public List<DependencyMergedModel> getDependencies() {
    return myDependencyModels;
  }

  @NotNull
  public List<DependencyMergedModel> getEditableDependencies() {
    List<DependencyMergedModel> dependencies = Lists.newArrayList();
    for (DependencyMergedModel model : myDependencyModels) {
      if (model.isInBuildFile()) {
        dependencies.add(model);
      }
    }
    return dependencies;
  }

  /**
   * Collection of artifact dependencies obtained from the Gradle model.
   */
  private static class GradleArtifactDependencies {
    @NotNull private final Multimap<String, GradleArtifactDependency> myDependenciesByScope = LinkedHashMultimap.create();
    @NotNull private final Map<String, GradleArtifactDependency> myDependenciesByCoordinate = Maps.newHashMap();
    @NotNull private final Set<GradleArtifactDependency> myDependencies = Sets.newHashSet(myDependenciesByCoordinate.values());

    void add(@NotNull Variant variant, @NotNull String scopeName, @NotNull GradleArtifactDependency dependency) {
      GradleArtifactDependency existing = myDependenciesByCoordinate.get(dependency.toString());
      if (existing != null) {
        dependency = existing;
      }
      else {
        myDependenciesByCoordinate.put(dependency.toString(), dependency);
      }
      dependency.addContainer(variant);
      myDependenciesByScope.put(scopeName, dependency);
      scopeName = capitalize(scopeName);
      for (String flavor : variant.getProductFlavors()) {
        String flavorScope = flavor + scopeName;
        myDependenciesByScope.put(flavorScope, dependency);
      }
      myDependenciesByScope.put(variant.getBuildType() + scopeName, dependency);
      myDependencies.add(dependency);
    }

    @NotNull
    Collection<GradleArtifactDependency> getByConfigurationName(String configurationName) {
      return myDependenciesByScope.get(configurationName);
    }

    /**
     * Indicates that the given dependency (obtained from the Gradle model) has been found in the build.gradle file.
     */
    void markAsFoundInBuildFile(GradleArtifactDependency dependency) {
      myDependencies.remove(dependency);
    }

    @NotNull
    Set<GradleArtifactDependency> getNotFoundInBuildFile() {
      return myDependencies;
    }
  }

  private static class GradleModuleDependencies {
    @NotNull private final Multimap<String, GradleModuleDependency> myDependenciesByScope = LinkedHashMultimap.create();
    @NotNull private final Map<String, GradleModuleDependency> myDependenciesByGradlePath = Maps.newHashMap();
    @NotNull private final Set<GradleModuleDependency> myDependencies = Sets.newHashSet(myDependenciesByGradlePath.values());

    void add(@NotNull Variant variant, @NotNull String scopeName, @NotNull GradleModuleDependency dependency) {
      GradleModuleDependency existing = myDependenciesByGradlePath.get(dependency.toString());
      if (existing != null) {
        dependency = existing;
      }
      else {
        myDependenciesByGradlePath.put(dependency.toString(), dependency);
      }
      dependency.addContainer(variant);
      myDependenciesByScope.put(scopeName, dependency);
      scopeName = capitalize(scopeName);
      for (String flavor : variant.getProductFlavors()) {
        String flavorScope = flavor + scopeName;
        myDependenciesByScope.put(flavorScope, dependency);
      }
      myDependenciesByScope.put(variant.getBuildType() + scopeName, dependency);
      myDependencies.add(dependency);

    }

    @NotNull
    Collection<GradleModuleDependency> getByConfigurationName(@NotNull  String configurationName) {
      return myDependenciesByScope.get(configurationName);
    }

    void markAsFoundInBuildFile(@NotNull GradleModuleDependency dependency) {
      myDependencies.remove(dependency);
    }
  }
}
