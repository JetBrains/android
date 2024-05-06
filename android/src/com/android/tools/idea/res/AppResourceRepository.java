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

import com.android.resources.aar.AarResourceRepository;
import com.android.tools.rendering.classloading.ModuleClassLoaderManager;
import com.android.tools.res.LocalResourceRepository;
import com.android.tools.res.ids.ResourceIdManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * @see StudioResourceRepositoryManager#getAppResources()
 */
class AppResourceRepository extends MemoryTrackingMultiResourceRepository {
  private final AndroidFacet myFacet;
  private final Object RESOURCE_MAP_LOCK = new Object();

  /**
   * Resource directories. Computed lazily.
   */
  @Nullable private Collection<VirtualFile> myResourceDirs;

  @NotNull
  static AppResourceRepository create(@NotNull AndroidFacet facet, @NotNull Collection<AarResourceRepository> libraryRepositories,
                                      @NotNull LocalResourceRepository<VirtualFile> sampleDataResourceRepository) {
    AppResourceRepository repository =
        new AppResourceRepository(facet, computeLocalRepositories(facet, sampleDataResourceRepository), libraryRepositories);
    AndroidProjectRootListener.ensureSubscribed(facet.getModule().getProject());

    return repository;
  }

  @NotNull
  Collection<VirtualFile> getAllResourceDirs() {
    synchronized (RESOURCE_MAP_LOCK) {
      if (myResourceDirs == null) {
        ImmutableList.Builder<VirtualFile> result = ImmutableList.builder();
        for (LocalResourceRepository<VirtualFile> resourceRepository : getLocalResources()) {
          result.addAll(resourceRepository.getResourceDirs());
        }
        myResourceDirs = result.build();
      }
      return myResourceDirs;
    }
  }

  private static List<LocalResourceRepository<VirtualFile>> computeLocalRepositories(
      @NotNull AndroidFacet facet, @NotNull LocalResourceRepository<VirtualFile> sampleDataResourceRepository) {
    return ImmutableList.of(StudioResourceRepositoryManager.getProjectResources(facet), sampleDataResourceRepository);
  }

  private AppResourceRepository(@NotNull AndroidFacet facet,
                                @NotNull List<LocalResourceRepository<VirtualFile>> localResources,
                                @NotNull Collection<AarResourceRepository> libraryResources) {
    super(facet.getModule().getName() + " with modules and libraries");
    myFacet = facet;
    setChildren(localResources, libraryResources, ImmutableList.of(PredefinedSampleDataResourceRepository.getInstance()));
  }

  void updateRoots(@NotNull Collection<? extends AarResourceRepository> libraryResources,
                   @NotNull LocalResourceRepository<VirtualFile> sampleDataResourceRepository) {
    List<LocalResourceRepository<VirtualFile>> localResources = computeLocalRepositories(myFacet, sampleDataResourceRepository);
    updateRoots(localResources, libraryResources);
  }

  @VisibleForTesting
  void updateRoots(@NotNull List<? extends LocalResourceRepository<VirtualFile>> localResources,
                   @NotNull Collection<? extends AarResourceRepository> libraryResources) {
    synchronized (RESOURCE_MAP_LOCK) {
      myResourceDirs = null;
    }
    invalidateResourceDirs();
    setChildren(localResources, libraryResources, ImmutableList.of(PredefinedSampleDataResourceRepository.getInstance()));

    // Clear the fake R class cache and the ModuleClassLoader cache.
    Module module = myFacet.getModule();
    ResourceIdManager.get(module).resetDynamicIds();
    ResourceClassRegistry.get(module.getProject()).clearCache();
    ModuleClassLoaderManager.get().clearCache(module);
  }

  @TestOnly
  @NotNull
  static AppResourceRepository createForTest(@NotNull AndroidFacet facet,
                                             @NotNull List<LocalResourceRepository<VirtualFile>> modules,
                                             @NotNull Collection<AarResourceRepository> libraries) {
    AppResourceRepository repository = new AppResourceRepository(facet, modules, libraries);
    Disposer.register(facet, repository);
    return repository;
  }
}
