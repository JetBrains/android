/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.service;

import com.android.tools.idea.gradle.AndroidProjectKeys;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.IdeaJavaProject;
import com.android.tools.idea.gradle.customizer.ModuleCustomizer;
import com.android.tools.idea.gradle.customizer.java.*;
import com.android.tools.idea.gradle.messages.Message;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.RunResult;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataService;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.PROJECT_STRUCTURE_ISSUES;
import static com.android.tools.idea.gradle.messages.Message.Type.ERROR;

public class JavaProjectDataService implements ProjectDataService<IdeaJavaProject, Void> {
  private static final Logger LOG = Logger.getInstance(JavaProjectDataService.class);

  private final List<ModuleCustomizer<IdeaJavaProject>> myCustomizers =
    ImmutableList.of(new JavaLanguageLevelModuleCustomizer(), new ContentRootModuleCustomizer(), new DependenciesModuleCustomizer(),
                     new CompilerOutputModuleCustomizer(), new ArtifactsByConfigurationModuleCustomizer());

  @Override
  @NotNull
  public Key<IdeaJavaProject> getTargetDataKey() {
    return AndroidProjectKeys.IDE_JAVA_PROJECT;
  }

  @Override
  public void importData(@NotNull Collection<DataNode<IdeaJavaProject>> toImport, @NotNull Project project, boolean synchronous) {
    if (!toImport.isEmpty()) {
      try {
        doImport(toImport, project);
      } catch (Throwable e) {
        LOG.error(String.format("Failed to set up Java modules in project '%1$s'", project.getName()), e);
        GradleSyncState.getInstance(project).syncFailed(e.getMessage());
      }
    }
  }

  private void doImport(final Collection<DataNode<IdeaJavaProject>> toImport, final Project project) throws Throwable {
    RunResult result = new WriteCommandAction.Simple(project) {
      @Override
      protected void run() throws Throwable {
        if (!project.isDisposed()) {
          ModuleManager moduleManager = ModuleManager.getInstance(project);
          Map<String, IdeaJavaProject> gradleProjectsByName = indexByModuleName(toImport);
          for (Module module : moduleManager.getModules()) {
            IdeaJavaProject javaProject = gradleProjectsByName.get(module.getName());
            if (javaProject != null) {
              customizeModule(module, javaProject);
            }
          }
        }
      }
    }.execute();
    Throwable error = result.getThrowable();
    if (error != null) {
      throw error;
    }
  }

  @NotNull
  private static Map<String, IdeaJavaProject> indexByModuleName(@NotNull Collection<DataNode<IdeaJavaProject>> dataNodes) {
    Map<String, IdeaJavaProject> javaProjectsByModuleName = Maps.newHashMap();
    for (DataNode<IdeaJavaProject> d : dataNodes) {
      IdeaJavaProject javaProject = d.getData();
      javaProjectsByModuleName.put(javaProject.getModuleName(), javaProject);
    }
    return javaProjectsByModuleName;
  }

  private void customizeModule(@NotNull Module module, @NotNull IdeaJavaProject javaProject) {
    if (javaProject.isAndroidProjectWithoutVariants()) {
      // See https://code.google.com/p/android/issues/detail?id=170722
      ProjectSyncMessages messages = ProjectSyncMessages.getInstance(module.getProject());
      String[] text = {
        String.format("The module '%1$s' is an Android project without build variants, and cannot be built.", module.getName()),
        "Please fix the module's configuration in the build.gradle file and sync the project again.",
      };
      messages.add(new Message(PROJECT_STRUCTURE_ISSUES, ERROR, text));
      cleanUpAndroidModuleWithoutVariants(module);
      // No need to setup source folders, dependencies, etc. Since the Android project does not have variants, and because this can
      // happen due to a project configuration error and there is a lot of module configuration missng, there is no point on even trying.
      return;
    }
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();
    try{
      for (ModuleCustomizer<IdeaJavaProject> customizer : myCustomizers) {
        customizer.customizeModule(module.getProject(), rootModel, javaProject);
      }
    }
    finally {
      rootModel.commit();
    }
  }

  private static void cleanUpAndroidModuleWithoutVariants(@NotNull Module module) {
    // Remove Android facet, otherwise the IDE will try to build the module, and fail. The facet may have been added in a previous
    // successful commit.
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null) {
      ModifiableFacetModel facetModel = FacetManager.getInstance(module).createModifiableModel();
      facetModel.removeFacet(facet);
      facetModel.commit();
    }

    // Clear all source and exclude folders.
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();
    for (ContentEntry contentEntry : rootModel.getContentEntries()) {
      contentEntry.clearSourceFolders();
      contentEntry.clearExcludeFolders();
    }

    rootModel.commit();
  }

  @Override
  public void removeData(@NotNull Collection<? extends Void> toRemove, @NotNull Project project, boolean synchronous) {
  }
}
