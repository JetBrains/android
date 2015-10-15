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
package com.android.tools.idea.rendering;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/** Resource repository for a module along with all its module dependencies */
public final class ProjectResourceRepository extends MultiResourceRepository {
  private final AndroidFacet myFacet;

  private ProjectResourceRepository(@NotNull AndroidFacet facet, @NotNull List<? extends LocalResourceRepository> delegates) {
    super(facet.getModule().getName() + " with modules", delegates);
    myFacet = facet;
  }

  /**
   * Returns the Android resources for this module and any modules it depends on, but not resources in any libraries
   *
   * @param module the module to look up resources for
   * @param createIfNecessary if true, create the app resources if necessary, otherwise only return if already computed
   * @return the resource repository
   */
  @Nullable
  public static ProjectResourceRepository getProjectResources(@NotNull Module module, boolean createIfNecessary) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null) {
      return facet.getProjectResources(createIfNecessary);
    }

    return null;
  }

  /**
   * Returns the Android resources for this module and any modules it depends on, but not resources in any libraries
   *
   * @param facet the module facet to look up resources for
   * @param createIfNecessary if true, create the app resources if necessary, otherwise only return if already computed
   * @return the resource repository
   */
  @Contract("!null, true -> !null")
  @Nullable
  public static ProjectResourceRepository getProjectResources(@NotNull AndroidFacet facet, boolean createIfNecessary) {
    return facet.getProjectResources(createIfNecessary);
  }

  @NotNull
  public static ProjectResourceRepository create(@NotNull final AndroidFacet facet) {
    List<LocalResourceRepository> resources = computeRepositories(facet);
    final ProjectResourceRepository repository = new ProjectResourceRepository(facet, resources);

    // TODO: Avoid this in non-Gradle projects?
    facet.addListener(new GradleSyncListener.Adapter() {
      @Override
      public void syncSucceeded(@NotNull Project project) {
        // Dependencies can change when we sync with Gradle
        repository.updateRoots();
      }
    });

    return repository;
  }

  private static List<LocalResourceRepository> computeRepositories(@NotNull final AndroidFacet facet) {
    LocalResourceRepository main = ModuleResourceRepository.getModuleResources(facet, true);

    // List of module facets the given module depends on
    List<AndroidFacet> dependentFacets = AndroidUtils.getAllAndroidDependencies(facet.getModule(), true);
    if (dependentFacets.isEmpty()) {
      return Collections.singletonList(main);
    }

    List<LocalResourceRepository> resources = Lists.newArrayListWithExpectedSize(dependentFacets.size());

    for (AndroidFacet f : dependentFacets) {
      LocalResourceRepository r = ModuleResourceRepository.getModuleResources(f, true);
      resources.add(r);
    }

    resources.add(main);

    return resources;
  }

  void updateRoots() {
    List<LocalResourceRepository> repositories = computeRepositories(myFacet);
    updateRoots(repositories);
  }

  @VisibleForTesting
  void updateRoots(List<LocalResourceRepository> resourceDirectories) {
    if (resourceDirectories.equals(myChildren)) {
      // Nothing changed (including order); nothing to do
      return;
    }

    setChildren(resourceDirectories);
  }

  /**
   * Called when module roots have changed in the given project. Locates all
   * the {@linkplain ProjectResourceRepository} instances (but only those that
   * have already been initialized) and updates the roots, if necessary.
   *
   * @param project the project whose module roots changed.
   */
  public static void moduleRootsChanged(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      moduleRootsChanged(module);
    }
  }

  /**
   * Called when module roots have changed in the given module. Locates the
   * {@linkplain ProjectResourceRepository} instance (but only if it has
   * already been initialized) and updates its roots, if necessary.
   * <p>
   * TODO: Currently, this method is only called during a Gradle project import.
   * We should call it for non-Gradle projects after modules are changed in the
   * project structure dialog etc. with
   *   project.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() { ... }
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
      ProjectResourceRepository projectResources = getProjectResources(facet, false);
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

  @VisibleForTesting
  @NotNull
  static ProjectResourceRepository createForTest(AndroidFacet facet, List<LocalResourceRepository> modules) {
    return new ProjectResourceRepository(facet, modules);
  }
}
