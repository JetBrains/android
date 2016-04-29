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
package com.android.tools.idea.gradle.structure.services;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.structure.services.DeveloperServiceBuildSystemOperations;
import com.android.tools.idea.structure.services.DeveloperServiceMetadata;
import com.android.tools.idea.templates.RepositoryUrlManager;
import com.google.common.collect.Lists;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.idea.gradle.util.Projects.isBuildWithGradle;

public class GradleOperations implements DeveloperServiceBuildSystemOperations {
  @Override
  public boolean canHandle(@NotNull Project project) {
    return isBuildWithGradle(project);
  }

  @Override
  public boolean containsAllDependencies(@NotNull Module module, @NotNull DeveloperServiceMetadata metadata) {
    // Consider ourselves installed if this service's dependencies are already found in the current
    // module.
    // TODO: Flesh this simplistic approach out more. We would like to have a way to say a service
    // isn't installed even if its dependency happens to be added to the project. For example,
    // multiple services might share a dependency but have additional settings that indicate some
    // are installed and others aren't.
    List<String> moduleDependencyNames = Lists.newArrayList();
    GradleBuildModel buildModel = GradleBuildModel.get(module);
    if (buildModel != null) {
      DependenciesModel dependenciesModel = buildModel.dependencies();
      if (dependenciesModel != null) {
        for (ArtifactDependencyModel dependency : dependenciesModel.artifacts()) {
          String name = dependency.name();
          moduleDependencyNames.add(name);
        }
      }
    }
    boolean allDependenciesFound = true;
    for (String serviceDependency : metadata.getDependencies()) {
      boolean thisDependencyFound = false;
      for (String moduleDependencyName : moduleDependencyNames) {
        if (serviceDependency.contains(moduleDependencyName)) {
          thisDependencyFound = true;
          break;
        }
      }

      if (!thisDependencyFound) {
        allDependenciesFound = false;
        break;
      }
    }
    return allDependenciesFound;
  }

  @Override
  public boolean isServiceInstalled(@NotNull Module module, @NotNull DeveloperServiceMetadata metadata) {
    GradleBuildModel buildModel = GradleBuildModel.get(module);
    if (buildModel != null) {
      DependenciesModel dependenciesModel = buildModel.dependencies();
      if (dependenciesModel != null) {
        for (ArtifactDependencyModel dependency : dependenciesModel.artifacts()) {
          ArtifactDependencySpec spec = dependency.getSpec();
          for (String dependencyValue : metadata.getDependencies()) {
            if (spec.equals(ArtifactDependencySpec.create(dependencyValue))) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  @Override
  public void removeDependencies(@NotNull Module module, @NotNull DeveloperServiceMetadata metadata) {
    final GradleBuildModel buildModel = GradleBuildModel.get(module);
    if (buildModel != null) {
      boolean dependenciesChanged = false;

      DependenciesModel dependenciesModel = buildModel.dependencies();
      if (dependenciesModel != null) {
        for (ArtifactDependencyModel dependency : dependenciesModel.artifacts()) {
          ArtifactDependencySpec spec = dependency.getSpec();
          for (String dependencyValue : metadata.getDependencies()) {
            if (spec.equals(ArtifactDependencySpec.create(dependencyValue))) {
              dependenciesModel.remove(dependency);
              dependenciesChanged = true;
              break;
            }
          }
        }
      }

      final Project project = module.getProject();
      if (dependenciesChanged) {
        new WriteCommandAction.Simple(project, "Uninstall " + metadata.getName()) {
          @Override
          public void run() {
            buildModel.applyChanges();
          }
        }.execute();
      }
      GradleProjectImporter.getInstance().requestProjectSync(project, null);
    }
  }

  @Override
  public void initializeServices(@NotNull Module module, @NotNull final Runnable initializationTask) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null;
    facet.addListener(new GradleSyncListener.Adapter() {
      @Override
      public void syncSucceeded(@NotNull Project project) {
        initializationTask.run();
      }
    });
  }

  @Override
  @NotNull
  public String getBuildSystemId() {
    return "Gradle";
  }

  /**
   * Note that this method currently only checks local repositories and does not do a network
   * fetch as part of resolving the highest version.
   *
   * TODO: Add network fetch as an option if necessary.
   */
  @Override
  @Nullable
  public String getHighestVersion(@NotNull String groupId, @NotNull String artifactId) {
    GradleCoordinate gradleCoordinate = new GradleCoordinate(groupId, artifactId, GradleCoordinate.PLUS_REV);
    return RepositoryUrlManager.get().resolveDynamicCoordinateVersion(gradleCoordinate, null);
  }
}
