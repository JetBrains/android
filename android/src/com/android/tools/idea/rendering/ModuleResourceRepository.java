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
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Resource repository for a single module (which can possibly have multiple resource folders)
 */
final class ModuleResourceRepository extends MultiResourceRepository {
  private final AndroidFacet myFacet;

  private ModuleResourceRepository(@NotNull AndroidFacet facet,
                                   @NotNull List<ResourceFolderRepository> delegates) {
    super(facet.getModule().getName(), delegates);
    myFacet = facet;
  }

  /**
   * Creates a new resource repository for the given module, <b>not</b> including its dependent modules.
   *
   * @param facet the facet for the module
   * @return the resource repository
   */
  @NotNull
  public static ProjectResources create(@NotNull final AndroidFacet facet) {
    boolean gradleProject = facet.isGradleProject();
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
    List<ResourceFolderRepository> resources = Lists.newArrayListWithExpectedSize(resourceDirectories.size());
    for (VirtualFile resourceDirectory : resourceDirectories) {
      ResourceFolderRepository repository = ResourceFolderRegistry.get(facet, resourceDirectory);
      resources.add(repository);
    }

    // We create a ModuleResourceRepository even if resources.isEmpty(), because we may
    // dynamically add children to it later (in updateRoots)
    final ModuleResourceRepository repository = new ModuleResourceRepository(facet, resources);

    // If the model is not yet ready, we may get an incomplete set of resource
    // directories, so in that case update the repository when the model is available.

    folderManager.addListener(new ResourceFolderManager.ResourceFolderListener() {
      @Override
      public void resourceFoldersChanged(@NotNull AndroidFacet facet, @NotNull List<VirtualFile> folders,
                                         @NotNull Collection<VirtualFile> added, @NotNull Collection<VirtualFile> removed) {
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
    // Compute current roots
    Map<VirtualFile, ResourceFolderRepository> map = Maps.newHashMap();
    for (ProjectResources resources : myChildren) {
      ResourceFolderRepository repository = (ResourceFolderRepository)resources;
      VirtualFile resourceDir = repository.getResourceDir();
      map.put(resourceDir, repository);
    }

    // Compute new resource directories (it's possible for just the order to differ, or
    // for resource dirs to have been added and/or removed)
    Set<VirtualFile> newDirs = Sets.newHashSet(resourceDirectories);
    List<ResourceFolderRepository> resources = Lists.newArrayListWithExpectedSize(newDirs.size());
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
  @VisibleForTesting
  @NotNull
  static ModuleResourceRepository createForTest(@NotNull final AndroidFacet facet, @NotNull List<VirtualFile> resourceDirectories) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    List<ResourceFolderRepository> resources = Lists.newArrayListWithExpectedSize(resourceDirectories.size());
    for (VirtualFile resourceDirectory : resourceDirectories) {
      ResourceFolderRepository repository = ResourceFolderRegistry.get(facet, resourceDirectory);
      resources.add(repository);
    }

    return new ModuleResourceRepository(facet, resources);
  }

  private static class EmptyRepository extends MultiResourceRepository {
    public EmptyRepository() {
      super("", Collections.<ProjectResources>emptyList());
    }

    @Override
    protected void setChildren(@NotNull List<? extends ProjectResources> children) {
      myChildren = children;
    }
  }
}
