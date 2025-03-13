/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.dependencies;

import static com.android.SdkConstants.SUPPORT_LIB_GROUP_ID;
import static com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.IMPLEMENTATION;

import com.android.ide.common.gradle.Component;
import com.android.ide.common.gradle.Dependency;
import com.android.ide.common.gradle.RichVersion;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.gradle.repositories.RepositoryUrlManager;
import com.android.tools.idea.projectsystem.gradle.GradleModuleSystem;
import com.google.common.base.Objects;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.kotlin.idea.base.facet.KotlinFacetUtils;

public class GradleDependencyManager {
  private static final String ADD_DEPENDENCY = "Add Dependency";

  @NotNull
  public static GradleDependencyManager getInstance(@NotNull Project project) {
    return project.getService(GradleDependencyManager.class);
  }

  /**
   * Returns the dependencies that are NOT defined in the build files.
   * <p>
   * Note: A dependency is still regarded as missing even if it's available
   * by a transitive dependency.
   * Also: the version of the dependency is disregarded.
   *
   * @param module       the module to check dependencies in
   * @param dependencies the dependencies of interest.
   * @return a list of the dependencies NOT defined in the build files.
   */
  @TestOnly
  public List<Dependency> findMissingDependencies(@NotNull Module module, @NotNull Iterable<Dependency> dependencies) {
    Project project = module.getProject();
    GradleAndroidModel gradleModel = GradleAndroidModel.get(module);
    GradleBuildModel buildModel = ProjectBuildModel.get(project).getModuleBuildModel(module);

    if (gradleModel == null && buildModel == null) {
      return Collections.emptyList();
    }

    List<ArtifactDependencyModel> compileDependencies = buildModel != null ? buildModel.dependencies().artifacts() : null;
    String declaredAppCompatVersion = getDeclaredAppCompatVersion(compileDependencies);

    List<Dependency> missingLibraries = new ArrayList<>();
    for (Dependency dependency : dependencies) {

      if (dependency.getGroup() == null) continue;

      Optional<Dependency> resolvedCoordinate = resolveCoordinate(project, dependency, declaredAppCompatVersion);
      Dependency finalDependency = resolvedCoordinate.orElse(dependency);

      boolean dependencyFound = compileDependencies != null &&
                                compileDependencies.stream()
                                  .anyMatch(d -> Objects.equal(d.group().toString(), finalDependency.getGroup()) &&
                                                 d.name().forceString().equals(finalDependency.getName()));
      if (!dependencyFound) {
        missingLibraries.add(finalDependency);
      }
    }

    return missingLibraries;
  }

  private static String getDeclaredAppCompatVersion(List<ArtifactDependencyModel> compileDependencies) {
    // Record current version of support library; if used, prefer that for other dependencies
    // (e.g. if you're using appcompat-v7 version 25.3.1, and you drag in a recyclerview-v7
    // library, we should also use 25.3.1, not whatever happens to be latest
    String appCompatVersion = null;
    if (compileDependencies != null) {
      for (ArtifactDependencyModel dependency : compileDependencies) {
        if (Objects.equal(SUPPORT_LIB_GROUP_ID, dependency.group().toString()) &&
            !Objects.equal("multidex", dependency.name().forceString())) {
          String s = dependency.version().toString();
          if (s != null) {
            appCompatVersion = s;
          }
          break;
        }
      }
    }
    return appCompatVersion;
  }

  private Optional<Dependency> resolveCoordinate(Project project, Dependency dependency, String declaredAppCompatVersion) {
    RepositoryUrlManager manager = RepositoryUrlManager.get();
    Component resolvedComponent = manager.resolveDependency(dependency, project, null);

    // If we're adding a support library with a non-singleton version, and we already have a declared
    // support library version, use that declared version for the new support library too to keep them
    // all consistent.
    String group = dependency.getGroup();
    if (declaredAppCompatVersion != null
        && SUPPORT_LIB_GROUP_ID.equals(group)
        && dependency.getExplicitSingletonVersion() == null
        // The only library in groupId=SUPPORT_LIB_GROUP_ID which doesn't follow the normal version numbering scheme
        && !dependency.getName().equals("multidex")) {
      return Optional.of(new Dependency(group, dependency.getName(), RichVersion.parse(declaredAppCompatVersion), null, null));
    }

    if (resolvedComponent == null) {
      return Optional.of(dependency);
    }
    else {
      return Optional.of(new Dependency(resolvedComponent.getGroup(), resolvedComponent.getName(), RichVersion.require(resolvedComponent.getVersion()), null, null));
    }
  }

