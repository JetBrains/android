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

import com.android.tools.idea.dependencies.DependencyManager;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.templates.RepositoryUrlManager;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames.COMPILE;
import static com.intellij.openapi.util.text.StringUtil.pluralize;

public class GradleDependencyManager extends DependencyManager {
  private static final String ADD_DEPENDENCY = "Add Dependency";

  @NotNull
  public static GradleDependencyManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleDependencyManager.class);
  }

  @NotNull
  @Override
  public List<String> findMissingDependencies(@NotNull Module module, @NotNull Iterable<String> androidDependencies) {
    GradleBuildModel buildModel = GradleBuildModel.get(module);
    assert buildModel != null;
    return findMissingLibrariesFromGradleBuildFile(buildModel, androidDependencies);
  }

  @Override
  public boolean ensureLibraryIsIncluded(@NotNull Module module,
                                         @NotNull Iterable<String> androidDependencies,
                                         @Nullable Runnable callback) {
    GradleBuildModel buildModel = GradleBuildModel.get(module);
    assert buildModel != null;
    List<String> missing = findMissingLibrariesFromGradleBuildFile(buildModel, androidDependencies);
    if (missing.isEmpty()) {
      return true;
    }
    if (userWantToAddDependencies(module, missing)) {
      addDependenciesInTransaction(buildModel, module, missing, callback);
    }
    return false;
  }

  private static List<String> findMissingLibrariesFromGradleBuildFile(@NotNull GradleBuildModel buildModel,
                                                                      @NotNull Iterable<String> pathIds) {
    List<String> missingLibraries = Lists.newArrayList();
    RepositoryUrlManager manager = RepositoryUrlManager.get();
    for (String pathId : pathIds) {
      String libraryCoordinate = manager.getLibraryCoordinate(pathId);

      boolean dependencyFound = false;
      for (ArtifactDependencyModel dependency : buildModel.dependencies().artifacts(COMPILE)) {
        String compactNotation = dependency.getSpec().compactNotation();
        if (compactNotation.equals(libraryCoordinate)) {
          dependencyFound = true;
          break;
        }
      }
      if (!dependencyFound) {
        missingLibraries.add(pathId);
      }
    }
    return missingLibraries;
  }

  private static boolean userWantToAddDependencies(@NotNull Module module, @NotNull List<String> missing) {
    String libraryNames = StringUtil.join(missing, ", ");
    String message = String.format("This operation requires the %1$s: %2$s. \n\nWould you like to add %3$s %1$s now?",
                                   pluralize("library", missing.size()), libraryNames, pluralize("this", missing.size()));
    Project project = module.getProject();
    return Messages.showOkCancelDialog(project, message, "Add Project Dependency", Messages.getErrorIcon()) == Messages.OK;
  }

  private static void addDependenciesInTransaction(@NotNull final GradleBuildModel buildModel,
                                                   @NotNull final Module module,
                                                   @NotNull List<String> missing,
                                                   @Nullable final Runnable callback) {
    assert !missing.isEmpty();

    final List<String> missingLibraryCoordinates = Lists.newArrayList();
    RepositoryUrlManager manager = RepositoryUrlManager.get();
    for (String pathId : missing) {
      missingLibraryCoordinates.add(manager.getLibraryCoordinate(pathId));
    }

    final Project project = module.getProject();
    new WriteCommandAction(project, ADD_DEPENDENCY) {
      @Override
      protected void run(@NotNull final Result result) throws Throwable {
        addDependencies(buildModel, module, missingLibraryCoordinates);
        GradleProjectImporter.getInstance().requestProjectSync(project, false /* do not generate sources */, createSyncListener(callback));
      }
    }.execute();
  }

  private static void addDependencies(@NotNull final GradleBuildModel buildModel,
                                      @NotNull Module module,
                                      @NotNull final List<String> libraryCoordinates) {
    ModuleRootModificationUtil.updateModel(module, new Consumer<ModifiableRootModel>() {
      @Override
      public void consume(ModifiableRootModel model) {
        DependenciesModel dependenciesModel = buildModel.dependencies();
        for (String libraryCoordinate : libraryCoordinates) {
          dependenciesModel.addArtifact(COMPILE, libraryCoordinate);
        }
        buildModel.applyChanges();
      }
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
