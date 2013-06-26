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
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.tools.idea.gradle.variant.view.BuildVariantView.BuildVariantSelectionChangeListener;

/**
 * Resource repository for a single module (which can possibly have multiple resource folders)
 * <p/>
 * TODO:
 * <ul>
 * <li> Handle proper overlay handling here; separate source folders should overlap in the given order.
 * Maybe the right way to handle it is to have each DelegatingProjectResources override a map merging
 * algorithm.
 * </li>
 * <li> Figure out how I can super efficiently keep these up to date; the getMap thing in the other repo
 * is slow</li>
 * <li>Make a registry for the SingleResourceDirectoryRepositories! On the facet perhaps? So I can reuse them quickly etc.</li>
 * <li>Make ResourceRepository delegate the mItems map to the children via a getMap so the delegators can do a smarter
 *     job syncing</li>
 * <li>Handle updates!</li>
 * </ul>
 */
final class ModuleResourceRepository extends MultiResourceRepository {
  private final AndroidFacet myFacet;

  private ModuleResourceRepository(@NotNull AndroidFacet facet, @NotNull List<ResourceFolderRepository> delegates) {
    super(delegates);
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
      assert primaryResourceDir != null;
      return ResourceFolderRepository.create(facet, primaryResourceDir);
    }

    List<VirtualFile> resourceDirectories = facet.getAllResourceDirectories();
    List<ResourceFolderRepository> resources = Lists.newArrayListWithExpectedSize(resourceDirectories.size());
    for (VirtualFile resourceDirectory : resourceDirectories) {
      ResourceFolderRepository repository = ResourceFolderRepository.create(facet, resourceDirectory);
      resources.add(repository);
    }

    boolean refresh = facet.getIdeaAndroidProject() == null;
    final ModuleResourceRepository repository = new ModuleResourceRepository(facet, resources);

    // If the model is not yet ready, we may get an incomplete set of resource
    // directories, so in that case update the repository when the model is available.
    if (refresh) {
      facet.addListener(new AndroidFacet.GradleProjectAvailableListener() {
        @Override
        public void gradleProjectAvailable(@NotNull IdeaAndroidProject project) {
          facet.removeListener(this);
          repository.updateRoots();
        }
      });
    }

    // Also refresh the project resources whenever the variant changes
    BuildVariantView.getInstance(facet.getModule().getProject()).addListener(new BuildVariantSelectionChangeListener() {
      @Override
      public void buildVariantSelected(@NotNull AndroidFacet facet) {
        repository.updateRoots();
      }
    });

    return repository;
  }

  private void updateRoots() {
    updateRoots(myFacet.getAllResourceDirectories());
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
        repository = ResourceFolderRepository.create(myFacet, dir);
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
      removed.setParent(null);
    }

    // Dispose unused repositories; this will cause them to unregister from PSI events.
    // This is vital since if we switch back to this resource directory, a new repository
    // will be created, which will register a listener, and there must only be one repository
    // per folder (per project). Longer term we should instead keep a single instance around
    // in the project, and reuse these when possible. That will be necessary when we offer APIs
    // to iterate through all possible resources, not just those for the current flavor configuration, etc.
    for (ResourceFolderRepository unused : map.values()) {
      unused.dispose();
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
      ResourceFolderRepository repository = ResourceFolderRepository.create(facet, resourceDirectory);
      resources.add(repository);
    }

    return new ModuleResourceRepository(facet, resources);
  }
}
