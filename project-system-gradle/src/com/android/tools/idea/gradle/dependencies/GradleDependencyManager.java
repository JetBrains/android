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
import static com.android.ide.common.repository.VersionCatalogNamingUtilKt.pickLibraryVariableName;
import static com.android.tools.idea.gradle.dependencies.AddDependencyPolicy.calculateAddDependencyPolicy;
import static com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.COMPILE;
import static com.android.tools.idea.gradle.dsl.api.settings.VersionCatalogModel.DEFAULT_CATALOG_NAME;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_GRADLEDEPENDENCY_ADDED;
import static com.intellij.openapi.roots.ModuleRootModificationUtil.updateModel;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogModel;
import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogsModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.api.catalog.GradleVersionCatalogLibraries;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.LibraryDeclarationSpec;
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.repositories.RepositoryUrlManager;
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.google.common.base.Objects;
import com.google.wireless.android.sdk.stats.GradleSyncStats;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class GradleDependencyManager {
  private static final String ADD_DEPENDENCY = "Add Dependency";

  @NotNull
  public static GradleDependencyManager getInstance(@NotNull Project project) {
    return project.getService(GradleDependencyManager.class);
  }

  static class CatalogDependenciesInfo {
    public List<GradleCoordinate> missingLibraries = new ArrayList<>();
    public List<Pair<String, GradleCoordinate>> matchedCoordinates = new ArrayList<>();
  }


  protected CatalogDependenciesInfo computeCatalogDependenciesInfo(@NotNull Project project,
                                                                   @NotNull Iterable<GradleCoordinate> dependencies,
                                                                   GradleVersionCatalogModel catalogModel) {
    GradleBuildModel buildModel = ProjectBuildModel.get(project).getProjectBuildModel();
    List<ArtifactDependencyModel> compileDependencies = buildModel != null ? buildModel.dependencies().artifacts() : null;

    String appCompatVersion = getAppCompatVersion(compileDependencies);
    CatalogDependenciesInfo searchResult = new CatalogDependenciesInfo();
    GradleVersionCatalogLibraries libraries = catalogModel.libraryDeclarations();
    for (GradleCoordinate coordinate : dependencies) {
      if (coordinate.getGroupId() == null || coordinate.getArtifactId() == null) continue;
      Optional<GradleCoordinate> resolvedCoordinate = resolveCoordinate(project, coordinate, appCompatVersion);
      GradleCoordinate finalCoordinate = resolvedCoordinate.orElse(coordinate);
      if (libraries == null) {
        searchResult.missingLibraries.add(finalCoordinate);
      }
      else {
        Optional<Pair<String, GradleCoordinate>> maybeCoordinate = libraries.getAll().entrySet().stream()
          .filter(entry -> {
            LibraryDeclarationSpec spec = entry.getValue().getSpec();
            return (Objects.equal(spec.getGroup(), coordinate.getGroupId()) &&
                    Objects.equal(spec.getName(), coordinate.getArtifactId()));
          }).map(dep -> new Pair<>(dep.getKey(), coordinate)).findFirst();
        if (maybeCoordinate.isEmpty()) {
          searchResult.missingLibraries.add(finalCoordinate);
        }
        else {
          searchResult.matchedCoordinates.add(maybeCoordinate.get());
        }
      }
    }
    return searchResult;
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

  @NotNull
  public List<GradleCoordinate> findMissingDependencies(@NotNull Module module, @NotNull Iterable<GradleCoordinate> dependencies) {
    Project project = module.getProject();
    AndroidModuleModel gradleModel = AndroidModuleModel.get(module);
    GradleBuildModel buildModel = ProjectBuildModel.get(project).getModuleBuildModel(module);

    if (gradleModel == null && buildModel == null) {
      return Collections.emptyList();
    }

    List<ArtifactDependencyModel> compileDependencies = buildModel != null ? buildModel.dependencies().artifacts() : null;
    String appCompatVersion = getAppCompatVersion(compileDependencies);

    List<GradleCoordinate> missingLibraries = new ArrayList<>();
    for (GradleCoordinate coordinate : dependencies) {

      if (coordinate.getGroupId() == null || coordinate.getArtifactId() == null) continue;

      Optional<GradleCoordinate> resolvedCoordinate = resolveCoordinate(project, coordinate, appCompatVersion);
      GradleCoordinate finalCoordinate = resolvedCoordinate.orElse(coordinate);

      boolean dependencyFound = compileDependencies != null &&
                                compileDependencies.stream()
                                  .anyMatch(d -> Objects.equal(d.group().toString(), finalCoordinate.getGroupId()) &&
                                                 d.name().forceString().equals(finalCoordinate.getArtifactId()));
      if (!dependencyFound) {
        missingLibraries.add(finalCoordinate);
      }
    }

    return missingLibraries;
  }

  private static String getAppCompatVersion(List<ArtifactDependencyModel> compileDependencies) {
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

  private Optional<GradleCoordinate> resolveCoordinate(Project project, GradleCoordinate coordinate, String appCompatVersion) {
    RepositoryUrlManager manager = RepositoryUrlManager.get();
    String groupId = coordinate.getGroupId();
    String artifactId = coordinate.getArtifactId();

    GradleCoordinate resolvedCoordinate = manager.resolveDynamicCoordinate(coordinate, project, null);

    // If we're adding a support library with a dynamic version (+), and we already have a resolved
    // support library version, use that specific version for the new support library too to keep them
    // all consistent.
    if (appCompatVersion != null
        && coordinate.acceptsGreaterRevisions() && SUPPORT_LIB_GROUP_ID.equals(groupId)
        // The only library in groupId=SUPPORT_LIB_GROUP_ID which doesn't follow the normal version numbering scheme
        && !artifactId.equals("multidex")) {
      resolvedCoordinate = GradleCoordinate.parseCoordinateString(groupId + ":" + artifactId + ":" + appCompatVersion);
    }

    return Optional.of((resolvedCoordinate != null) ? resolvedCoordinate : coordinate);
  }

  /**
   * Add all the specified dependencies to the module. Adding a dependency that already exists will result in a no-op.
   * A sync will be triggered immediately after a successful addition (e.g. [dependencies] contains a dependency that
   * doesn't already exist and is therefore added); and caller may supply a callback to determine when the requested
   * dependencies have been added (this make take several seconds).
   *
   * @param module       the module to add dependencies to
   * @param dependencies the dependencies of interest
   * @return true if the dependencies were successfully added or were already present in the module
   */
  @TestOnly
  public boolean addDependenciesAndSync(@NotNull Module module,
                                        @NotNull Iterable<GradleCoordinate> dependencies) {
    AddDependencyPolicy policy = calculateAddDependencyPolicy(ProjectBuildModel.get(module.getProject()));
    boolean result = addDependenciesInTransaction(module, dependencies, policy, null);
    requestProjectSync(module.getProject(), TRIGGER_GRADLEDEPENDENCY_ADDED);
    return result;
  }

  /**
   * Add all the specified dependencies to the module without triggering a sync afterwards.
   * Adding a dependency that already exists will result in a no-op.
   *
   * @param module       the module to add dependencies to
   * @param dependencies the dependencies of interest
   * @return true if the dependencies were successfully added or were already present in the module.
   */
  public boolean addDependenciesWithoutSync(@NotNull Module module, @NotNull Iterable<GradleCoordinate> dependencies) {
    AddDependencyPolicy policy = calculateAddDependencyPolicy(ProjectBuildModel.get(module.getProject()));
    return addDependenciesInTransaction(module, dependencies, policy, null);
  }

  /**
   * Like {@link #addDependenciesWithoutSync(Module, Iterable)} but allows you to customize the configuration
   * name of the inserted dependencies.
   *
   * @param module       the module to add dependencies to
   * @param dependencies the dependencies of interest
   * @param nameMapper   a factory to produce configuration names and artifact specs
   * @return true if the dependencies were successfully added or were already present in the module.
   */
  public boolean addDependenciesWithoutSync(
    @NotNull Module module,
    @NotNull Iterable<GradleCoordinate> dependencies,
    @Nullable ConfigurationNameMapper nameMapper) {
    AddDependencyPolicy policy = calculateAddDependencyPolicy(ProjectBuildModel.get(module.getProject()));
    return addDependenciesInTransaction(module, dependencies, policy,nameMapper);
  }

  /**
   * Updates any coordinates to the versions specified in the dependencies list.
   * For example, if you pass it [com.android.support.constraint:constraint-layout:1.0.0-alpha2],
   * it will find any constraint layout occurrences of 1.0.0-alpha1 and replace them with 1.0.0-alpha2.
   */
  public boolean updateLibrariesToVersion(@NotNull Module module,
                                          @NotNull List<GradleCoordinate> dependencies) {
    // TODO upgrade to work seamlessly with version catalog
    GradleBuildModel buildModel = ProjectBuildModel.get(module.getProject()).getModuleBuildModel(module);
    if (buildModel == null) {
      return false;
    }
    updateDependenciesInTransaction(buildModel, module, dependencies);
    return true;
  }

  private static List<Pair<String, GradleCoordinate>> addCatalogLibraries(@NotNull GradleVersionCatalogModel catalogModel,
                                                                          @NotNull List<GradleCoordinate> coordinates) {
    List<Pair<String, GradleCoordinate>> addedCoordinates = new ArrayList<>();

    GradleVersionCatalogLibraries libraries = catalogModel.libraryDeclarations();
    Set<String> names = libraries.getAllAliases();
    for (GradleCoordinate coordinate : coordinates) {
      String alias = pickLibraryVariableName(coordinate, false, names);
      libraries.addDeclaration(alias, coordinate.toString());
      names.add(alias);
      addedCoordinates.add(new Pair<>(alias, coordinate));
    }
    return addedCoordinates;
  }

  private static void addCatalogReferences(@NotNull GradleBuildModel buildModel,
                                           @NotNull Module module,
                                           @NotNull List<Pair<String, GradleCoordinate>> namedCoordinates,
                                           @NotNull GradleVersionCatalogModel catalogModel,
                                           @Nullable ConfigurationNameMapper nameMapper) {
      DependenciesModel dependenciesModel = buildModel.dependencies();
      for (Pair<String, GradleCoordinate> namedCoordinate : namedCoordinates) {
        String name = COMPILE;
        if (nameMapper != null) {
          name = nameMapper.mapName(module, name, namedCoordinate.getSecond());
        }
        name = GradleUtil.mapConfigurationName(name, GradleProjectSystemUtil.getAndroidGradleModelVersionInUse(module), false);
        String alias = namedCoordinate.getFirst();
        ReferenceTo reference = new ReferenceTo(catalogModel.libraries().findProperty(alias), dependenciesModel);
        dependenciesModel.addArtifact(name, reference);
      }
  }

  private boolean addDependenciesInTransaction(@NotNull Module module,
                                               @NotNull Iterable<GradleCoordinate> coordinates,
                                               @NotNull AddDependencyPolicy policy,
                                               @Nullable ConfigurationNameMapper nameMapper) {
    Project project = module.getProject();
    GradleBuildModel buildModel = ProjectBuildModel.get(project).getModuleBuildModel(module);
    if (buildModel == null) {
      return false;
    }

    switch (policy) {
      case BUILD_FILE ->
        WriteCommandAction.writeCommandAction(project).withName(ADD_DEPENDENCY).run(() -> {
          List<GradleCoordinate> missing = findMissingDependencies(module, coordinates);
          if (missing.isEmpty()) {
            return;
          }
          addDependenciesToBuildFile(buildModel, module, missing, nameMapper);
        });
      case VERSION_CATALOG -> {
        GradleVersionCatalogsModel catalog = ProjectBuildModel.get(project).getVersionCatalogsModel();
        GradleVersionCatalogModel catalogModel = catalog.getVersionCatalogModel(DEFAULT_CATALOG_NAME);
        WriteCommandAction.writeCommandAction(project).withName(ADD_DEPENDENCY).run(() -> {

          List<GradleCoordinate> missingFromModule = findMissingDependencies(module, coordinates);
          if(missingFromModule.isEmpty()) return; // we have all dependencies already

          CatalogDependenciesInfo catalogSearchResult = computeCatalogDependenciesInfo(module.getProject(), missingFromModule, catalogModel);

          addDependenciesToCatalogAndModuleBuildFile(module, nameMapper, buildModel, catalogModel, catalogSearchResult);
        });
      }
    }
    return true;
  }

  private static void addDependenciesToCatalogAndModuleBuildFile(@NotNull Module module,
                                                                 @Nullable ConfigurationNameMapper nameMapper,
                                                                 GradleBuildModel buildModel,
                                                                 GradleVersionCatalogModel catalogModel,
                                                                 CatalogDependenciesInfo catalogSearchResult) {
    updateModel(module, model -> {
      List<Pair<String, GradleCoordinate>> addedCoordinates = addCatalogLibraries(catalogModel,
                                                                                  catalogSearchResult.missingLibraries);

      List<Pair<String, GradleCoordinate>> allCoordinates = new ArrayList<>(catalogSearchResult.matchedCoordinates);
      allCoordinates.addAll(addedCoordinates);
      addCatalogReferences(buildModel, module, allCoordinates, catalogModel, nameMapper);
      catalogModel.applyChanges(); // need to store catalog first as build file has reference to it
      buildModel.applyChanges();
    });
  }


  private static void addDependenciesToBuildFile(@NotNull GradleBuildModel buildModel,
                                                 @NotNull Module module,
                                                 @NotNull List<GradleCoordinate> coordinates,
                                                 @Nullable ConfigurationNameMapper nameMapper) {
    updateModel(module, model -> {
      DependenciesModel dependenciesModel = buildModel.dependencies();
      for (GradleCoordinate coordinate : coordinates) {
        String name = COMPILE;
        if (nameMapper != null) {
          name = nameMapper.mapName(module, name, coordinate);
        }
        name = GradleUtil.mapConfigurationName(name, GradleProjectSystemUtil.getAndroidGradleModelVersionInUse(module), false);
        dependenciesModel.addArtifact(name, coordinate.toString());
      }
      buildModel.applyChanges();
    });
  }

  private static void updateDependenciesInTransaction(@NotNull GradleBuildModel buildModel,
                                                      @NotNull Module module,
                                                      @NotNull List<GradleCoordinate> coordinates) {
    assert !coordinates.isEmpty();

    Project project = module.getProject();
    WriteCommandAction.writeCommandAction(project).withName(ADD_DEPENDENCY).run(() -> updateDependencies(buildModel, module, coordinates));
  }

  private static void requestProjectSync(@NotNull Project project, @NotNull GradleSyncStats.Trigger trigger) {
    GradleSyncInvoker.Request request = new GradleSyncInvoker.Request(trigger);
    GradleSyncInvoker.getInstance().requestProjectSync(project, request, null);
  }

  private static void updateDependencies(@NotNull GradleBuildModel buildModel,
                                         @NotNull Module module,
                                         @NotNull List<GradleCoordinate> coordinates) {
    updateModel(module, model -> {
      DependenciesModel dependenciesModel = buildModel.dependencies();
      for (GradleCoordinate gc : coordinates) {
        List<ArtifactDependencyModel> artifacts = new ArrayList<>(dependenciesModel.artifacts());
        for (ArtifactDependencyModel m : artifacts) {
          if (gc.getGroupId().equals(m.group().toString())
              && gc.getArtifactId().equals(m.name().forceString())
              && !gc.getRevision().equals(m.version().toString())) {
            // TODO probably need to update in place as external references can be broken
            // need to reconsider version catalog as dependency storage
            dependenciesModel.remove(m);
            dependenciesModel.addArtifact(m.configurationName(), gc.toString());
          }
        }
      }
      buildModel.applyChanges();
    });
  }
}
