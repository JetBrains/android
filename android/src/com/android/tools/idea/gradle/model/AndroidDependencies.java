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
package com.android.tools.idea.gradle.model;

import com.android.build.gradle.model.*;
import com.android.builder.model.AndroidLibrary;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Configures a module's dependencies from an {@link AndroidProject}.
 */
public final class AndroidDependencies {
  private AndroidDependencies() {
  }

  /**
   * Populates the dependencies of a module based on the given {@link AndroidProject}.
   *
   * @param androidProject    structure of the Android-Gradle project.
   * @param dependencyFactory creates and adds dependencies to a module.
   */
  public static void populate(@NotNull IdeaAndroidProject androidProject, @NotNull DependencyFactory dependencyFactory) {
    AndroidProject delegate = androidProject.getDelegate();

    Variant selectedVariant = androidProject.getSelectedVariant();
    for (String flavorName : selectedVariant.getProductFlavors()) {
      ProductFlavorContainer productFlavor = delegate.getProductFlavors().get(flavorName);
      populateDependencies(productFlavor, dependencyFactory);
    }

    ProductFlavorContainer defaultConfig = delegate.getDefaultConfig();
    populateDependencies(defaultConfig, dependencyFactory);

    String buildTypeName = selectedVariant.getBuildType();
    BuildTypeContainer buildType = delegate.getBuildTypes().get(buildTypeName);
    if (buildType != null) {
      populateDependencies(DependencyScope.COMPILE, buildType.getDependency(), dependencyFactory);
    }
  }

  private static void populateDependencies(@NotNull ProductFlavorContainer productFlavor, @NotNull DependencyFactory dependencyFactory) {
    populateDependencies(DependencyScope.COMPILE, productFlavor.getDependencies(), dependencyFactory);
    populateDependencies(DependencyScope.TEST, productFlavor.getTestDependencies(), dependencyFactory);
  }

  private static void populateDependencies(@NotNull DependencyScope scope,
                                           @NotNull Dependencies dependencies,
                                           @NotNull DependencyFactory dependencyFactory) {
    for (File jar : dependencies.getJars()) {
      dependencyFactory.addDependency(scope, FileUtil.getNameWithoutExtension(jar), jar);
    }
    for (AndroidLibrary lib : dependencies.getLibraries()) {
      File jar = lib.getJarFile();
      File parentFile = jar.getParentFile();
      String name = parentFile != null ? parentFile.getName() : FileUtil.getNameWithoutExtension(jar);
      dependencyFactory.addDependency(scope, name, jar);
    }
  }

  /**
   * Adds a new dependency to a module.
   */
  public interface DependencyFactory {
    /**
     * Adds a new dependency to a module.
     *
     * @param scope      scope of the dependency.
     * @param name       name of the dependency.
     * @param binaryPath absolute path of the dependency's jar file.
     */
    void addDependency(@NotNull DependencyScope scope, @NotNull String name, @NotNull File binaryPath);
  }
}
