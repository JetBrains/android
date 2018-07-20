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

import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @see ResourceRepositoryManager#getProjectResources(boolean)
 */
final class ProjectResourceRepository extends MultiResourceRepository {
  private AndroidFacet myFacet;

  private ProjectResourceRepository(@NotNull AndroidFacet facet, @NotNull List<? extends LocalResourceRepository> delegates) {
    super(facet.getModule().getName() + " with modules");
    myFacet = facet;
    setChildren(delegates);
  }

  @NotNull
  public static ProjectResourceRepository create(@NotNull AndroidFacet facet) {
    List<LocalResourceRepository> resources = computeRepositories(facet);
    ProjectResourceRepository repository = new ProjectResourceRepository(facet, resources);

    ProjectResourceRepositoryRootListener.ensureSubscribed(facet.getModule().getProject());
    return repository;
  }

  @NotNull
  private static List<LocalResourceRepository> computeRepositories(@NotNull AndroidFacet facet) {
    LocalResourceRepository main = ResourceRepositoryManager.getModuleResources(facet);

    // List of module facets the given module depends on
    List<AndroidFacet> dependentFacets = AndroidUtils.getAllAndroidDependencies(facet.getModule(), true);
    if (dependentFacets.isEmpty()) {
      return Collections.singletonList(main);
    }

    List<LocalResourceRepository> resources = new ArrayList<>(dependentFacets.size() + 1);
    // Add the dependent facets in reverse order to the overrides are handled correctly. Resources in n + 1 will override elements in n.
    for (int i = dependentFacets.size(); --i >= 0;) {
      resources.add(ResourceRepositoryManager.getModuleResources(dependentFacets.get(i)));
    }
    resources.add(main);

    return resources;
  }

  void updateRoots() {
    List<LocalResourceRepository> repositories = computeRepositories(myFacet);
    updateRoots(repositories);
  }

  private void updateRoots(@NotNull List<LocalResourceRepository> resourceDirectories) {
    invalidateResourceDirs();
    // If nothing changed (including order), then nothing remaining to do.
    if (!resourceDirectories.equals(getChildren())) {
      setChildren(resourceDirectories);
    }
  }

  @Override
  public void dispose() {
    myFacet = null;
    super.dispose();
  }

  @VisibleForTesting
  @NotNull
  static ProjectResourceRepository createForTest(@NotNull AndroidFacet facet, @NotNull List<LocalResourceRepository> modules) {
    return new ProjectResourceRepository(facet, modules);
  }
}
