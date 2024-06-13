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
import static com.android.tools.idea.gradle.dependencies.AddDependencyPolicy.calculateAddDependencyPolicy;
import static com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.IMPLEMENTATION;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_GRADLEDEPENDENCY_ADDED;
import static com.intellij.openapi.roots.ModuleRootModificationUtil.updateModel;

import com.android.ide.common.gradle.Component;
import com.android.ide.common.gradle.Dependency;
import com.android.ide.common.gradle.RichVersion;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogModel;
import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogsModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.api.catalog.GradleVersionCatalogLibraries;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.LibraryDeclarationSpec;
import com.android.tools.idea.gradle.dsl.api.dependencies.VersionDeclarationSpec;
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.repositories.RepositoryUrlManager;
import com.google.common.base.Objects;
import com.google.wireless.android.sdk.stats.GradleSyncStats;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class GradleDependencyManager {
  private static final String ADD_DEPENDENCY = "Add Dependency";

  private static final Logger LOG = Logger.getInstance("Gradle Dependency Manager");

  @NotNull
  public static GradleDependencyManager getInstance(@NotNull Project project) {
    return project.getService(GradleDependencyManager.class);
  }

  static class CatalogDependenciesInfo {
    public List<Dependency> missingLibraries = new ArrayList<>();
    public List<Pair<String, Dependency>> matchedCoordinates = new ArrayList<>();
  }


  /**
   * Looks through catalog and adds aliases for declarations that already there
   * @param module - need to understand module context and resolve new dependency version correctly in case it's a dynamic '+' one
   * @param dependencies
   * @param catalogModel
   * @return
   */
  @NotNull
  protected CatalogDependenciesInfo computeCatalogDependenciesInfo(@NotNull Module module,
                                                                   @NotNull Iterable<Dependency> dependencies,
                                                                   @NotNull GradleVersionCatalogModel catalogModel) {
    Project project = module.getProject();
    GradleBuildModel moduleModel = ProjectBuildModel.get(project).getModuleBuildModel(module);

    List<ArtifactDependencyModel> compileDependencies = moduleModel != null ? moduleModel.dependencies().artifacts() : null;

    String declaredAppCompatVersion = getDeclaredAppCompatVersion(compileDependencies);
    CatalogDependenciesInfo searchResult = new CatalogDependenciesInfo();
    GradleVersionCatalogLibraries libraries = catalogModel.libraryDeclarations();
    for (Dependency dependency : dependencies) {
      if (dependency.getGroup() == null || dependency.getName() == null) continue;
      Optional<Dependency> resolvedDependency = resolveCoordinate(project, dependency, declaredAppCompatVersion);
      Dependency finalDependency = resolvedDependency.orElse(dependency);
      Optional<Pair<String, Dependency>> maybeDependency = libraries.getAll().entrySet().stream()
        .filter(entry -> {
          LibraryDeclarationSpec spec = entry.getValue().getSpec();
          VersionDeclarationSpec version = spec.getVersion();
          String richVersionIdentifier = null;
          RichVersion richVersion = dependency.getVersion();
          if (richVersion != null) {
            richVersionIdentifier = richVersion.toIdentifier();
          }
          return (Objects.equal(spec.getGroup(), dependency.getGroup()) &&
                  Objects.equal(spec.getName(), dependency.getName()) &&
                  Objects.equal(version == null ? null : version.compactNotation(), richVersionIdentifier));
        }).map(dep -> new Pair<>(dep.getKey(), dependency)).findFirst();
      if (maybeDependency.isEmpty()) {
        searchResult.missingLibraries.add(finalDependency);
      }
      else {
        searchResult.matchedCoordinates.add(maybeDependency.get());
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

      if (dependency.getGroup() == null || dependency.getName() == null) continue;

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
                                        @NotNull Iterable<Dependency> dependencies) {
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
  public boolean addDependenciesWithoutSync(@NotNull Module module, @NotNull Iterable<Dependency> dependencies) {
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
    @NotNull Iterable<Dependency> dependencies,
    @Nullable ConfigurationNameMapper nameMapper) {
    AddDependencyPolicy policy = calculateAddDependencyPolicy(ProjectBuildModel.get(module.getProject()));
    return addDependenciesInTransaction(module, dependencies, policy, nameMapper);
  }

  /**
   * Updates any coordinates to the versions specified in the dependencies list.
   * In case module has a reference to catalog file, dependency will be updated there.
   * For example, if you pass it [com.android.support.constraint:constraint-layout:1.0.0-alpha2],
   * it will find any constraint layout occurrences of 1.0.0-alpha1 and replace them with 1.0.0-alpha2.
   */
  public boolean updateLibrariesToVersion(@NotNull Module module,
                                          @NotNull List<Dependency> dependencies) {
    GradleBuildModel buildModel = ProjectBuildModel.get(module.getProject()).getModuleBuildModel(module);
    if (buildModel == null) {
      return false;
    }
    updateDependenciesInTransaction(buildModel, module, dependencies);
    return true;
  }

  private static List<Pair<String, Dependency>> addCatalogLibraries(@NotNull GradleVersionCatalogModel catalogModel,
                                                                          @NotNull List<Dependency> dependencies) {
    List<Pair<String, Dependency>> addedCoordinates = new ArrayList<>();

    for (Dependency dependency : dependencies) {
      String alias = CatalogDependenciesInserter.addCatalogLibrary(catalogModel, dependency);
      addedCoordinates.add(new Pair<>(alias, dependency));
    }
    return addedCoordinates;
  }

  private static void addCatalogReferences(@NotNull GradleBuildModel buildModel,
                                           @NotNull Module module,
                                           @NotNull List<Pair<String, Dependency>> namedDependencies,
                                           @NotNull GradleVersionCatalogModel catalogModel,
                                           @Nullable ConfigurationNameMapper nameMapper) {
      DependenciesModel dependenciesModel = buildModel.dependencies();
      for (Pair<String, Dependency> namedDependency : namedDependencies) {
        String name = IMPLEMENTATION;
        if (nameMapper != null) {
          name = nameMapper.mapName(module, name, namedDependency.getSecond());
        }
        String alias = namedDependency.getFirst();
        ReferenceTo reference = new ReferenceTo(catalogModel.libraries().findProperty(alias), dependenciesModel);
        dependenciesModel.addArtifact(name, reference);
      }
  }

  private boolean addDependenciesInTransaction(@NotNull Module module,
                                               @NotNull Iterable<Dependency> dependencies,
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
          List<Dependency> missing = findMissingDependencies(module, dependencies);
          if (missing.isEmpty()) {
            return;
          }
          addDependenciesToBuildFile(buildModel, module, missing, nameMapper);
        });
      case VERSION_CATALOG -> {
        ProjectBuildModel projectBuildModel = ProjectBuildModel.get(project);
        GradleVersionCatalogModel catalogModel = DependenciesHelper.getDefaultCatalogModel(projectBuildModel);
        if (catalogModel == null) {
          LOG.warn("Version Catalog model is null but VERSION_CATALOG policy in effect");
          return addDependenciesInTransaction(module, dependencies, AddDependencyPolicy.BUILD_FILE, nameMapper);
        }
        WriteCommandAction.writeCommandAction(project).withName(ADD_DEPENDENCY).run(() -> {

          List<Dependency> missingFromModule = findMissingDependencies(module, dependencies);
          if(missingFromModule.isEmpty()) return; // we have all dependencies already

          CatalogDependenciesInfo catalogSearchResult = computeCatalogDependenciesInfo(module, missingFromModule, catalogModel);

          addDependenciesToCatalogAndModuleBuildFile(module, nameMapper, buildModel, catalogModel, catalogSearchResult);
        });
      }
    }
    return true;
  }

  private static void addDependenciesToCatalogAndModuleBuildFile(@NotNull Module module,
                                                                 @Nullable ConfigurationNameMapper nameMapper,
                                                                 @NotNull GradleBuildModel buildModel,
                                                                 @NotNull GradleVersionCatalogModel catalogModel,
                                                                 @NotNull CatalogDependenciesInfo catalogSearchResult) {
    updateModel(module, model -> {
      List<Pair<String, Dependency>> addedDependencies = addCatalogLibraries(catalogModel,
                                                                             catalogSearchResult.missingLibraries);

      List<Pair<String, Dependency>> allCoordinates = new ArrayList<>(catalogSearchResult.matchedCoordinates);
      allCoordinates.addAll(addedDependencies);
      addCatalogReferences(buildModel, module, allCoordinates, catalogModel, nameMapper);
      catalogModel.applyChanges(); // need to store catalog first as build file has reference to it
      buildModel.applyChanges();
    });
  }


  private static void addDependenciesToBuildFile(@NotNull GradleBuildModel buildModel,
                                                 @NotNull Module module,
                                                 @NotNull List<Dependency> dependencies,
                                                 @Nullable ConfigurationNameMapper nameMapper) {
    updateModel(module, model -> {
      DependenciesModel dependenciesModel = buildModel.dependencies();
      for (Dependency dependency : dependencies) {
        String name = IMPLEMENTATION;
        if (nameMapper != null) {
          name = nameMapper.mapName(module, name, dependency);
        }
        String identifier = dependency.toIdentifier();
        if (identifier != null) {
          dependenciesModel.addArtifact(name, identifier);
        }
      }
      buildModel.applyChanges();
    });
  }

  private static void updateDependenciesInTransaction(@NotNull GradleBuildModel buildModel,
                                                      @NotNull Module module,
                                                      @NotNull List<Dependency> dependencies) {
    assert !dependencies.isEmpty();

    Project project = module.getProject();
    WriteCommandAction.writeCommandAction(project).withName(ADD_DEPENDENCY)
      .run(() -> updateDependencies(buildModel, module, dependencies));
  }

  private static void requestProjectSync(@NotNull Project project, @NotNull GradleSyncStats.Trigger trigger) {
    GradleSyncInvoker.Request request = new GradleSyncInvoker.Request(trigger);
    GradleSyncInvoker.getInstance().requestProjectSync(project, request, null);
  }

  private static void updateDependencies(@NotNull GradleBuildModel buildModel,
                                         @NotNull Module module,
                                         @NotNull List<Dependency> dependencies) {
    GradleVersionCatalogsModel catalogsModel = ProjectBuildModel.get(module.getProject()).getVersionCatalogsModel();

    updateModel(module, model -> {
      DependenciesModel dependenciesModel = buildModel.dependencies();
      for (Dependency dependency : dependencies) {
        List<ArtifactDependencyModel> artifacts = new ArrayList<>(dependenciesModel.artifacts());
        for (ArtifactDependencyModel m : artifacts) {
          RichVersion richVersion = dependency.getVersion();
          String richVersionIdentifier = null;
          if (richVersion != null) richVersionIdentifier = richVersion.toIdentifier();
          if (Objects.equal(dependency.getGroup(), m.group().toString())
              && Objects.equal(dependency.getName(), m.name().forceString())
              && !Objects.equal(richVersionIdentifier, m.version().toString())) {

            boolean successfulUpdate = false;

            if (m.isVersionCatalogDependency()) {
              // Trying update catalog once dependency is a reference to a catalog declaration
              successfulUpdate = CatalogDependenciesInserter.updateCatalogLibrary(catalogsModel, m, richVersion);
            }
            if (!successfulUpdate) {
              // Update directly in build file if there is no catalog or update there was unsuccessful
              dependenciesModel.remove(m);
              dependenciesModel.addArtifact(m.configurationName(), dependency.toString());
            }
          }
        }
      }
      for (String catalogName : catalogsModel.catalogNames()) {
        catalogsModel.getVersionCatalogModel(catalogName).applyChanges();
      }
      buildModel.applyChanges();
    });
  }
}
