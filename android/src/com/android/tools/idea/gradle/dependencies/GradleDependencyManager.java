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
package com.android.tools.idea.gradle.dependencies;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.templates.RepositoryUrlManager;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

import static com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames.COMPILE;
import static com.intellij.openapi.util.text.StringUtil.pluralize;

public class GradleDependencyManager {
  private static final String ADD_DEPENDENCY = "Add Dependency";

  @NotNull
  public static GradleDependencyManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleDependencyManager.class);
  }

  /**
   * Returns the dependencies that are NOT included in the specified module.
   * Note: the version of the dependency is disregarded.
   *
   * @param module the module to check dependencies in
   * @param dependencies the dependencies of interest.
   * @return a list of the dependencies NOT included in the module
   */
  @NotNull
  public List<GradleCoordinate> findMissingDependencies(@NotNull Module module, @NotNull Iterable<GradleCoordinate> dependencies) {
    GradleBuildModel buildModel = GradleBuildModel.get(module);
    assert buildModel != null;
    return findMissingLibrariesFromGradleBuildFile(buildModel, dependencies);
  }

  /**
   * Ensures that all the specified dependencies are included in the specified module.
   * <p/>
   * If some dependencies are missing a dialog is presented to the user if those dependencies should be added to the module.
   * If the user agrees the dependencies are added. The caller may supply a callback to determine when the requested dependencies
   * have been added (this make take several seconds).
   *
   * @param module       the module to add dependencies to
   * @param dependencies the dependencies of interest.
   * @param callback     an optional callback to signal to completion of the added dependencies
   * @return true if the dependencies were already present in the module or if the user requested adding them, and
   * false if the dependency was missing and the user declined adding them
   */
  public boolean ensureLibraryIsIncluded(@NotNull Module module,
                                         @NotNull Iterable<GradleCoordinate> dependencies,
                                         @Nullable Runnable callback) {
    GradleBuildModel buildModel = GradleBuildModel.get(module);
    assert buildModel != null;
    List<GradleCoordinate> missing = findMissingLibrariesFromGradleBuildFile(buildModel, dependencies);
    if (missing.isEmpty()) {
      return true;
    }
    if (userWantToAddDependencies(module, missing)) {
      addDependenciesInTransaction(buildModel, module, missing, callback);
      return true;
    }
    return false;
  }

  /**
   * Updates any coordinates to the versions specified in the dependencies list.
   * For example, if you pass it [com.android.support.constraint:constraint-layout:1.0.0-alpha2],
   * it will find any constraint layout occurrences of 1.0.0-alpha1 and replace them with 1.0.0-alpha2.
   */
  public boolean updateLibrariesToVersion(@NotNull Module module,
                                          @NotNull List<GradleCoordinate> dependencies,
                                          @Nullable Runnable callback) {
    GradleBuildModel buildModel = GradleBuildModel.get(module);
    assert buildModel != null;
    updateDependenciesInTransaction(buildModel, module, dependencies, callback);
    return true;
  }

  @NotNull
  private static List<GradleCoordinate> findMissingLibrariesFromGradleBuildFile(@NotNull GradleBuildModel buildModel,
                                                                                @NotNull Iterable<GradleCoordinate> dependencies) {
    RepositoryUrlManager manager = RepositoryUrlManager.get();
    List<GradleCoordinate> missingLibraries = Lists.newArrayList();
    for (GradleCoordinate coordinate : dependencies) {
      coordinate = manager.resolveDynamicCoordinate(coordinate, null);
      if (coordinate == null) {
        // TODO Should this be an error ?
        continue;
      }

      boolean dependencyFound = false;
      for (ArtifactDependencyModel dependency : buildModel.dependencies().artifacts(COMPILE)) {
        if (Objects.equal(coordinate.getGroupId(), dependency.group().value()) &&
            Objects.equal(coordinate.getArtifactId(), dependency.name().value())) {
          dependencyFound = true;
          break;
        }
      }
      if (!dependencyFound) {
        missingLibraries.add(coordinate);
      }
    }
    return missingLibraries;
  }

  private static boolean userWantToAddDependencies(@NotNull Module module, @NotNull Collection<GradleCoordinate> missing) {
    String libraryNames = StringUtil.join(missing, new Function<GradleCoordinate, String>() {
      @Nullable
      @Override
      public String fun(GradleCoordinate coordinate) {
        return coordinate.getArtifactId();
      }
    }, ", ");
    String message = String.format("This operation requires the %1$s %2$s. \n\nWould you like to add %3$s %1$s now?",
                                   pluralize("library", missing.size()), libraryNames, pluralize("this", missing.size()));
    Project project = module.getProject();
    return Messages.showOkCancelDialog(project, message, "Add Project Dependency", Messages.getErrorIcon()) == Messages.OK;
  }

  private static void addDependenciesInTransaction(@NotNull final GradleBuildModel buildModel,
                                                   @NotNull final Module module,
                                                   @NotNull final List<GradleCoordinate> coordinates,
                                                   @Nullable final Runnable callback) {
    assert !coordinates.isEmpty();

    final Project project = module.getProject();
    new WriteCommandAction(project, ADD_DEPENDENCY) {
      @Override
      protected void run(@NotNull final Result result) throws Throwable {
        addDependencies(buildModel, module, coordinates);
        GradleProjectImporter.getInstance().requestProjectSync(project, false /* do not generate sources */, createSyncListener(callback));
      }
    }.execute();
  }

  private static void addDependencies(@NotNull final GradleBuildModel buildModel,
                                      @NotNull Module module,
                                      @NotNull final List<GradleCoordinate> coordinates) {
    ModuleRootModificationUtil.updateModel(module, new Consumer<ModifiableRootModel>() {
      @Override
      public void consume(ModifiableRootModel model) {
        DependenciesModel dependenciesModel = buildModel.dependencies();
        for (GradleCoordinate coordinate : coordinates) {
          dependenciesModel.addArtifact(COMPILE, coordinate.toString());
        }
        buildModel.applyChanges();
      }
    });
  }

  private static void updateDependenciesInTransaction(@NotNull final GradleBuildModel buildModel,
                                                      @NotNull final Module module,
                                                      @NotNull final List<GradleCoordinate> coordinates,
                                                      @Nullable final Runnable callback) {
    assert !coordinates.isEmpty();

    final Project project = module.getProject();
    new WriteCommandAction(project, ADD_DEPENDENCY) {
      @Override
      protected void run(@NotNull final Result result) throws Throwable {
        updateDependencies(buildModel, module, coordinates);
        GradleProjectImporter.getInstance().requestProjectSync(project, false /* do not generate sources */, createSyncListener(callback));
      }
    }.execute();
  }

  private static void updateDependencies(@NotNull final GradleBuildModel buildModel,
                                         @NotNull Module module,
                                         @NotNull final List<GradleCoordinate> coordinates) {
    ModuleRootModificationUtil.updateModel(module, model -> {
      DependenciesModel dependenciesModel = buildModel.dependencies();
      for (GradleCoordinate gc : coordinates) {
        List<ArtifactDependencyModel> artifacts = Lists.newArrayList(dependenciesModel.artifacts());
        for (ArtifactDependencyModel m : artifacts) {
          if (gc.getGroupId() != null && gc.getGroupId().equals(m.group().value())
              && gc.getArtifactId() != null && gc.getArtifactId().equals(m.name().value())
              && !gc.getRevision().equals(m.version().value())) {
            dependenciesModel.remove(m);
            dependenciesModel.addArtifact(m.configurationName(), gc.toString());
          }
        }
      }
      buildModel.applyChanges();
    });
  }

  @Nullable
  private static GradleSyncListener createSyncListener(@Nullable final Runnable callback) {
    if (callback == null) {
      return null;
    }
    return new GradleSyncListener.Adapter() {
      @Override
      public void syncSucceeded(@NotNull Project project) {
        callback.run();
      }

      @Override
      public void syncSkipped(@NotNull Project project) {
        callback.run();
      }
    };
  }
}
