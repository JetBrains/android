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

import static com.android.tools.idea.res.ResourceUpdateTracer.pathsForLogging;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.resources.ResourceType;
import com.android.tools.idea.model.AndroidModel;
import com.android.utils.TraceUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.android.facet.ResourceFolderManager.ResourceFolderListener;
import org.jetbrains.android.facet.SourceProviderManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @see ResourceRepositoryManager#getModuleResources()
 */
final class ModuleResourceRepository extends MultiResourceRepository implements SingleNamespaceResourceRepository {
  @NotNull private final AndroidFacet myFacet;
  @NotNull private final ResourceNamespace myNamespace;
  @NotNull private final SourceSet mySourceSet;
  @NotNull private final ResourceFolderRegistry myRegistry;

  private enum SourceSet { MAIN, TEST }

  /**
   * Creates a new resource repository for the given module, <b>not</b> including its dependent modules.
   *
   * <p>The returned repository needs to be registered with a {@link com.intellij.openapi.Disposable} parent.
   *
   * @param facet the facet for the module
   * @param namespace the namespace for the repository
   * @return the resource repository
   */
  @NotNull
  static LocalResourceRepository forMainResources(@NotNull AndroidFacet facet, @NotNull ResourceNamespace namespace) {
    ResourceFolderRegistry resourceFolderRegistry = ResourceFolderRegistry.getInstance(facet.getModule().getProject());
    ResourceFolderManager folderManager = ResourceFolderManager.getInstance(facet);
    List<VirtualFile> resourceDirectories = folderManager.getFolders();

    if (!AndroidModel.isRequired(facet)) {
      if (resourceDirectories.isEmpty()) {
        return new EmptyRepository(namespace);
      }
      List<LocalResourceRepository> childRepositories = new ArrayList<>(resourceDirectories.size());
      addRepositoriesInReverseOverlayOrder(resourceDirectories, childRepositories, facet, resourceFolderRegistry);
      return new ModuleResourceRepository(facet,
                                          namespace,
                                          childRepositories,
                                          SourceSet.MAIN);
    }


    DynamicValueResourceRepository dynamicResources = DynamicValueResourceRepository.create(facet, namespace);
    ModuleResourceRepository moduleRepository;

    try {
      List<LocalResourceRepository> childRepositories = new ArrayList<>(1 + resourceDirectories.size());
      childRepositories.add(dynamicResources);
      addRepositoriesInReverseOverlayOrder(resourceDirectories, childRepositories, facet, resourceFolderRegistry);
      moduleRepository = new ModuleResourceRepository(facet, namespace, childRepositories, SourceSet.MAIN);
    }
    catch (Throwable t) {
      Disposer.dispose(dynamicResources);
      throw t;
    }

    Disposer.register(moduleRepository, dynamicResources);
    return moduleRepository;
  }

  /**
   * Creates a new resource repository for the given module, <b>not</b> including its dependent modules.
   *
   * <p>The returned repository needs to be registered with a {@link com.intellij.openapi.Disposable} parent.
   *
   * @param facet the facet for the module
   * @param namespace the namespace for the repository
   * @return the resource repository
   */
  @NotNull
  static LocalResourceRepository forTestResources(@NotNull AndroidFacet facet, @NotNull ResourceNamespace namespace) {
    ResourceFolderRegistry resourceFolderRegistry = ResourceFolderRegistry.getInstance(facet.getModule().getProject());
    ResourceFolderManager folderManager = ResourceFolderManager.getInstance(facet);
    List<VirtualFile> resourceDirectories = folderManager.getTestFolders();

    if (!AndroidModel.isRequired(facet) && resourceDirectories.isEmpty()) {
      return new EmptyRepository(namespace);
    }

    List<LocalResourceRepository> childRepositories = new ArrayList<>(resourceDirectories.size());
    addRepositoriesInReverseOverlayOrder(resourceDirectories, childRepositories, facet, resourceFolderRegistry);

    return new ModuleResourceRepository(facet, namespace, childRepositories, SourceSet.TEST);
  }

  /**
   * Inserts repositories for the given {@code resourceDirectories} into {@code childRepositories}, in the right order.
   *
   * <p>{@code resourceDirectories} is assumed to be in the order returned from
   * {@link SourceProviderManager#getCurrentSourceProviders()}, which is the inverse of what we need. The code in
   * {@link MultiResourceRepository#getMap(ResourceNamespace, ResourceType, boolean)} gives priority to child repositories which are earlier
   * in the list, so after creating repositories for every folder, we add them in reverse to the list.
   *
   * @param resourceDirectories directories for which repositories should be constructed
   * @param childRepositories the list of repositories to which new repositories will be added
   * @param facet {@link AndroidFacet} that repositories will correspond to
   * @param resourceFolderRegistry {@link ResourceFolderRegistry} used to construct the repositories
   */
  private static void addRepositoriesInReverseOverlayOrder(@NotNull List<VirtualFile> resourceDirectories,
                                                           @NotNull List<LocalResourceRepository> childRepositories,
                                                           @NotNull AndroidFacet facet,
                                                           @NotNull ResourceFolderRegistry resourceFolderRegistry) {
    for (int i = resourceDirectories.size(); --i >= 0;) {
      VirtualFile resourceDirectory = resourceDirectories.get(i);
      ResourceFolderRepository repository = resourceFolderRegistry.get(facet, resourceDirectory);
      childRepositories.add(repository);
    }
  }

