/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.android.SdkConstants;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.ArtifactInfo;
import com.android.builder.model.Dependencies;
import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Configures a module's dependencies from an {@link com.android.builder.model.AndroidProject}.
 */
public final class AndroidDependencies {
  private AndroidDependencies() {
  }

  /**
   * Populates the dependencies of a module based on the given {@link com.android.builder.model.AndroidProject}.
   *
   * @param androidProject    structure of the Android-Gradle project.
   * @param dependencyFactory creates and adds dependencies to a module.
   * @param eventLogger       logs any caught failures. These failures will be later on presented to the user.
   */
  public static void populate(@NotNull IdeaAndroidProject androidProject,
                              @NotNull DependencyFactory dependencyFactory,
                              @Nullable ProjectImportEventLogger eventLogger) {
    Variant selectedVariant = androidProject.getSelectedVariant();
    String moduleName = androidProject.getModuleName();

    // Process "test" scope first. The "test" dependencies returned by Gradle include also the "compile" dependencies. If we process
    // "compile" scope first, dependencies will end up with "test" scope because IDEA overwrites the scope.
    ArtifactInfo testArtifactInfo = selectedVariant.getTestArtifactInfo();
    if (testArtifactInfo != null) {
      populateDependencies(moduleName, DependencyScope.TEST, testArtifactInfo.getDependencies(), dependencyFactory, eventLogger);
    }

    ArtifactInfo mainArtifactInfo = selectedVariant.getMainArtifactInfo();
    populateDependencies(moduleName, DependencyScope.COMPILE, mainArtifactInfo.getDependencies(), dependencyFactory, eventLogger);
  }

  private static void populateDependencies(@NotNull String moduleName,
                                           @NotNull DependencyScope scope,
                                           @NotNull Dependencies dependencies,
                                           @NotNull DependencyFactory dependencyFactory,
                                           @Nullable ProjectImportEventLogger eventLogger) {
    for (File jar : dependencies.getJars()) {
      addLibraryDependency(scope, dependencyFactory, jar);
    }
    for (AndroidLibrary lib : dependencies.getLibraries()) {
      boolean fallingBackToAar = false;
      String project = lib.getProject();
      if (project != null && !project.isEmpty()) {
        if (addModuleDependency(scope, dependencyFactory, project)) {
          continue;
        }
        else {
          fallingBackToAar = true;
        }
      }
      File jar = lib.getJarFile();
      File parentFile = jar.getParentFile();
      if (fallingBackToAar) {
        logModuleDependencyNotFound(moduleName, project, parentFile, eventLogger);
      }
      String name = parentFile != null ? parentFile.getName() : FileUtil.getNameWithoutExtension(jar);
      dependencyFactory.addLibraryDependency(scope, name, jar);
      for (File localJar : lib.getLocalJars()) {
        addLibraryDependency(scope, dependencyFactory, localJar);
      }
    }
    for (String project : dependencies.getProjects()) {
      if (project != null && !project.isEmpty()) {
        if (!addModuleDependency(scope, dependencyFactory, project)) {
          logModuleDependencyNotFound(moduleName, project, eventLogger);
        }
      }
    }
  }

  private static void logModuleDependencyNotFound(@NotNull String moduleName,
                                                  @NotNull String dependency,
                                                  @NotNull File aarFile,
                                                  @Nullable ProjectImportEventLogger eventLogger) {
    if (eventLogger != null) {
      String category = String.format("Warning(s) found while populating dependencies of module '%1$s'. " +
                                      "A common cause is references to modules outside of the project.", moduleName);
      String msg = String.format("Unable fo find module '%1$s'. Linking to file '%2$s' instead.", dependency, aarFile.getName());
      eventLogger.log(category, msg);
    }
  }

  private static void logModuleDependencyNotFound(@NotNull String moduleName,
                                                  @NotNull String dependency,
                                                  @Nullable ProjectImportEventLogger eventLogger) {
    if (eventLogger != null) {
      String category = String.format("Error(s) found while populating dependencies of module '%1$s'.", moduleName);
      String msg = String.format("Unable fo find module '%1$s'.", dependency);
      eventLogger.log(category, msg);
    }
  }

  private static void addLibraryDependency(@NotNull DependencyScope scope,
                                           @NotNull DependencyFactory dependencyFactory,
                                           @NotNull File jar) {
    dependencyFactory.addLibraryDependency(scope, FileUtil.getNameWithoutExtension(jar), jar);
  }

  private static boolean addModuleDependency(@NotNull DependencyScope scope,
                                             @NotNull DependencyFactory dependencyFactory,
                                             @NotNull String modulePath) {
    String[] pathSegments = modulePath.split(SdkConstants.GRADLE_PATH_SEPARATOR);
    String moduleName = pathSegments[pathSegments.length - 1];
    return dependencyFactory.addModuleDependency(scope, moduleName, modulePath);
  }

  /**
   * Adds a new dependency to a module.
   */
  public interface DependencyFactory {
    /**
     * Adds a new library dependency to a module.
     *
     * @param scope      scope of the dependency.
     * @param name       name of the dependency.
     * @param binaryPath absolute path of the dependency's jar file.
     * @return {@code true} if the dependency was successfully added; {@code false} otherwise.
     */
    boolean addLibraryDependency(@NotNull DependencyScope scope, @NotNull String name, @NotNull File binaryPath);

    /**
     * Adds a dependency on another module in the same project.
     *
     * @param scope       scope of the dependency.
     * @param name        the name of the module.
     * @param modulePath  the Gradle path of the module.
     * @return {@code true} if the dependency was successfully added; {@code false} otherwise.
     */
    boolean addModuleDependency(@NotNull DependencyScope scope, @NotNull String name, @NotNull String modulePath);
  }
}
