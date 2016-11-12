/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.module.java;

import com.android.tools.idea.gradle.project.sync.model.JavaModuleModel;
import com.android.tools.idea.gradle.facet.JavaGradleFacet;
import com.android.tools.idea.gradle.facet.JavaGradleFacetConfiguration;
import com.android.tools.idea.gradle.model.java.JarLibraryDependency;
import com.android.tools.idea.gradle.model.java.JavaModuleDependency;
import com.android.tools.idea.gradle.project.sync.SyncAction;
import com.android.tools.idea.gradle.project.sync.facet.gradle.AndroidGradleFacet;
import com.android.tools.idea.gradle.project.sync.issues.UnresolvedDependenciesReporter;
import com.android.tools.idea.gradle.project.sync.setup.module.JavaModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.common.DependenciesSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.common.DependencySetupErrors;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.util.Facets.findFacet;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static com.intellij.openapi.util.io.FileUtil.*;
import static java.util.Collections.singletonList;

public class DependenciesModuleSetupStep extends JavaModuleSetupStep {
  private static final DependencyScope DEFAULT_DEPENDENCY_SCOPE = COMPILE;

  @NotNull private final DependenciesSetup myDependenciesSetup;

  public DependenciesModuleSetupStep() {
    this(new DependenciesSetup());
  }

  @VisibleForTesting
  DependenciesModuleSetupStep(@NotNull DependenciesSetup dependenciesSetup) {
    myDependenciesSetup = dependenciesSetup;
  }

  @Override
  protected void doSetUpModule(@NotNull Module module,
                               @NotNull IdeModifiableModelsProvider ideModelsProvider,
                               @NotNull JavaModuleModel javaModuleModel,
                               @Nullable SyncAction.ModuleModels gradleModels,
                               @Nullable ProgressIndicator indicator) {
    List<String> unresolved = new ArrayList<>();
    for (JavaModuleDependency dependency : javaModuleModel.getJavaModuleDependencies()) {
      updateDependency(module, ideModelsProvider, dependency);
    }

    for (JarLibraryDependency dependency : javaModuleModel.getJarLibraryDependencies()) {
      if (dependency.isResolved()) {
        updateDependency(module, ideModelsProvider, dependency);
      }
      else {
        unresolved.add(dependency.getName());
      }
    }

    UnresolvedDependenciesReporter.getInstance().report(unresolved, module);

    JavaGradleFacet facet = setAndGetJavaGradleFacet(module, ideModelsProvider);
    File buildFolderPath = javaModuleModel.getBuildFolderPath();

    AndroidGradleFacet gradleFacet = findFacet(module, ideModelsProvider, AndroidGradleFacet.getFacetTypeId());
    if (gradleFacet != null) {
      // This is an actual Gradle module, because it has the AndroidGradleFacet. Top-level modules in a multi-module project usually don't
      // have this facet.
      facet.setJavaModuleModel(javaModuleModel);
    }
    JavaGradleFacetConfiguration facetProperties = facet.getConfiguration();
    facetProperties.BUILD_FOLDER_PATH = buildFolderPath != null ? toSystemIndependentName(buildFolderPath.getPath()) : "";
    facetProperties.BUILDABLE = javaModuleModel.isBuildable();
  }

  private static void updateDependency(@NotNull Module module,
                                       @NotNull IdeModifiableModelsProvider modelsProvider,
                                       @NotNull JavaModuleDependency dependency) {
    DependencySetupErrors setupErrors = DependencySetupErrors.getInstance(module.getProject());

    String moduleName = dependency.getModuleName();
    Module found = null;
    for (Module m : modelsProvider.getModules()) {
      if (moduleName.equals(m.getName())) {
        found = m;
      }
    }

    ModifiableRootModel moduleModel = modelsProvider.getModifiableRootModel(module);
    if (found != null) {
      AndroidFacet androidFacet = findFacet(found, modelsProvider, AndroidFacet.ID);
      if (androidFacet == null) {
        ModuleOrderEntry entry = moduleModel.addModuleOrderEntry(found);
        entry.setExported(true);
      }
      else {
        // If it depends on an android module, we should skip that.
        setupErrors.addInvalidModuleDependency(moduleModel.getModule(), found.getName(), "Java modules cannot depend on Android modules");
      }
      return;
    }
    setupErrors.addMissingModule(moduleName, module.getName(), null);
  }

  private void updateDependency(@NotNull Module module,
                                @NotNull IdeModifiableModelsProvider modelsProvider,
                                @NotNull JarLibraryDependency dependency) {
    DependencyScope scope = parseScope(dependency.getScope());
    File binaryPath = dependency.getBinaryPath();
    if (binaryPath == null) {
      DependencySetupErrors setupErrors = DependencySetupErrors.getInstance(module.getProject());
      setupErrors.addMissingBinaryPath(module.getName());
      return;
    }

    // Gradle API doesn't provide library name at the moment.
    String name = binaryPath.isFile() ? getNameWithoutExtension(binaryPath) : sanitizeFileName(binaryPath.getPath());

    List<String> binaries = singletonList(binaryPath.getPath());
    List<String> sources = asPaths(dependency.getSourcePath());
    List<String> javadocs = asPaths(dependency.getJavadocPath());
    myDependenciesSetup.setUpLibraryDependency(module, modelsProvider, name, scope, binaries, sources, javadocs);
  }

  @NotNull
  private static List<String> asPaths(@Nullable File file) {
    return file == null ? Collections.emptyList() : singletonList(file.getPath());
  }

  @NotNull
  private static DependencyScope parseScope(@Nullable String scope) {
    if (scope == null) {
      return DEFAULT_DEPENDENCY_SCOPE;
    }
    for (DependencyScope dependencyScope : DependencyScope.values()) {
      if (scope.equalsIgnoreCase(dependencyScope.toString())) {
        return dependencyScope;
      }
    }
    return DEFAULT_DEPENDENCY_SCOPE;
  }

  @NotNull
  private static JavaGradleFacet setAndGetJavaGradleFacet(@NotNull Module module, @NotNull IdeModifiableModelsProvider modelsProvider) {
    JavaGradleFacet facet = findFacet(module, modelsProvider, JavaGradleFacet.TYPE_ID);
    if (facet != null) {
      return facet;
    }

    FacetManager facetManager = FacetManager.getInstance(module);
    ModifiableFacetModel model = modelsProvider.getModifiableFacetModel(module);
    facet = facetManager.createFacet(JavaGradleFacet.getFacetType(), JavaGradleFacet.NAME, null);
    model.addFacet(facet);
    return facet;
  }

  @Override
  @NotNull
  public String getDescription() {
    return "Java dependencies setup";
  }
}
