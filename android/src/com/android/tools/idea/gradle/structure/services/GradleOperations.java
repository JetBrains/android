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
import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.BuildFileStatement;
import com.android.tools.idea.gradle.parser.Dependency;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.structure.services.DeveloperServiceBuildSystemOperations;
import com.android.tools.idea.structure.services.DeveloperServiceMetadata;
import com.android.tools.idea.templates.RepositoryUrlManager;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
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
    GradleBuildFile gradleBuildFile = GradleBuildFile.get(module);
    if (gradleBuildFile != null) {
      for (BuildFileStatement dependency : gradleBuildFile.getDependencies()) {
        if (dependency instanceof Dependency) {
          Object data = ((Dependency)dependency).data;
          if (data instanceof String) {
            String dependencyString = (String)data;
            List<String> dependencyParts = Lists.newArrayList(Splitter.on(':').split(dependencyString));
            if (dependencyParts.size() == 3) {
              // From the dependency URL "group:name:version" string - we only care about "name"
              // We ignore the version, as a service may be installed using an older version
              // TODO: Handle "group: 'com.android.support', name: 'support-v4', version: '21.0.+'" format also
              // See also GradleDetector#getNamedDependency
              moduleDependencyNames.add(dependencyParts.get(1));
            }
          }
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
    GradleBuildFile gradleFile = GradleBuildFile.get(module);
    if (gradleFile != null) {
      List<BuildFileStatement> dependencies = gradleFile.getDependencies();
      for (BuildFileStatement statement : dependencies) {
        if (!(statement instanceof Dependency)) {
          continue;
        }

        Dependency dependency = (Dependency)statement;
        for (String dependencyValue : metadata.getDependencies()) {
          if (dependency.getValueAsString().equals(dependencyValue)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Override
  public void removeDependencies(@NotNull Module module, @NotNull DeveloperServiceMetadata metadata) {
    final GradleBuildFile gradleFile = GradleBuildFile.get(module);
    if (gradleFile != null) {
      boolean dependenciesChanged = false;

      final List<BuildFileStatement> dependencies = gradleFile.getDependencies();
      Iterator<BuildFileStatement> iterator = dependencies.iterator();
      while (iterator.hasNext()) {
        BuildFileStatement statement = iterator.next();
        if (!(statement instanceof Dependency)) {
          continue;
        }

        Dependency dependency = (Dependency)statement;
        for (String dependencyValue : metadata.getDependencies()) {
          if (dependency.getValueAsString().equals(dependencyValue)) {
            iterator.remove();
            dependenciesChanged = true;
            break;
          }
        }
      }

      final Project project = module.getProject();
      if (dependenciesChanged) {
        new WriteCommandAction.Simple(project, "Uninstall " + metadata.getName()) {
          @Override
          public void run() {
            gradleFile.setValue(BuildFileKey.DEPENDENCIES, dependencies);
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
