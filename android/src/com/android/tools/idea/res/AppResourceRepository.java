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
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.util.PathString;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.aar.AarSourceResourceRepository;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.ModuleClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @see ResourceRepositoryManager#getAppResources(boolean)
 */
class AppResourceRepository extends MultiResourceRepository {
  private static final Logger LOG = Logger.getInstance(AppResourceRepository.class);
  static final Key<Boolean> TEMPORARY_RESOURCE_CACHE = Key.create("TemporaryResourceCache");

  private final AndroidFacet myFacet;
  private Collection<AarSourceResourceRepository> myLibraries;
  private long myIdsModificationCount;
  private ListMultimap<String, ResourceItem> myIds;

  private final Object RESOURCE_MAP_LOCK = new Object();

  /**
   * Map from library name to resource dirs.
   * The key library name may be null.
   */
  @Nullable private Multimap<String, VirtualFile> myResourceDirMap;

  @NotNull
  static AppResourceRepository create(@NotNull AndroidFacet facet, @NotNull List<AarSourceResourceRepository> libraryRepositories) {
    AppResourceRepository repository =
        new AppResourceRepository(facet, computeRepositories(facet, libraryRepositories), libraryRepositories);
    ProjectResourceRepositoryRootListener.ensureSubscribed(facet.getModule().getProject());

    return repository;
  }

  @NotNull
  Multimap<String, VirtualFile> getAllResourceDirs() {
    synchronized (RESOURCE_MAP_LOCK) {
      if (myResourceDirMap == null) {
        myResourceDirMap = HashMultimap.create();
        for (LocalResourceRepository resourceRepository : getChildren()) {
          myResourceDirMap.putAll(resourceRepository.getLibraryName(), resourceRepository.getResourceDirs());
        }
      }
      return myResourceDirMap;
    }
  }

  private static List<LocalResourceRepository> computeRepositories(@NotNull AndroidFacet facet,
                                                                   @NotNull Collection<AarSourceResourceRepository> libraryRepositories) {
    List<LocalResourceRepository> repositories = new ArrayList<>(libraryRepositories.size() + 2);
    repositories.addAll(libraryRepositories);
    repositories.add(ResourceRepositoryManager.getProjectResources(facet));
    repositories.add(SampleDataResourceRepository.getInstance(facet));
    return repositories;
  }

  protected AppResourceRepository(@NotNull AndroidFacet facet,
                                  @NotNull List<? extends LocalResourceRepository> delegates,
                                  @NotNull Collection<AarSourceResourceRepository> libraries) {
    super(facet.getModule().getName() + " with modules and libraries");
    myFacet = facet;
    myLibraries = libraries;
    setChildren(delegates);
  }

  /**
   * Returns the names of all resources of type {@link ResourceType#ID} available in a non-namespaced application.
   * This means reading the R.txt files from libraries, as we don't scan layouts etc. in AARs for "@+id" declarations.
   *
   * TODO(namespaces): remove the dependency on R.txt
   */
  @NotNull
  private ListMultimap<String, ResourceItem> getAllIds(@NotNull ResourceNamespace namespace) {
    long currentModCount = getModificationCount();
    if (myIdsModificationCount < currentModCount) {
      myIdsModificationCount = currentModCount;
      if (myIds == null) {
        int size = 0;
        for (AarSourceResourceRepository library : myLibraries) {
          if (library.getAllDeclaredIds() != null) {
            size += library.getAllDeclaredIds().size();
          }
        }
        myIds = ArrayListMultimap.create(size, 1);
      }
      else {
        myIds.clear();
      }
      for (AarSourceResourceRepository library : myLibraries) {
        if (library.getAllDeclaredIds() != null) {
          for (String name : library.getAllDeclaredIds().keySet()) {
            myIds.put(name, new IdResourceItem(name));
          }
        }
      }
      // Also add all ids from resource types, just in case it contains things that are not in the libraries.
      myIds.putAll(super.getResources(namespace, ResourceType.ID));
    }
    return myIds;
  }

  @Override
  @NotNull
  public ListMultimap<String, ResourceItem> getResources(@NotNull ResourceNamespace namespace, @NotNull ResourceType type) {
    synchronized (ITEM_MAP_LOCK) {
      // TODO(namespaces): store all ID resources in the repositories and stop reading R.txt.
      return type == ResourceType.ID && namespace == ResourceNamespace.RES_AUTO ?
             getAllIds(namespace) : super.getResources(namespace, type);
    }
  }

  void updateRoots(@NotNull Collection<AarSourceResourceRepository> libraries) {
    List<LocalResourceRepository> repositories = computeRepositories(myFacet, libraries);
    updateRoots(repositories, libraries);
  }

  @VisibleForTesting
  void updateRoots(@NotNull List<LocalResourceRepository> resources, @NotNull Collection<AarSourceResourceRepository> libraries) {
    synchronized (RESOURCE_MAP_LOCK) {
      myResourceDirMap = null;
    }
    invalidateResourceDirs();

    if (resources.equals(getChildren())) {
      // Nothing changed (including order); nothing to do.
      return;
    }

    myLibraries = libraries;
    setChildren(resources);

    // Clear the fake R class cache and the ModuleClassLoader cache.
    ResourceIdManager.get(myFacet.getModule()).resetDynamicIds();
    ModuleClassLoader.clearCache(myFacet.getModule());
  }

  @VisibleForTesting
  @NotNull
  static AppResourceRepository createForTest(@NotNull AndroidFacet facet,
                                             @NotNull List<LocalResourceRepository> modules,
                                             @NotNull Collection<AarSourceResourceRepository> libraries) {
    assert modules.containsAll(libraries);
    assert modules.size() == libraries.size() + 1; // Should only combine with the module set repository.
    return new AppResourceRepository(facet, modules, libraries);
  }

  private static class IdResourceItem implements ResourceItem {
    private final String myName;

    IdResourceItem(@NotNull String name) {
      myName = name;
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @NotNull
    @Override
    public ResourceType getType() {
      return ResourceType.ID;
    }

    @NotNull
    @Override
    public ResourceNamespace getNamespace() {
      return ResourceNamespace.RES_AUTO;
    }

    @Nullable
    @Override
    public String getLibraryName() {
      return null;
    }

    @NotNull
    @Override
    public ResourceReference getReferenceToSelf() {
      return new ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, myName);
    }

    @NotNull
    @Override
    public String getKey() {
      return myName;
    }

    @Nullable
    @Override
    public ResourceValue getResourceValue() {
      return null;
    }

    @Nullable
    @Override
    public PathString getSource() {
      return null;
    }

    @Override
    public boolean isFileBased() {
      return false;
    }

    @NotNull
    @Override
    public FolderConfiguration getConfiguration() {
      return FolderConfiguration.createDefault();
    }
  }
}
