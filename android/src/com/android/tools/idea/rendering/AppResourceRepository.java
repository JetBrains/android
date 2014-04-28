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
import com.android.builder.model.AndroidLibrary;
import com.android.ide.common.resources.IntArrayWrapper;
import com.android.resources.ResourceType;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.util.Pair;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.module.Module;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.GradleSyncListener;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.android.SdkConstants.DOT_AAR;
import static org.jetbrains.android.facet.ResourceFolderManager.EXPLODED_AAR;
import static org.jetbrains.android.facet.ResourceFolderManager.addAarsFromModuleLibraries;

/**
 * Resource repository which merges in resources from all the libraries and all the modules
 * in a project
 */
public class AppResourceRepository extends MultiResourceRepository {
  private final AndroidFacet myFacet;
  private List<LocalResourceRepository> myLibraries;

  protected AppResourceRepository(@NotNull AndroidFacet facet,
                                @NotNull List<? extends LocalResourceRepository> delegates,
                                @NotNull List<LocalResourceRepository> libraries) {
    super(facet.getModule().getName() + " with modules and libraries", delegates);
    myFacet = facet;
    myLibraries = libraries;
  }

  /**
   * Returns the Android merge resource repository for the resources in this module, any other modules in this project,
   * and any libraries this project depends on.
   *
   * @param module the module to look up resources for
   * @param createIfNecessary if true, create the app resources if necessary, otherwise only return if already computed
   * @return the resource repository
   */
  @Nullable
  public static AppResourceRepository getAppResources(@NotNull Module module, boolean createIfNecessary) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null) {
      return facet.getAppResources(createIfNecessary);
    }

    return null;
  }

  /**
   * Returns the Android merge resource repository for the resources in this module, any other modules in this project,
   * and any libraries this project depends on.
   *
   * @param facet the module facet to look up resources for
   * @param createIfNecessary if true, create the app resources if necessary, otherwise only return if already computed
   * @return the resource repository
   */
  @Contract("!null, true -> !null")
  @Nullable
  public static AppResourceRepository getAppResources(@NotNull AndroidFacet facet, boolean createIfNecessary) {
    return facet.getAppResources(createIfNecessary);
  }

  @NotNull
  public static AppResourceRepository create(@NotNull final AndroidFacet facet) {
    List<LocalResourceRepository> libraries = computeLibraries(facet);
    List<LocalResourceRepository> delegates = computeRepositories(facet, libraries);
    final AppResourceRepository repository = new AppResourceRepository(facet, delegates, libraries);

    facet.addListener(new GradleSyncListener() {
      @Override
      public void performedGradleSync(@NotNull AndroidFacet facet, boolean success) {
        // Libraries can change when we sync with Gradle
        if (success) {
          repository.updateRoots();
        }
      }
    });

    return repository;
  }

  private static List<LocalResourceRepository> computeRepositories(@NotNull final AndroidFacet facet,
                                                                 List<LocalResourceRepository> libraries) {
    List<LocalResourceRepository> repositories = Lists.newArrayListWithExpectedSize(10);
    LocalResourceRepository resources = ProjectResourceRepository.getProjectResources(facet, true);
    repositories.addAll(libraries);
    repositories.add(resources);
    return repositories;
  }

  private static List<LocalResourceRepository> computeLibraries(@NotNull final AndroidFacet facet) {
    List<AndroidFacet> dependentFacets = AndroidUtils.getAllAndroidDependencies(facet.getModule(), true);
    List<File> aarDirs = findAarLibraries(facet, dependentFacets);
    if (aarDirs.isEmpty()) {
      return Collections.emptyList();
    }

    List<LocalResourceRepository> resources = Lists.newArrayListWithExpectedSize(aarDirs.size());
    for (File root : aarDirs) {
      resources.add(FileResourceRepository.get(root));
    }
    return resources;
  }

  @NotNull
  private static List<File> findAarLibraries(AndroidFacet facet, List<AndroidFacet> dependentFacets) {
    // Use the gradle model if available, but if not, fall back to using plain IntelliJ library dependencies
    // which have been persisted since the most recent sync
    if (facet.isGradleProject() && facet.getIdeaAndroidProject() != null) {
      List<AndroidLibrary> libraries = Lists.newArrayList();
      addGradleLibraries(libraries, facet);
      for (AndroidFacet f : dependentFacets) {
        addGradleLibraries(libraries, f);
      }
      return findAarLibrariesFromGradle(dependentFacets, libraries);
    }
    return findAarLibrariesFromIntelliJ(facet, dependentFacets);
  }

  /**
   *  Reads IntelliJ library definitions ({@link com.intellij.openapi.roots.LibraryOrSdkOrderEntry}) and if possible, finds a corresponding
   * {@code .aar} resource library to include. This works before the Gradle project has been initialized.
   */
  private static List<File> findAarLibrariesFromIntelliJ(AndroidFacet facet, List<AndroidFacet> dependentFacets) {
    // Find .aar libraries from old IntelliJ library definitions
    Set<File> dirs = Sets.newHashSet();
    addAarsFromModuleLibraries(facet, dirs);
    for (AndroidFacet f : dependentFacets) {
      addAarsFromModuleLibraries(f, dirs);
    }
    List<File> sorted = new ArrayList<File>(dirs);
    // Sort to ensure consistent results between pre-model sync order of resources and
    // the post-sync order. (Also see sort comment in the method below.)
    Collections.sort(sorted);
    return sorted;
  }

  /**
   * Looks up the library dependencies from the Gradle tools model and returns the corresponding {@code .aar}
   * resource directories.
   */
  @NotNull
  private static List<File> findAarLibrariesFromGradle(List<AndroidFacet> dependentFacets, List<AndroidLibrary> libraries) {
    // Pull out the unique directories, in case multiple modules point to the same .aar folder
    Set<File> files = Sets.newHashSetWithExpectedSize(dependentFacets.size());

    Set<String> moduleNames = Sets.newHashSet();
    for (AndroidFacet f : dependentFacets) {
      moduleNames.add(f.getModule().getName());
    }
    for (AndroidLibrary library : libraries) {
      // We should only add .aar dependencies if they aren't already provided as modules.
      // For now, the way we associate them with each other is via the library name;
      // in the future the model will provide this for us
      String libraryName = null;
      String projectName = library.getProject();
      if (projectName != null && !projectName.isEmpty()) {
        libraryName = projectName.substring(projectName.lastIndexOf(':') + 1);
        // Since this library has project!=null, it exists in module form; don't
        // add it here.
        moduleNames.add(libraryName);
        continue;
      } else {
        File folder = library.getFolder();
        String name = folder.getName();
        if (name.endsWith(DOT_AAR)) {
          libraryName = name.substring(0, name.length() - DOT_AAR.length());
        } else if (folder.getPath().contains(EXPLODED_AAR)) {
          libraryName = folder.getParentFile().getName();
        }
      }
      if (libraryName != null && !moduleNames.contains(libraryName)) {
        File resFolder = library.getResFolder();
        if (resFolder.exists()) {
          files.add(resFolder);

          // Don't add it again!
          moduleNames.add(libraryName);
        }
      }
    }

    List<File> dirs = Lists.newArrayList();
    for (File resFolder : files) {
      dirs.add(resFolder);
    }

    // Sort alphabetically to ensure that we keep a consistent order of these libraries;
    // otherwise when we jump from libraries initialized from IntelliJ library binary paths
    // to gradle project state, the order difference will cause the merged project resource
    // maps to have to be recomputed
    Collections.sort(dirs);
    return dirs;
  }

  private static void addGradleLibraries(List<AndroidLibrary> list, AndroidFacet facet) {
    IdeaAndroidProject gradleProject = facet.getIdeaAndroidProject();
    if (gradleProject != null) {
      List<AndroidLibrary> libraries = gradleProject.getSelectedVariant().getMainArtifact().getDependencies().getLibraries();
      Set<File> unique = Sets.newHashSet();
      for (AndroidLibrary library : libraries) {
        addGradleLibrary(list, library, unique);
      }
    }
  }

  private static void addGradleLibrary(List<AndroidLibrary> list, AndroidLibrary library, Set<File> unique) {
    File folder = library.getFolder();
    if (!unique.add(folder)) {
      return;
    }
    list.add(library);
    for (AndroidLibrary dependency : library.getLibraryDependencies()) {
      addGradleLibrary(list, dependency, unique);
    }
  }

  /** Returns the libraries among the app resources, if any */
  @NotNull
  public List<LocalResourceRepository> getLibraries() {
    return myLibraries;
  }

  void updateRoots() {
    List<LocalResourceRepository> libraries = computeLibraries(myFacet);
    List<LocalResourceRepository> repositories = computeRepositories(myFacet, libraries);
    updateRoots(repositories, libraries);
  }

  @VisibleForTesting
  void updateRoots(List<LocalResourceRepository> resources, List<LocalResourceRepository> libraries) {
    if (resources.equals(myChildren)) {
      // Nothing changed (including order); nothing to do
      return;
    }

    myLibraries = libraries;
    setChildren(resources);
  }

  @VisibleForTesting
  @NotNull
  static AppResourceRepository createForTest(AndroidFacet facet,
                                             List<LocalResourceRepository> modules,
                                             List<LocalResourceRepository> libraries) {
    assert modules.containsAll(libraries);
    assert modules.size() == libraries.size() + 1; // should only combine with the module set repository
    return new AppResourceRepository(facet, modules, libraries);
  }

  @Nullable
  public FileResourceRepository findRepositoryFor(@NotNull File aarDirectory) {
    String aarPath = aarDirectory.getPath();
    assert aarPath.endsWith(DOT_AAR) || aarPath.contains(EXPLODED_AAR) : aarPath;
    for (LocalResourceRepository r : myLibraries) {
      if (r instanceof FileResourceRepository) {
        FileResourceRepository repository = (FileResourceRepository)r;
        if (repository.getResourceDirectory().getPath().startsWith(aarPath)) {
          return repository;
        }
      } else {
        assert false : r.getClass();
      }
    }
    return null;
  }

  // For LayoutlibCallback

  // Project resource ints are defined as 0x7FXX#### where XX is the resource type (layout, drawable,
  // etc...). Using FF as the type allows for 255 resource types before we get a collision
  // which should be fine.
  private static final int DYNAMIC_ID_SEED_START = 0x7fff0000;

  /** Map of (name, id) for resources of type {@link com.android.resources.ResourceType#ID} coming from R.java */
  private Map<ResourceType, TObjectIntHashMap<String>> myResourceValueMap;
  /** Map of (id, [name, resType]) for all resources coming from R.java */
  private TIntObjectHashMap<Pair<ResourceType, String>> myResIdValueToNameMap;
  /** Map of (int[], name) for styleable resources coming from R.java */
  private Map<IntArrayWrapper, String> myStyleableValueToNameMap;

  private final TObjectIntHashMap<String> myName2DynamicIdMap = new TObjectIntHashMap<String>();
  private final TIntObjectHashMap<Pair<ResourceType, String>> myDynamicId2ResourceMap =
    new TIntObjectHashMap<Pair<ResourceType, String>>();
  private int myDynamicSeed = DYNAMIC_ID_SEED_START;
  private final IntArrayWrapper myWrapper = new IntArrayWrapper(null);


  @Nullable
  public Pair<ResourceType, String> resolveResourceId(int id) {
    Pair<ResourceType, String> result = null;
    if (myResIdValueToNameMap != null) {
      result = myResIdValueToNameMap.get(id);
    }

    if (result == null) {
      final Pair<ResourceType, String> pair = myDynamicId2ResourceMap.get(id);
      if (pair != null) {
        result = pair;
      }
    }

    return result;
  }

  @Nullable
  public String resolveStyleable(int[] id) {
    if (myStyleableValueToNameMap != null) {
      myWrapper.set(id);
      // A normal map lookup on int[] would only consider object identity, but the IntArrayWrapper
      // will check all the individual elements for equality. We reuse an instance for all the lookups
      // since we don't need a new one each time.
      return myStyleableValueToNameMap.get(myWrapper);
    }

    return null;
  }

  @Nullable
  public Integer getResourceId(ResourceType type, String name) {
    final TObjectIntHashMap<String> map = myResourceValueMap != null ? myResourceValueMap.get(type) : null;

    if (map == null || !map.containsKey(name)) {
      return getDynamicId(type, name);
    }
    return map.get(name);
  }

  private int getDynamicId(ResourceType type, String name) {
    synchronized (myName2DynamicIdMap) {
      if (myName2DynamicIdMap.containsKey(name)) {
        return myName2DynamicIdMap.get(name);
      }
      final int value = ++myDynamicSeed;
      myName2DynamicIdMap.put(name, value);
      myDynamicId2ResourceMap.put(value, Pair.of(type, name));
      return value;
    }
  }

  public void setCompiledResources(TIntObjectHashMap<Pair<ResourceType, String>> id2res,
                                   Map<IntArrayWrapper, String> styleableId2name,
                                   Map<ResourceType, TObjectIntHashMap<String>> res2id) {
    // Regularly clear dynamic seed such that we don't run out of numbers (we only have 255)
    synchronized (myName2DynamicIdMap) {
      myDynamicSeed = DYNAMIC_ID_SEED_START;
      myName2DynamicIdMap.clear();
      myDynamicId2ResourceMap.clear();
    }

    myResourceValueMap = res2id;
    myResIdValueToNameMap = id2res;
    myStyleableValueToNameMap = styleableId2name;

//    AarResourceClassRegistry.get().clear();
  }
}
