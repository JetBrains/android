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
import com.android.builder.model.AaptOptions;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.util.PathString;
import com.android.projectmodel.AarLibrary;
import com.android.resources.ResourceType;
import com.android.tools.idea.AndroidProjectModelUtils;
import com.android.tools.idea.projectsystem.FilenameConstants;
import com.android.tools.idea.res.aar.AarResourceRepositoryCache;
import com.android.tools.idea.res.aar.AarSourceResourceRepository;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.ModuleClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.android.SdkConstants.FD_RES;

/**
 * @see ResourceRepositoryManager#getAppResources(boolean)
 */
class AppResourceRepository extends MultiResourceRepository {
  private static final Logger LOG = Logger.getInstance(AppResourceRepository.class);
  static final Key<Boolean> TEMPORARY_RESOURCE_CACHE = Key.create("TemporaryResourceCache");

  private final AndroidFacet myFacet;
  private List<AarSourceResourceRepository> myLibraries;
  private long myIdsModificationCount;
  private Set<String> myIds;

  private final Object RESOURCE_MAP_LOCK = new Object();

  /**
   * Map from library name to resource dirs.
   * The key library name may be null.
   */
  @Nullable private Multimap<String, VirtualFile> myResourceDirMap;

  @NotNull
  static AppResourceRepository create(@NotNull AndroidFacet facet) {
    List<AarSourceResourceRepository> libraries = computeLibraries(facet);
    AppResourceRepository repository = new AppResourceRepository(facet, computeRepositories(facet, libraries), libraries);
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
                                                                   List<AarSourceResourceRepository> libraries) {
    List<LocalResourceRepository> repositories = new ArrayList<>(libraries.size() + 2);
    repositories.addAll(libraries);
    repositories.add(ResourceRepositoryManager.getProjectResources(facet));
    repositories.add(SampleDataResourceRepository.getInstance(facet));
    return repositories;
  }

  private static List<AarSourceResourceRepository> computeLibraries(@NotNull AndroidFacet facet) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("computeLibraries");
    }

    AaptOptions.Namespacing namespacing = ResourceRepositoryManager.getOrCreateInstance(facet).getNamespacing();
    Map<String, AarLibrary> aarDependencies = AndroidProjectModelUtils.findAarDependencies(facet.getModule());
    List<AarSourceResourceRepository> result = new ArrayList<>(aarDependencies.size());
    for (AarLibrary aarLibrary: aarDependencies.values()) {
      if (namespacing == AaptOptions.Namespacing.DISABLED) {
        File resFolder = aarLibrary.getResFolder().toFile();
        if (resFolder == null) {
          LOG.warn("Cannot find res folder for " + aarLibrary.getAddress());
          continue;
        }
        result.add(AarResourceRepositoryCache.getInstance().getSourceRepository(resFolder, aarLibrary.getAddress()));
      } else {
        PathString resApkPath = aarLibrary.getResApkFile();
        if (resApkPath == null) {
          LOG.warn("No res.apk for " + aarLibrary.getAddress());
          continue;
        }

        File resApkFile = resApkPath.toFile();
        if (resApkFile == null) {
          LOG.warn("Cannot find res.apk for " + aarLibrary.getAddress());
          continue;
        }

        result.add(AarResourceRepositoryCache.getInstance().getProtoRepository(resApkFile, aarLibrary.getAddress()));
      }
    }
    return result;
  }

  protected AppResourceRepository(@NotNull AndroidFacet facet,
                                  @NotNull List<? extends LocalResourceRepository> delegates,
                                  @NotNull List<AarSourceResourceRepository> libraries) {
    super(facet.getModule().getName() + " with modules and libraries");
    myFacet = facet;
    myLibraries = libraries;
    setChildren(delegates);
  }

  /**
   * Returns the libraries among the app resources, if any.
   */
  @NotNull
  List<AarSourceResourceRepository> getLibraries() {
    return myLibraries;
  }

  /**
   * Returns the names of all resources of type {@link ResourceType#ID} available in a non-namespaced application. This means reading the
   * R.txt files from libraries, as we don't scan layouts etc. in AARs for "@+id" declarations.
   *
   * TODO(namespaces): remove the dependency on R.txt
   */
  @NotNull
  private Set<String> getAllIds() {
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
        myIds = Sets.newHashSetWithExpectedSize(size);
      }
      else {
        myIds.clear();
      }
      for (AarSourceResourceRepository library : myLibraries) {
        if (library.getAllDeclaredIds() != null) {
          myIds.addAll(library.getAllDeclaredIds().keySet());
        }
      }
      // Also add all ids from resource types, just in case it contains things that are not in the libraries.
      myIds.addAll(super.getItemsOfType(ResourceNamespace.TODO(), ResourceType.ID));
    }
    return myIds;
  }

  @Override
  @NotNull
  public Collection<String> getItemsOfType(@NotNull ResourceNamespace namespace, @NotNull ResourceType type) {
    synchronized (ITEM_MAP_LOCK) {
      // TODO(namespaces): store all ID resources in the repositories and stop reading R.txt.
      return type == ResourceType.ID && namespace == ResourceNamespace.RES_AUTO ? getAllIds() : super.getItemsOfType(namespace, type);
    }
  }

  void updateRoots() {
    List<AarSourceResourceRepository> libraries = computeLibraries(myFacet);
    List<LocalResourceRepository> repositories = computeRepositories(myFacet, libraries);
    updateRoots(repositories, libraries);
  }

  @VisibleForTesting
  void updateRoots(@NotNull List<LocalResourceRepository> resources, @NotNull List<AarSourceResourceRepository> libraries) {
    synchronized (RESOURCE_MAP_LOCK) {
      myResourceDirMap = null;
    }
    invalidateResourceDirs();

    if (resources.equals(getChildren())) {
      // Nothing changed (including order); nothing to do
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
                                             @NotNull List<AarSourceResourceRepository> libraries) {
    assert modules.containsAll(libraries);
    assert modules.size() == libraries.size() + 1; // should only combine with the module set repository
    return new AppResourceRepository(facet, modules, libraries);
  }

  @Nullable
  AarSourceResourceRepository findRepositoryFor(@NotNull File aarDirectory) {
    String aarPath = aarDirectory.getPath();
    for (LocalResourceRepository r : myLibraries) {
      if (r instanceof AarSourceResourceRepository) {
        AarSourceResourceRepository repository = (AarSourceResourceRepository)r;
        if (repository.getResourceDirectory().getPath().startsWith(aarPath)) {
          return repository;
        }
      }
      else {
        assert false : r.getClass();
      }
    }

    // If we're looking for an AAR archive and didn't find it above, also
    // attempt searching by suffix alone. This is helpful scenarios like
    // http://b.android.com/210062 where we can end up in a scenario where
    // we're rendering in a library module, and Gradle sync has mapped an
    // AAR library to an existing library definition in the main module. In
    // that case we need to find the corresponding resources there.
    int exploded = aarPath.indexOf(FilenameConstants.EXPLODED_AAR);
    if (exploded != -1) {
      String suffix = aarPath.substring(exploded) + File.separator + FD_RES;
      for (LocalResourceRepository r : myLibraries) {
        if (r instanceof AarSourceResourceRepository) {
          AarSourceResourceRepository repository = (AarSourceResourceRepository)r;
          String path = repository.getResourceDirectory().getPath();
          if (path.endsWith(suffix)) {
            return repository;
          }
        }
        else {
          assert false : r.getClass();
        }
      }
    }

    return null;
  }
}
