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

import com.android.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
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

    ProjectResourceRepositoryRootListener.ensureSubscribed(facet.getModule().getProject());
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
    invalidateResourceDirs();
    // If nothing changed (including order), then nothing remaining to do.
    if (!resourceDirectories.equals(myChildren)) {
      setChildren(resourceDirectories);
    }
  }

  @VisibleForTesting
  @NotNull
  static ProjectResourceRepository createForTest(AndroidFacet facet, List<LocalResourceRepository> modules) {
    return new ProjectResourceRepository(facet, modules);
  }
}