  /**
   * Add all the specified dependencies to the module without triggering a sync afterwards.
   * Adding a dependency that already exists will result in a no-op.
   *
   * @param module       the module to add dependencies to
   * @param dependencies the dependencies of interest
   * @return true if the dependencies were successfully added or were already present in the module.
   */
  public boolean addDependencies(@NotNull Module module, @NotNull Iterable<Dependency> dependencies) {
    return addDependenciesInTransaction(module, dependencies, null);
  }

  /**
   * Like {@link #addDependencies(Module, Iterable)} but allows you to customize the configuration
   * name of the inserted dependencies.
   *
   * @param module       the module to add dependencies to
   * @param dependencies the dependencies of interest
   * @param nameMapper   a factory to produce configuration names and artifact specs
   * @return true if the dependencies were successfully added or were already present in the module.
   */
  public boolean addDependencies(
    @NotNull Module module,
    @NotNull Iterable<Dependency> dependencies,
    @Nullable ConfigurationNameMapper nameMapper) {
    return addDependenciesInTransaction(module, dependencies, nameMapper);
  }

  /**
   * Updates any coordinates to the versions specified in the dependencies list.
   * In case module has a reference to catalog file, dependency will be updated there.
   * For example, if you pass it [com.android.support.constraint:constraint-layout:1.0.0-alpha2],
   * it will find any constraint layout occurrences of 1.0.0-alpha1 and replace them with 1.0.0-alpha2.
   */
  public boolean updateLibrariesToVersion(@NotNull Module module,
                                          @NotNull List<Dependency> dependencies) {
    ProjectBuildModel projectBuildModel = ProjectBuildModel.get(module.getProject());
    GradleBuildModel buildModel = projectBuildModel.getModuleBuildModel(module);
    if (buildModel == null) {
      return false;
    }
    updateDependenciesInTransaction(projectBuildModel, buildModel, module, dependencies);
    return true;
  }

  private boolean addDependenciesInTransaction(@NotNull Module module,
                                               @NotNull Iterable<Dependency> dependencies,
                                               @Nullable ConfigurationNameMapper nameMapper) {

    Project project = module.getProject();
    ProjectBuildModel projectBuildModel = ProjectBuildModel.get(project);
    GradleBuildModel buildModel = projectBuildModel.getModuleBuildModel(module);
    DependenciesInserter helper = DependenciesHelper.withModel(projectBuildModel);
    if (buildModel == null) {
      return false;
    }

    String sourceSet;
    if (KotlinFacetUtils.isMultiPlatformModule(module)) {
      sourceSet = GradleModuleSystem.getGradleSourceSetName(module);
    }
    else {
      sourceSet = null;
    }

    List<ArtifactDependencyModel> compileDependencies = buildModel.dependencies().artifacts();
    String declaredAppCompatVersion = getDeclaredAppCompatVersion(compileDependencies);

    WriteCommandAction.writeCommandAction(project).withName(ADD_DEPENDENCY).run(() -> {
      for (Dependency dependency : dependencies) {
        String name = IMPLEMENTATION;
        if (nameMapper != null) {
          name = nameMapper.mapName(module, name, dependency);
        }
        Optional<Dependency> resolvedCoordinate = resolveCoordinate(project, dependency, declaredAppCompatVersion);
        Dependency finalDependency = resolvedCoordinate.orElse(dependency);
        String depString = finalDependency.toIdentifier();
        if (depString == null) return;

        if (sourceSet != null) helper.addDependency(name, depString, buildModel, sourceSet);
        else helper.addDependency(name, depString, buildModel);
      }
      projectBuildModel.applyChanges();
    });
    return true;
  }

  private static void updateDependenciesInTransaction(@NotNull ProjectBuildModel projectBuildModel,
                                                      @NotNull GradleBuildModel buildModel,
                                                      @NotNull Module module,
                                                      @NotNull List<Dependency> dependencies) {
    assert !dependencies.isEmpty();

    Project project = module.getProject();
    WriteCommandAction.writeCommandAction(project).withName(ADD_DEPENDENCY)
      .run(() -> {
        DependenciesInserter helper = DependenciesHelper.withModel(projectBuildModel);

        for (Dependency dependency : dependencies) {
          helper.updateDependencyVersion(dependency, buildModel);
        }
        projectBuildModel.applyChanges();
      });
  }

}
