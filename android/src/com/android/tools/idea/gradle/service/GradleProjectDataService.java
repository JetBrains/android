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
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.util.Facets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataService;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.gradle.tooling.model.GradleProject;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Service that stores the "Gradle project paths" of an imported Android-Gradle project.
 */
public class GradleProjectDataService implements ProjectDataService<GradleProject, Void> {
  @NotNull
  @Override
  public Key<GradleProject> getTargetDataKey() {
    return AndroidProjectKeys.GRADLE_PROJECT_KEY;
  }

  @Override
  public void importData(@NotNull final Collection<DataNode<GradleProject>> toImport,
                         @NotNull final Project project,
                         boolean synchronous) {
    if (toImport.isEmpty()) {
      return;
    }
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    final List<Module> modules = ImmutableList.copyOf(moduleManager.getModules());
    ExternalSystemApiUtil.executeProjectChangeAction(synchronous, new Runnable() {
      @Override
      public void run() {
        Map<String, GradleProject> gradleProjectsByName = indexByGradleProjectName(toImport);
        for (Module module : modules) {
          GradleProject androidProject = gradleProjectsByName.get(module.getName());
          customizeModule(module, androidProject);
        }
      }
    });
  }

  @NotNull
  private static Map<String, GradleProject> indexByGradleProjectName(@NotNull Collection<DataNode<GradleProject>> dataNodes) {
    Map<String, GradleProject> index = Maps.newHashMap();
    for (DataNode<GradleProject> d : dataNodes) {
      GradleProject gradleProject = d.getData();
      index.put(gradleProject.getName(), gradleProject);
    }
    return index;
  }

  private static void customizeModule(@NotNull Module module, @NotNull GradleProject gradleProject) {
    AndroidGradleFacet androidGradleFacet = Facets.getAndroidGradleFacet(module);
    androidGradleFacet.getConfiguration().GRADLE_PROJECT_PATH = gradleProject.getPath();
  }

  @Override
  public void removeData(@NotNull Collection<? extends Void> toRemove, @NotNull Project project, boolean synchronous) {
  }
}
