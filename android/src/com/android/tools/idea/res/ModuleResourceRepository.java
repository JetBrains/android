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

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.res2.ResourceTable;
import com.android.resources.ResourceType;
import com.google.common.collect.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Resource repository for a single module (which can possibly have multiple resource folders). Does not include
 * resources from any dependencies.
 */
public final class ModuleResourceRepository extends MultiResourceRepository {
  private final AndroidFacet myFacet;
  private final ResourceFolderManager.ResourceFolderListener myResourceFolderListener = (facet, folders, added, removed) -> updateRoots();

  /**
   * Returns the Android resources specific to this module, not including resources in any
   * dependent modules or any AAR libraries, create the app resources if necessary.
   *
   * @param module the module to look up resources for
   * @return the resource repository
   */
  @Nullable
  public static LocalResourceRepository getOrCreateInstance(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet != null ? getOrCreateInstance(facet) : null;
  }

  @NotNull
  public static LocalResourceRepository getOrCreateInstance(@NotNull AndroidFacet facet) {
    return findModuleResources(facet, true);
  }

  @Nullable
  public static LocalResourceRepository findExistingInstance(@NotNull AndroidFacet facet) {
    return findModuleResources(facet, false);
  }

  @Contract("_, true -> !null")
  @Nullable
  private static LocalResourceRepository findModuleResources(@NotNull AndroidFacet facet, boolean createIfNecessary) {
    ResourceRepositories repositories = ResourceRepositories.getOrCreateInstance(facet);
    return repositories.getModuleResources(createIfNecessary);
  }

  /**
   * Creates a new resource repository for the given module, <b>not</b> including its dependent modules.
   *
   * @param facet the facet for the module
   * @return the resource repository
   */
  @NotNull
  static LocalResourceRepository create(@NotNull AndroidFacet facet) {
    if (!facet.requiresAndroidModel()) {
      // Always just a single resource folder: simple
      VirtualFile primaryResourceDir = facet.getPrimaryResourceDir();
      if (primaryResourceDir == null) {
        return new EmptyRepository();
      }
      return ResourceFolderRegistry.get(facet, primaryResourceDir);
    }

    ResourceFolderManager folderManager = facet.getResourceFolderManager();
    List<VirtualFile> resourceDirectories = folderManager.getFolders();
    List<LocalResourceRepository> resources = Lists.newArrayListWithExpectedSize(resourceDirectories.size() + 1);
    for (VirtualFile resourceDirectory : resourceDirectories) {
      ResourceFolderRepository repository = ResourceFolderRegistry.get(facet, resourceDirectory);
      resources.add(repository);
    }

    DynamicResourceValueRepository dynamicResources = DynamicResourceValueRepository.create(facet);
    resources.add(dynamicResources);

    // We create a ModuleResourceRepository even if resources.isEmpty(), because we may
    // dynamically add children to it later (in updateRoots)
    final ModuleResourceRepository repository = new ModuleResourceRepository(facet, resources);
    Disposer.register(repository, dynamicResources);

    return repository;
  }

  private ModuleResourceRepository(@NotNull AndroidFacet facet, @NotNull List<? extends LocalResourceRepository> delegates) {
    super(facet.getModule().getName(), delegates);
    myFacet = facet;

    // Subscribe to update the roots when the resource folders change
    myFacet.getResourceFolderManager().addListener(myResourceFolderListener);
  }

  private void updateRoots() {
    updateRoots(myFacet.getResourceFolderManager().getFolders());
  }

  @VisibleForTesting
  void updateRoots(List<VirtualFile> resourceDirectories) {
    // Non-folder repositories: Always kept last in the list
    List<LocalResourceRepository> other = null;

    // Compute current roots
    Map<VirtualFile, ResourceFolderRepository> map = Maps.newHashMap();
    for (LocalResourceRepository repository : getChildren()) {
      if (repository instanceof ResourceFolderRepository) {
        ResourceFolderRepository folderRepository = (ResourceFolderRepository)repository;
        VirtualFile resourceDir = folderRepository.getResourceDir();
        map.put(resourceDir, folderRepository);
      }
      else {
        assert repository instanceof DynamicResourceValueRepository;
        if (other == null) {
          other = Lists.newArrayList();
        }
        other.add(repository);
      }
    }

    // Compute new resource directories (it's possible for just the order to differ, or
    // for resource dirs to have been added and/or removed)
    Set<VirtualFile> newDirs = Sets.newHashSet(resourceDirectories);
    List<LocalResourceRepository> resources = Lists.newArrayListWithExpectedSize(newDirs.size() + (other != null ? other.size() : 0));
    for (VirtualFile dir : resourceDirectories) {
      ResourceFolderRepository repository = map.get(dir);
      if (repository == null) {
        repository = ResourceFolderRegistry.get(myFacet, dir);
      }
      else {
        map.remove(dir);
      }
      resources.add(repository);
    }

    if (other != null) {
      resources.addAll(other);
    }

    if (resources.equals(getChildren())) {
      // Nothing changed (including order); nothing to do
      assert map.isEmpty(); // shouldn't have created any new ones
      return;
    }

    for (ResourceFolderRepository removed : map.values()) {
      removed.removeParent(this);
    }

    setChildren(resources);
  }

  @Override
  public void dispose() {
    super.dispose();

    myFacet.getResourceFolderManager().removeListener(myResourceFolderListener);
  }

  /**
   * For testing: creates a project with a given set of resource roots; this allows tests to check
   * this repository without creating a gradle project setup etc
   */
  @NotNull
  @VisibleForTesting
  public static ModuleResourceRepository createForTest(@NotNull AndroidFacet facet, @NotNull Collection<VirtualFile> resourceDirectories) {
    return createForTest(facet, resourceDirectories, null, null);
  }

  @NotNull
  @VisibleForTesting
  public static ModuleResourceRepository createForTest(@NotNull AndroidFacet facet,
                                                       @NotNull Collection<VirtualFile> resourceDirectories,
                                                       @Nullable String namespace,
                                                       @Nullable DynamicResourceValueRepository dynamicResourceValueRepository) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    List<LocalResourceRepository> delegates = new ArrayList<>(resourceDirectories.size() + 1);

    for (VirtualFile resourceDirectory : resourceDirectories) {
      delegates.add(ResourceFolderRegistry.get(facet, resourceDirectory, namespace));
    }

    if (dynamicResourceValueRepository != null) {
      delegates.add(dynamicResourceValueRepository);
    }
    return new ModuleResourceRepository(facet, delegates);
  }

  private static class EmptyRepository extends LocalResourceRepository {

    protected EmptyRepository() {
      super("");
    }

    @NotNull
    @Override
    protected Set<VirtualFile> computeResourceDirs() {
      return Collections.emptySet();
    }

    @NonNull
    @Override
    protected ResourceTable getFullTable() {
      return new ResourceTable();
    }

    @com.android.annotations.Nullable
    @Override
    protected ListMultimap<String, ResourceItem> getMap(@com.android.annotations.Nullable String namespace,
                                                        @NonNull ResourceType type,
                                                        boolean create) {
      return create ? ArrayListMultimap.create() : null;
    }

    @NonNull
    @Override
    public Set<String> getNamespaces() {
      return Collections.emptySet();
    }
  }
}
