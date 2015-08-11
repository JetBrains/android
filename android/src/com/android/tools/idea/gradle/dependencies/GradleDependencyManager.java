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
import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.BuildFileStatement;
import com.android.tools.idea.gradle.parser.Dependency;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
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
    GradleBuildFile gradleBuildFile = GradleBuildFile.get(module);
    assert gradleBuildFile != null;
    return findMissingLibrariesFromGradleBuildFile(gradleBuildFile, androidDependencies);
  }

  @Override
  public boolean ensureLibraryIsIncluded(@NotNull Module module,
                                         @NotNull Iterable<String> androidDependencies,
                                         @Nullable Runnable callback) {
    GradleBuildFile gradleBuildFile = GradleBuildFile.get(module);
    assert gradleBuildFile != null;
    List<String> missing = findMissingLibrariesFromGradleBuildFile(gradleBuildFile, androidDependencies);
    if (missing.isEmpty()) {
      return true;
    }
    if (userWantToAddDependencies(module, missing)) {
      addDependenciesInTransaction(gradleBuildFile, module, missing, callback);
    }
    return false;
  }

  private static List<String> findMissingLibrariesFromGradleBuildFile(@NotNull GradleBuildFile gradleBuildFile,
                                                                      @NotNull Iterable<String> pathIds) {
    List<String> missingLibraries = Lists.newArrayList();
    RepositoryUrlManager manager = RepositoryUrlManager.get();
    for (String pathId : pathIds) {
      String libraryCoordinate = manager.getLibraryCoordinate(pathId);

      boolean dependencyFound = false;
      for (BuildFileStatement entry : gradleBuildFile.getDependencies()) {
        if (entry instanceof Dependency) {
          Dependency dependency = (Dependency)entry;
          if (dependency.scope == Dependency.Scope.COMPILE &&
              dependency.type == Dependency.Type.EXTERNAL &&
              dependency.getValueAsString().equals(libraryCoordinate)) {
            dependencyFound = true;
            break;
          }
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

  private static void addDependenciesInTransaction(@NotNull final GradleBuildFile gradleBuildFile,
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
        addDependencies(gradleBuildFile, module, missingLibraryCoordinates);
        GradleProjectImporter.getInstance().requestProjectSync(project, false /* do not generate sources */, createSyncListener(callback));
      }
    }.execute();
  }

  private static void addDependencies(@NotNull final GradleBuildFile gradleBuildFile,
                                      @NotNull Module module,
                                      @NotNull final List<String> libraryCoordinates) {
    ModuleRootModificationUtil.updateModel(module, new Consumer<ModifiableRootModel>() {
      @Override
      public void consume(ModifiableRootModel model) {
        List<BuildFileStatement> dependencies = gradleBuildFile.getDependencies();
        for (String libraryCoordinate : libraryCoordinates) {
          dependencies.add(new Dependency(Dependency.Scope.COMPILE, Dependency.Type.EXTERNAL, libraryCoordinate));
        }
        gradleBuildFile.setValue(BuildFileKey.DEPENDENCIES, dependencies);
      }
    });
  }

  @Nullable
  private static GradleSyncListener createSyncListener(@Nullable final Runnable callback) {
    if (callback == null) {
      return null;
    }
    return new GradleSyncListener() {
      @Override
      public void syncStarted(@NotNull Project project) {
      }

      @Override
      public void syncSucceeded(@NotNull Project project) {
        callback.run();
      }

      @Override
      public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
      }

      @Override
      public void syncSkipped(@NotNull Project project) {
        callback.run();
      }
    };
  }
}
