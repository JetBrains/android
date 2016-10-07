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
package com.android.tools.idea.res;

import com.intellij.ProjectTopics;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/**
 * Service that subscribes to project root changes in order to update ResourceRepository roots.
 * Also invalidates the ResourceFolderManager cache upon changes.
 */
public class ProjectResourceRepositoryRootListener {

  private ProjectResourceRepositoryRootListener(@NotNull final Project project) {
    project.getMessageBus().connect(project).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        moduleRootsChanged(project);
      }
    });
  }

  public static void ensureSubscribed(@NotNull Project project) {
    ServiceManager.getService(project, ProjectResourceRepositoryRootListener.class);
  }

  /**
   * Called when module roots have changed in the given project. Locates all
   * the {@linkplain ProjectResourceRepository} instances (but only those that
   * have already been initialized) and updates the roots, if necessary.
   *
   * @param project the project whose module roots changed.
   */
  private static void moduleRootsChanged(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      moduleRootsChanged(module);
    }
  }

  /**
   * Called when module roots have changed in the given module. Locates the
   * {@linkplain ProjectResourceRepository} instance (but only if it has
   * already been initialized) and updates its roots, if necessary.
   *
   * @param module the module whose roots changed
   */
  private static void moduleRootsChanged(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null) {
      if (facet.requiresAndroidModel() && facet.getAndroidModel() == null) {
        // Project not yet fully initialized; no need to do a sync now because our
        // GradleProjectAvailableListener will be called as soon as it is and do a proper sync
        return;
      }
      facet.getResourceFolderManager().invalidate();
      ProjectResourceRepository projectResources = ProjectResourceRepository.getProjectResources(facet, false);
      if (projectResources != null) {
        projectResources.updateRoots();

        AppResourceRepository appResources = AppResourceRepository.getAppResources(facet, false);
        if (appResources != null) {
          appResources.invalidateCache(projectResources);
          appResources.updateRoots();
        }
      }
    }
  }
}
