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
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Resource repository for a module along with all its (local) module dependencies.
 *
 * <p>It doesn't contain resources from AAR dependencies.
 *
 * <p>An example of where this is useful is the layout editor; in its “Language” menu it lists all the relevant languages in the project and
 * lets you choose between them. Here we don’t want to include resources from libraries; If you depend on Google Play Services, and it
 * provides 40 translations for its UI, we don’t want to show all 40 languages in the language menu, only the languages actually locally in
 * the user’s source code.
 */
public final class ProjectResourceRepository extends MultiResourceRepository {
  private AndroidFacet myFacet;

  @Nullable
  public static ProjectResourceRepository getOrCreateInstance(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet != null ? getOrCreateInstance(facet) : null;
  }

  @NotNull
  public static ProjectResourceRepository getOrCreateInstance(@NotNull AndroidFacet facet) {
    return findProjectResources(facet, true);
  }

  @Nullable
  public static ProjectResourceRepository findExistingInstance(@NotNull AndroidFacet facet) {
    return findProjectResources(facet, false);
  }

  @Contract("_, true -> !null")
  @Nullable
  private static ProjectResourceRepository findProjectResources(@NotNull AndroidFacet facet, boolean createIfNecessary) {
    return ResourceRepositoryManager.getOrCreateInstance(facet).getProjectResources(createIfNecessary);
  }

  private ProjectResourceRepository(@NotNull AndroidFacet facet, @NotNull List<? extends LocalResourceRepository> delegates) {
    super(facet.getModule().getName() + " with modules", delegates);
    myFacet = facet;
  }

  @NotNull
  public static ProjectResourceRepository create(@NotNull AndroidFacet facet) {
    List<LocalResourceRepository> resources = computeRepositories(facet);
    final ProjectResourceRepository repository = new ProjectResourceRepository(facet, resources);

    ProjectResourceRepositoryRootListener.ensureSubscribed(facet.getModule().getProject());
    return repository;
  }

  private static List<LocalResourceRepository> computeRepositories(@NotNull final AndroidFacet facet) {
    LocalResourceRepository main = ModuleResourceRepository.getOrCreateInstance(facet);

    // List of module facets the given module depends on
    List<AndroidFacet> dependentFacets = AndroidUtils.getAllAndroidDependencies(facet.getModule(), true);
    if (dependentFacets.isEmpty()) {
      return Collections.singletonList(main);
    }

    List<LocalResourceRepository> resources = Lists.newArrayListWithCapacity(dependentFacets.size() + 1);
    // Add the dependent facets in reverse order to the overrides are handled correctly. Resources in n + 1 will override elements in n
    for (int i = dependentFacets.size() - 1; i >= 0; i--) {
      resources.add(ModuleResourceRepository.getOrCreateInstance(dependentFacets.get(i)));
    }
    resources.add(main);

    return resources;
  }

  @VisibleForTesting
  void updateRoots() {
    List<LocalResourceRepository> repositories = computeRepositories(myFacet);
    updateRoots(repositories);
  }

  private void updateRoots(List<LocalResourceRepository> resourceDirectories) {
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
  static ProjectResourceRepository createForTest(AndroidFacet facet, List<LocalResourceRepository> modules) {
    return new ProjectResourceRepository(facet, modules);
  }
}
