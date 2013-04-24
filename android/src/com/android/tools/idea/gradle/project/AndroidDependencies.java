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

import com.android.build.gradle.model.AndroidProject;
import com.android.build.gradle.model.Dependencies;
import com.android.build.gradle.model.ProductFlavorContainer;
import com.android.build.gradle.model.Variant;
import com.android.builder.model.AndroidLibrary;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.LibraryPathType;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Populates an IDEA module with dependencies created from an {@link IdeaAndroidProject}.
 */
final class AndroidDependencies {
  private AndroidDependencies() {
  }

  static void populate(@NotNull DataNode<ModuleData> moduleInfo,
                       @NotNull DataNode<ProjectData> projectInfo,
                       @NotNull IdeaAndroidProject androidProject) {
    AndroidProject delegate = androidProject.getDelegate();

    Variant selectedVariant = getSelectedVariant(androidProject);
    for (String flavorName : selectedVariant.getProductFlavors()) {
      ProductFlavorContainer productFlavor = delegate.getProductFlavors().get(flavorName);
      populateDependencies(moduleInfo, projectInfo, productFlavor);
    }

    ProductFlavorContainer defaultConfig = delegate.getDefaultConfig();
    populateDependencies(moduleInfo, projectInfo, defaultConfig);
  }

  @NotNull
  private static Variant getSelectedVariant(@NotNull IdeaAndroidProject androidProject) {
    String selectedVariantName = androidProject.getSelectedVariantName();
    return androidProject.getDelegate().getVariants().get(selectedVariantName);
  }

  private static void populateDependencies(@NotNull DataNode<ModuleData> moduleInfo,
                                           @NotNull DataNode<ProjectData> projectInfo,
                                           @NotNull ProductFlavorContainer productFlavor) {
    populateDependencies(moduleInfo, projectInfo, DependencyScope.COMPILE, productFlavor.getDependencies());
    populateDependencies(moduleInfo, projectInfo, DependencyScope.TEST, productFlavor.getTestDependencies());
  }

  private static void populateDependencies(@NotNull DataNode<ModuleData> moduleInfo,
                                           @NotNull DataNode<ProjectData> projectInfo,
                                           @NotNull DependencyScope scope,
                                           @NotNull Dependencies dependencies) {
    for (File jar : dependencies.getJars()) {
      LibraryDependency dependency = createDependency(scope, FileUtil.getNameWithoutExtension(jar), jar);
      dependency.addTo(moduleInfo, projectInfo);
    }
    for (AndroidLibrary lib : dependencies.getLibraries()) {
      File jar = lib.getJarFile();
      File parentFile = jar.getParentFile();
      String name = parentFile != null ? parentFile.getName() : FileUtil.getNameWithoutExtension(jar);
      LibraryDependency dependency = createDependency(scope, name, jar);
      dependency.addTo(moduleInfo, projectInfo);
    }
  }

  @NotNull
  private static LibraryDependency createDependency(@NotNull DependencyScope scope, @NotNull String name, @NotNull File binaryPath) {
    LibraryDependency dependency = new LibraryDependency(name);
    dependency.setScope(scope);
    dependency.addPath(LibraryPathType.BINARY, binaryPath);
    return dependency;
  }
}
