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
package com.android.tools.idea.gradle.project.sync.setup.post.cleanup;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.NativeAndroidProject;
import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.setup.module.android.DependenciesModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.dependency.DependenciesExtractor;
import com.android.tools.idea.gradle.project.sync.setup.module.dependency.DependencySet;
import com.android.tools.idea.gradle.project.sync.setup.module.dependency.LibraryDependency;
import com.android.tools.idea.gradle.project.sync.setup.module.dependency.ModuleDependency;
import com.android.tools.idea.gradle.project.sync.setup.post.ProjectCleanupStep;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.IdeSdks;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

import static com.android.tools.idea.gradle.util.GradleUtil.getAndroidProject;
import static com.android.tools.idea.gradle.util.GradleUtil.getNativeAndroidProject;
import static com.android.tools.idea.gradle.util.Projects.getModuleCompiledArtifact;

public class ProjectStructureCleanupStep extends ProjectCleanupStep {
  @NotNull private final AndroidSdks myAndroidSdks;
  @NotNull private final DependenciesExtractor myDependenciesExtractor;
  @NotNull private final DependenciesModuleSetupStep myDependenciesModuleSetupStep;

  @SuppressWarnings("unused") // Instantiated by IDEA
  public ProjectStructureCleanupStep(@NotNull AndroidSdks androidSdks, @NotNull DependenciesExtractor dependenciesExtractor) {
    this(androidSdks, dependenciesExtractor, DependenciesModuleSetupStep.getInstance());
  }

  @VisibleForTesting
  ProjectStructureCleanupStep(@NotNull AndroidSdks androidSdks,
                              @NotNull DependenciesExtractor dependenciesExtractor,
                              @NotNull DependenciesModuleSetupStep dependenciesModuleSetupStep) {
    myAndroidSdks = androidSdks;
    myDependenciesExtractor = dependenciesExtractor;
    myDependenciesModuleSetupStep = dependenciesModuleSetupStep;
  }

  @Override
  public void cleanUpProject(@NotNull Project project,
                             @NotNull IdeModifiableModelsProvider ideModifiableModelsProvider,
                             @Nullable ProgressIndicator indicator) {
    Set<Sdk> androidSdks = new HashSet<>();

    for (Module module : ideModifiableModelsProvider.getModules()) {
      ModifiableRootModel rootModel = ideModifiableModelsProvider.getModifiableRootModel(module);
      adjustInterModuleDependencies(module, ideModifiableModelsProvider);

      Sdk sdk = rootModel.getSdk();
      if (sdk != null) {
        if (myAndroidSdks.isAndroidSdk(sdk)) {
          androidSdks.add(sdk);
        }
        continue;
      }

      NativeAndroidProject nativeAndroidProject = getNativeAndroidProject(module);
      if (nativeAndroidProject != null) {
        // Native modules does not need any jdk entry.
        continue;
      }

      Sdk jdk = IdeSdks.getInstance().getJdk();
      rootModel.setSdk(jdk);
    }

    for (Sdk sdk : androidSdks) {
      ExternalSystemApiUtil.executeProjectChangeAction(new DisposeAwareProjectChange(project) {
        @Override
        public void execute() {
          myAndroidSdks.refreshLibrariesIn(sdk);
        }
      });
    }
  }


  private void adjustInterModuleDependencies(@NotNull Module module, @NotNull IdeModifiableModelsProvider modelsProvider) {
    // Verifies that inter-module dependencies between Android modules are correctly set. If module A depends on module B, and module B
    // does not contain sources but exposes an AAR as an artifact, the IDE should set the dependency in the 'exploded AAR' instead of trying
    // to find the library in module B. The 'exploded AAR' is in the 'build' folder of module A.
    // See: https://code.google.com/p/android/issues/detail?id=162634
    AndroidProject androidProject = getAndroidProject(module);
    if (androidProject == null) {
      return;
    }
    updateAarDependencies(module, modelsProvider, androidProject);
  }

  // See: https://code.google.com/p/android/issues/detail?id=163888
  private void updateAarDependencies(@NotNull Module module,
                                     @NotNull IdeModifiableModelsProvider modelsProvider,
                                     @NotNull AndroidProject androidProject) {
    ModifiableRootModel modifiableModel = modelsProvider.getModifiableRootModel(module);
    for (Module dependency : modifiableModel.getModuleDependencies()) {
      updateTransitiveDependencies(module, modelsProvider, androidProject, dependency);
    }
  }

  // See: https://code.google.com/p/android/issues/detail?id=213627
  private void updateTransitiveDependencies(@NotNull Module module,
                                            @NotNull IdeModifiableModelsProvider ideModifiableModelsProvider,
                                            @NotNull AndroidProject androidProject,
                                            @Nullable Module dependency) {
    if (dependency == null) {
      return;
    }

    JavaFacet javaFacet = JavaFacet.getInstance(dependency);
    if (javaFacet != null
        // BUILDABLE == false -> means this is an AAR-based module, not a regular Java lib module
        && javaFacet.getConfiguration().BUILDABLE) {
      // Ignore Java lib modules. They are already set up properly.
      return;
    }
    AndroidProject dependencyAndroidProject = getAndroidProject(dependency);
    if (dependencyAndroidProject != null) {
      AndroidModuleModel androidModel = AndroidModuleModel.get(dependency);
      if (androidModel != null) {
        DependencySet dependencies = myDependenciesExtractor.extractFrom(androidModel);

        for (LibraryDependency libraryDependency : dependencies.onLibraries()) {
          myDependenciesModuleSetupStep.updateLibraryDependency(module, ideModifiableModelsProvider, libraryDependency,
                                                                androidModel.getAndroidProject());
        }

        Project project = module.getProject();
        for (ModuleDependency moduleDependency : dependencies.onModules()) {
          Module module1 = moduleDependency.getModule(project);
          updateTransitiveDependencies(module, ideModifiableModelsProvider, androidProject, module1);
        }
      }
    }
    else {
      LibraryDependency backup = getModuleCompiledArtifact(dependency);
      if (backup != null) {
        myDependenciesModuleSetupStep.updateLibraryDependency(module, ideModifiableModelsProvider, backup, androidProject);
      }
    }
  }
}
