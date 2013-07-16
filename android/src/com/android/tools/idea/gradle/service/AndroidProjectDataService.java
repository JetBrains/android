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
package com.android.tools.idea.gradle.service;

import com.android.tools.idea.gradle.AndroidProjectKeys;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.customizer.*;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataService;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Service that sets an Android SDK and facets to the modules of a project that has been imported from an Android-Gradle project.
 */
public class AndroidProjectDataService implements ProjectDataService<IdeaAndroidProject, Void> {
  private final ModuleCustomizer[] myCustomizers;

  // This constructor is called by the IDE. See this module's plugin.xml file, implementation of extension 'externalProjectDataService'.
  public AndroidProjectDataService() {
    myCustomizers =
      new ModuleCustomizer[]{
        new AndroidSdkModuleCustomizer(), new AndroidFacetModuleCustomizer(), new RunConfigModuleCustomizer(),
        new ContentRootModuleCustomizer(), new CompilerOutputPathModuleCustomizer()
      };
  }

  @VisibleForTesting
  AndroidProjectDataService(@NotNull ModuleCustomizer...customizers) {
    myCustomizers = customizers;
  }

  @NotNull
  @Override
  public Key<IdeaAndroidProject> getTargetDataKey() {
    return AndroidProjectKeys.IDE_ANDROID_PROJECT;
  }

  /**
   * Sets an Android SDK and facets to the modules of a project that has been imported from an Android-Gradle project.
   *
   * @param toImport    contains the Android-Gradle project.
   * @param project     IDEA project to configure.
   * @param synchronous indicates whether this operation is synchronous.
   */
  @Override
  public void importData(@NotNull final Collection<DataNode<IdeaAndroidProject>> toImport,
                         @NotNull final Project project,
                         boolean synchronous) {
    if (toImport.isEmpty()) {
      return;
    }

    final List<Module> modules = ImmutableList.copyOf(ModuleManager.getInstance(project).getModules());

    ExternalSystemApiUtil.executeProjectChangeAction(synchronous, new Runnable() {
      @Override
      public void run() {
        Map<String, IdeaAndroidProject> androidProjectsByModuleName = indexByModuleName(toImport);
        for (Module module : modules) {
          IdeaAndroidProject androidProject = androidProjectsByModuleName.get(module.getName());
          customizeModule(module, project, androidProject);
        }
      }
    });
  }

  @NotNull
  private static Map<String, IdeaAndroidProject> indexByModuleName(@NotNull Collection<DataNode<IdeaAndroidProject>> dataNodes) {
    Map<String, IdeaAndroidProject> index = Maps.newHashMap();
    for (DataNode<IdeaAndroidProject> d : dataNodes) {
      IdeaAndroidProject androidProject = d.getData();
      index.put(androidProject.getModuleName(), androidProject);
    }
    return index;
  }

  private void customizeModule(@NotNull Module module, @NotNull Project project, @Nullable IdeaAndroidProject ideaAndroidProject) {
    for (ModuleCustomizer customizer : myCustomizers) {
      customizer.customizeModule(module, project, ideaAndroidProject);
    }
  }

  @Override
  public void removeData(@NotNull Collection<? extends Void> toRemove, @NotNull Project project, boolean synchronous) {
  }
}