  private ModuleResourceRepository(@NotNull AndroidFacet facet,
                                   @NotNull ResourceNamespace namespace,
                                   @NotNull List<? extends LocalResourceRepository> delegates,
                                   @NotNull SourceSet sourceSet) {
    super(facet.getModule().getName());
    myFacet = facet;
    myNamespace = namespace;
    mySourceSet = sourceSet;
    myRegistry = ResourceFolderRegistry.getInstance(facet.getModule().getProject());

    setChildren(delegates, ImmutableList.of(), ImmutableList.of());

    ResourceFolderListener resourceFolderListener = new ResourceFolderListener() {
      @Override
      public void mainResourceFoldersChanged(@NotNull AndroidFacet facet, @NotNull List<? extends VirtualFile> folders) {
        if (mySourceSet == SourceSet.MAIN && facet.getModule() == myFacet.getModule()) {
          updateRoots(folders);
        }
      }

      @Override
      public void testResourceFoldersChanged(@NotNull AndroidFacet facet, @NotNull List<? extends VirtualFile> folders) {
        if (mySourceSet == SourceSet.TEST && facet.getModule() == myFacet.getModule()) {
          updateRoots(folders);
        }
      }
    };
    myFacet.getModule().getProject().getMessageBus().connect(this).subscribe(ResourceFolderManager.TOPIC, resourceFolderListener);
  }

  @VisibleForTesting
  void updateRoots(List<? extends VirtualFile> resourceDirectories) {
    ResourceUpdateTracer.logDirect(() ->
      TraceUtils.getSimpleId(this) + ".updateRoots(" + pathsForLogging(resourceDirectories, myFacet.getModule().getProject()) + ")"
    );
    // Non-folder repositories to put in front of the list.
    List<LocalResourceRepository> other = null;

    // Compute current roots.
    Map<VirtualFile, ResourceFolderRepository> map = new HashMap<>();
    ImmutableList<LocalResourceRepository> children = getLocalResources();
    for (LocalResourceRepository repository : children) {
      if (repository instanceof ResourceFolderRepository) {
        ResourceFolderRepository folderRepository = (ResourceFolderRepository)repository;
        VirtualFile resourceDir = folderRepository.getResourceDir();
        map.put(resourceDir, folderRepository);
      }
      else {
        assert repository instanceof DynamicValueResourceRepository;
        if (other == null) {
          other = new ArrayList<>();
        }
        other.add(repository);
      }
    }

    // Compute new resource directories (it's possible for just the order to differ, or
    // for resource dirs to have been added and/or removed).
    Set<VirtualFile> newDirs = new HashSet<>(resourceDirectories);
    List<LocalResourceRepository> resources = new ArrayList<>(newDirs.size() + (other != null ? other.size() : 0));
    if (other != null) {
      resources.addAll(other);
    }

    for (VirtualFile dir : resourceDirectories) {
      ResourceFolderRepository repository = map.get(dir);
      if (repository == null) {
        repository = myRegistry.get(myFacet, dir);
      }
      else {
        map.remove(dir);
      }
      resources.add(repository);
    }

    if (resources.equals(children)) {
      // Nothing changed (including order); nothing to do
      assert map.isEmpty(); // shouldn't have created any new ones
      return;
    }

    for (ResourceFolderRepository removed : map.values()) {
      removed.removeParent(this);
    }

    setChildren(resources, ImmutableList.of(), ImmutableList.of());
  }

  @Override
  @NotNull
  public ResourceNamespace getNamespace() {
    return myNamespace;
  }

  @Override
  @Nullable
  public String getPackageName() {
    return ResourceRepositoryImplUtil.getPackageName(myNamespace, myFacet);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
      .addValue(mySourceSet)
      .toString();
  }

  @VisibleForTesting
  @NotNull
  public static ModuleResourceRepository createForTest(@NotNull AndroidFacet facet,
                                                       @NotNull Collection<VirtualFile> resourceDirectories,
                                                       @NotNull ResourceNamespace namespace,
                                                       @Nullable DynamicValueResourceRepository dynamicResourceValueRepository) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    List<LocalResourceRepository> delegates =
        new ArrayList<>(resourceDirectories.size() + (dynamicResourceValueRepository == null ? 0 : 1));

    if (dynamicResourceValueRepository != null) {
      delegates.add(dynamicResourceValueRepository);
    }

    ResourceFolderRegistry resourceFolderRegistry = ResourceFolderRegistry.getInstance(facet.getModule().getProject());
    resourceDirectories.forEach(dir -> delegates.add(resourceFolderRegistry.get(facet, dir, namespace)));

    ModuleResourceRepository repository = new ModuleResourceRepository(facet, namespace, delegates, SourceSet.MAIN);
    Disposer.register(facet, repository);
    return repository;
  }
}
