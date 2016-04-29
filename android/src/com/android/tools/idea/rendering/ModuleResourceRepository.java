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
import com.google.common.collect.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Resource repository for a single module (which can possibly have multiple resource folders)
 */
public final class ModuleResourceRepository extends MultiResourceRepository {
  private final AndroidFacet myFacet;

  private ModuleResourceRepository(@NotNull AndroidFacet facet, @NotNull List<? extends LocalResourceRepository> delegates) {
    super(facet.getModule().getName(), delegates);
    myFacet = facet;
  }

  /**
   * Returns the Android resources specific to this module, not including resources in any
   * dependent modules or any AAR libraries
   *
   * @param module            the module to look up resources for
   * @param createIfNecessary if true, create the app resources if necessary, otherwise only return if already computed
   * @return the resource repository
   */
  @Nullable
  public static LocalResourceRepository getModuleResources(@NotNull Module module, boolean createIfNecessary) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null) {
      return facet.getModuleResources(createIfNecessary);
    }

    return null;
  }

  /**
   * Returns the Android resources specific to this module, not including resources in any
   * dependent modules or any AAR libraries
   *
   * @param facet             the module facet to look up resources for
   * @param createIfNecessary if true, create the app resources if necessary, otherwise only return if already computed
   * @return the resource repository
   */
  @Contract("!null, true -> !null")
  @Nullable
  public static LocalResourceRepository getModuleResources(@NotNull AndroidFacet facet, boolean createIfNecessary) {
    return facet.getModuleResources(createIfNecessary);
  }

  /**
   * Creates a new resource repository for the given module, <b>not</b> including its dependent modules.
   *
   * @param facet the facet for the module
   * @return the resource repository
   */
  @NotNull
  public static LocalResourceRepository create(@NotNull final AndroidFacet facet) {
    boolean gradleProject = facet.requiresAndroidModel();
    if (!gradleProject) {
      // Always just a single resource folder: simple
      VirtualFile primaryResourceDir = facet.getPrimaryResourceDir();
      if (primaryResourceDir == null) {
        return new EmptyRepository();
      }
      return ResourceFolderRegistry.get(facet, primaryResourceDir);
    }

    ResourceFolderManager folderManager = facet.getResourceFolderManager();
    List<VirtualFile> resourceDirectories = folderManager.getFolders();
    List<LocalResourceRepository> resources = Lists.newArrayListWithExpectedSize(resourceDirectories.size());
    for (VirtualFile resourceDirectory : resourceDirectories) {
      ResourceFolderRepository repository = ResourceFolderRegistry.get(facet, resourceDirectory);
      resources.add(repository);
    }

    DynamicResourceValueRepository dynamicResources = DynamicResourceValueRepository.create(facet);
    resources.add(dynamicResources);

    // We create a ModuleResourceRepository even if resources.isEmpty(), because we may
    // dynamically add children to it later (in updateRoots)
    final ModuleResourceRepository repository = new ModuleResourceRepository(facet, resources);

    // If the model is not yet ready, we may get an incomplete set of resource
    // directories, so in that case update the repository when the model is available.

    folderManager.addListener(new ResourceFolderManager.ResourceFolderListener() {
      @Override
      public void resourceFoldersChanged(@NotNull AndroidFacet facet,
                                         @NotNull List<VirtualFile> folders,
                                         @NotNull Collection<VirtualFile> added,
                                         @NotNull Collection<VirtualFile> removed) {
        repository.updateRoots();
      }
    });

    return repository;
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
    for (LocalResourceRepository repository : myChildren) {
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

    if (resources.equals(myChildren)) {
      // Nothing changed (including order); nothing to do
      assert map.isEmpty(); // shouldn't have created any new ones
      return;
    }

    for (ResourceFolderRepository removed : map.values()) {
      removed.removeParent(this);
    }

    setChildren(resources);
  }

  /**
   * For testing: creates a project with a given set of resource roots; this allows tests to check
   * this repository without creating a gradle project setup etc
   */
  @NotNull
  @VisibleForTesting
  public static ModuleResourceRepository createForTest(@NotNull AndroidFacet facet, @NotNull Collection<VirtualFile> resourceDirectories) {
    return createForTest(facet, resourceDirectories, Collections.<LocalResourceRepository>emptyList());
  }

  @NotNull
  @VisibleForTesting
  public static ModuleResourceRepository createForTest(@NotNull AndroidFacet facet,
                                                       @NotNull Collection<VirtualFile> resourceDirectories,
                                                       @NotNull Collection<LocalResourceRepository> otherDelegates) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    List<LocalResourceRepository> delegates = new ArrayList<LocalResourceRepository>(resourceDirectories.size() + otherDelegates.size());

    for (VirtualFile resourceDirectory : resourceDirectories) {
      delegates.add(ResourceFolderRegistry.get(facet, resourceDirectory));
    }

    delegates.addAll(otherDelegates);
    return new ModuleResourceRepository(facet, delegates);
  }

  private static class EmptyRepository extends MultiResourceRepository {
    public EmptyRepository() {
      super("", Collections.<LocalResourceRepository>emptyList());
    }

    @Override
    protected void setChildren(@NotNull List<? extends LocalResourceRepository> children) {
      myChildren = children;
    }
  }
}
