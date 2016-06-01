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
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.repository.ResourceVisibilityLookup;
import com.android.ide.common.resources.IntArrayWrapper;
import com.android.resources.ResourceType;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.util.Pair;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.module.Module;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.ModuleClassLoader;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.android.SdkConstants.DOT_AAR;
import static org.jetbrains.android.facet.ResourceFolderManager.addAarsFromModuleLibraries;

/**
 * Resource repository which merges in resources from the libraries and modules which are
 * transitive dependencies of the given AndroidFacet / module.
 */
public class AppResourceRepository extends MultiResourceRepository {
  private final AndroidFacet myFacet;
  private List<FileResourceRepository> myLibraries;
  private long myIdsModificationCount;

  /**
   * List of libraries that contain an R.txt file.
   *
   * The order of these libraries may not match the order of {@link #myLibraries}. It's intended to be used
   * only to get the R.txt files for declare styleables.
   */
  private final LinkedList<FileResourceRepository> myAarLibraries = Lists.newLinkedList();
  private Set<String> myIds;

  protected AppResourceRepository(@NotNull AndroidFacet facet,
                                  @NotNull List<? extends LocalResourceRepository> delegates,
                                  @NotNull List<FileResourceRepository> libraries) {
    super(facet.getModule().getName() + " with modules and libraries", delegates);
    myFacet = facet;
    myLibraries = libraries;
    for (FileResourceRepository library : libraries) {
      if (library.getResourceTextFile() != null) {
        myAarLibraries.add(library);
      }
    }
  }

  /**
   * Returns the Android merge resource repository for the resources in this module, any other modules in this project,
   * and any libraries this project depends on.
   *
   * @param module            the module to look up resources for
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
   * @param facet             the module facet to look up resources for
   * @param createIfNecessary if true, create the app resources if necessary, otherwise only return if already computed
   * @return the resource repository
   */
  @Contract("!null, true -> !null")
  @Nullable
  public static AppResourceRepository getAppResources(@NotNull AndroidFacet facet, boolean createIfNecessary) {
    return facet.getAppResources(createIfNecessary);
  }

  @NotNull
  public static AppResourceRepository create(@NotNull AndroidFacet facet) {
    List<FileResourceRepository> libraries = computeLibraries(facet);
    List<LocalResourceRepository> delegates = computeRepositories(facet, libraries);
    AppResourceRepository repository = new AppResourceRepository(facet, delegates, libraries);

    ProjectResourceRepositoryRootListener.ensureSubscribed(facet.getModule().getProject());

    return repository;
  }

  private static List<LocalResourceRepository> computeRepositories(@NotNull final AndroidFacet facet,
                                                                 List<FileResourceRepository> libraries) {
    List<LocalResourceRepository> repositories = Lists.newArrayListWithExpectedSize(10);
    LocalResourceRepository resources = ProjectResourceRepository.getProjectResources(facet, true);
    repositories.addAll(libraries);
    repositories.add(resources);
    return repositories;
  }

  private static List<FileResourceRepository> computeLibraries(@NotNull final AndroidFacet facet) {
    List<AndroidFacet> dependentFacets = AndroidUtils.getAllAndroidDependencies(facet.getModule(), true);
    Map<File, String> aarDirs = findAarLibraries(facet, dependentFacets);
    if (aarDirs.isEmpty()) {
      return Collections.emptyList();
    }

    List<File> dirs = Lists.newArrayList(aarDirs.keySet());

    // Sort alphabetically to ensure that we keep a consistent order of these libraries;
    // otherwise when we jump from libraries initialized from IntelliJ library binary paths
    // to gradle project state, the order difference will cause the merged project resource
    // maps to have to be recomputed
    Collections.sort(dirs);

    List<FileResourceRepository> resources = Lists.newArrayListWithExpectedSize(aarDirs.size());
    for (File root : dirs) {
      resources.add(FileResourceRepository.get(root, aarDirs.get(root)));
    }
    return resources;
  }

  @NotNull
  private static Map<File, String> findAarLibraries(@NotNull AndroidFacet facet, @NotNull List<AndroidFacet> dependentFacets) {
    // Use the gradle model if available, but if not, fall back to using plain IntelliJ library dependencies
    // which have been persisted since the most recent sync
    AndroidGradleModel androidGradleModel = AndroidGradleModel.get(facet);
    if (androidGradleModel != null) {
      List<AndroidLibrary> libraries = Lists.newArrayList();
      addGradleLibraries(libraries, androidGradleModel);
      for (AndroidFacet dependentFacet : dependentFacets) {
        AndroidGradleModel dependentGradleModel = AndroidGradleModel.get(dependentFacet);
        if (dependentGradleModel != null) {
          addGradleLibraries(libraries, dependentGradleModel);
        }
      }
      return findAarLibrariesFromGradle(dependentFacets, libraries);
    }
    return findAarLibrariesFromIntelliJ(facet, dependentFacets);
  }

  @NotNull
  public static Collection<AndroidLibrary> findAarLibraries(@NotNull AndroidFacet facet) {
    List<AndroidLibrary> libraries = Lists.newArrayList();
    if (facet.requiresAndroidModel()) {
      AndroidGradleModel androidModel = AndroidGradleModel.get(facet);
      if (androidModel != null) {
        List<AndroidFacet> dependentFacets = AndroidUtils.getAllAndroidDependencies(facet.getModule(), true);
        addGradleLibraries(libraries, androidModel);
        for (AndroidFacet dependentFacet : dependentFacets) {
          AndroidGradleModel dependentGradleModel = AndroidGradleModel.get(dependentFacet);
          if (dependentGradleModel != null) {
            addGradleLibraries(libraries, dependentGradleModel);
          }
        }
      }
    }
    return libraries;
  }

  /**
   * Reads IntelliJ library definitions ({@link com.intellij.openapi.roots.LibraryOrSdkOrderEntry}) and if possible, finds a corresponding
   * {@code .aar} resource library to include. This works before the Gradle project has been initialized.
   */
  private static Map<File, String> findAarLibrariesFromIntelliJ(AndroidFacet facet, List<AndroidFacet> dependentFacets) {
    // Find .aar libraries from old IntelliJ library definitions
    Map<File, String> dirs = new HashMap<>();
    addAarsFromModuleLibraries(facet, dirs);
    for (AndroidFacet f : dependentFacets) {
      addAarsFromModuleLibraries(f, dirs);
    }
    return dirs;
  }

  /**
   * Looks up the library dependencies from the Gradle tools model and returns the corresponding {@code .aar}
   * resource directories.
   */
  @NotNull
  private static Map<File, String> findAarLibrariesFromGradle(List<AndroidFacet> dependentFacets, List<AndroidLibrary> libraries) {
    // Pull out the unique directories, in case multiple modules point to the same .aar folder
    Map<File, String> files = new HashMap<>(libraries.size());

    Set<String> moduleNames = Sets.newHashSet();
    for (AndroidFacet f : dependentFacets) {
      moduleNames.add(f.getModule().getName());
    }
    try {
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
        }
        else {
          File folder = library.getFolder();
          String name = folder.getName();
          if (name.endsWith(DOT_AAR)) {
            libraryName = name.substring(0, name.length() - DOT_AAR.length());
          }
          else if (folder.getPath().contains(AndroidGradleModel.EXPLODED_AAR)) {
            libraryName = folder.getParentFile().getName();
          }
        }
        if (libraryName != null && !moduleNames.contains(libraryName)) {
          File resFolder = library.getResFolder();
          if (resFolder.exists()) {
            files.put(resFolder, library.getName());

            // Don't add it again!
            moduleNames.add(libraryName);
          }
        }
      }
    }
    catch (UnsupportedMethodException e) {
      // This happens when there is an incompatibility between the builder-model interfaces embedded in Android Studio and the
      // cached model.
      // If we got here is because this code got invoked before project sync happened (e.g. when reopening a project with open editors).
      // Project sync now is smart enough to handle this case and will trigger a full sync.
      LOG.warn("Incompatibility found between the IDE's builder-model and the cached Gradle model", e);
    }
    return files;
  }

  // TODO: b/23032391
  private static void addGradleLibraries(List<AndroidLibrary> list, AndroidGradleModel androidGradleModel) {
    Collection<AndroidLibrary> libraries = androidGradleModel.getSelectedMainCompileDependencies().getLibraries();
    Set<File> unique = Sets.newHashSet();
    for (AndroidLibrary library : libraries) {
      addGradleLibrary(list, library, unique);
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
  public List<FileResourceRepository> getLibraries() {
    return myLibraries;
  }

  @NotNull
  private Set<String> getAllIds() {
    long currentModCount = getModificationCount();
    if (myIdsModificationCount < currentModCount) {
      myIdsModificationCount = currentModCount;
      if (myIds == null) {
        int size = 0;
        for (FileResourceRepository library : myLibraries) {
          if (library.getAllDeclaredIds() != null) {
            size += library.getAllDeclaredIds().size();
          }
        }
        myIds = Sets.newHashSetWithExpectedSize(size);
      }
      else {
        myIds.clear();
      }
      for (FileResourceRepository library : myLibraries) {
        if (library.getAllDeclaredIds() != null) {
          myIds.addAll(library.getAllDeclaredIds());
        }
      }
      // Also add all ids from resource types, just in case it contains things that are not in the libraries.
      myIds.addAll(super.getItemsOfType(ResourceType.ID));
    }
    return myIds;
  }

  @Override
  @NotNull
  public Collection<String> getItemsOfType(@NotNull ResourceType type) {
    synchronized (ITEM_MAP_LOCK) {
      return type == ResourceType.ID ? getAllIds() : super.getItemsOfType(type);
    }
  }

  void updateRoots() {
    List<FileResourceRepository> libraries = computeLibraries(myFacet);
    List<LocalResourceRepository> repositories = computeRepositories(myFacet, libraries);
    updateRoots(repositories, libraries);
  }

  @VisibleForTesting
  void updateRoots(List<LocalResourceRepository> resources, List<FileResourceRepository> libraries) {
    myResourceVisibility = null;
    myResourceVisibilityProvider = null;
    invalidateResourceDirs();

    if (resources.equals(myChildren)) {
      // Nothing changed (including order); nothing to do
      return;
    }

    myResourceVisibility = null;
    myLibraries = libraries;
    myAarLibraries.clear();
    for (FileResourceRepository library : myLibraries) {
      if (library.getResourceTextFile() != null) {
        myAarLibraries.add(library);
      }
    }
    setChildren(resources);

    // Clear the fake R class cache and the ModuleClassLoader cache.
    resetDynamicIds(true);
    ModuleClassLoader.clearCache(myFacet.getModule());
  }

  @VisibleForTesting
  @NotNull
  static AppResourceRepository createForTest(@NotNull AndroidFacet facet,
                                             @NotNull List<LocalResourceRepository> modules,
                                             @NotNull List<FileResourceRepository> libraries) {
    assert modules.containsAll(libraries);
    assert modules.size() == libraries.size() + 1; // should only combine with the module set repository
    return new AppResourceRepository(facet, modules, libraries);
  }

  @Nullable
  public FileResourceRepository findRepositoryFor(@NotNull File aarDirectory) {
    String aarPath = aarDirectory.getPath();
    assert aarPath.endsWith(DOT_AAR) || aarPath.contains(AndroidGradleModel.EXPLODED_AAR) : aarPath;
    for (LocalResourceRepository r : myLibraries) {
      if (r instanceof FileResourceRepository) {
        FileResourceRepository repository = (FileResourceRepository)r;
        if (repository.getResourceDirectory().getPath().startsWith(aarPath)) {
          return repository;
        }
      }
      else {
        assert false : r.getClass();
      }
    }
    return null;
  }

  private ResourceVisibilityLookup myResourceVisibility;
  private ResourceVisibilityLookup.Provider myResourceVisibilityProvider;

  @Nullable
  public ResourceVisibilityLookup.Provider getResourceVisibilityProvider() {
    if (myResourceVisibilityProvider == null) {
      if (!myFacet.requiresAndroidModel() || myFacet.getAndroidModel() == null) {
        return null;
      }
      myResourceVisibilityProvider = new ResourceVisibilityLookup.Provider();
    }

    return myResourceVisibilityProvider;
  }

  @NotNull
  public ResourceVisibilityLookup getResourceVisibility(@NotNull AndroidFacet facet) {
    // TODO: b/23032391
    AndroidGradleModel androidModel = AndroidGradleModel.get(facet);
    if (androidModel != null) {
      ResourceVisibilityLookup.Provider provider = getResourceVisibilityProvider();
      if (provider != null) {
        AndroidProject androidProject = androidModel.getAndroidProject();
        Variant variant = androidModel.getSelectedVariant();
        return provider.get(androidProject, variant);
      }
    }

    return ResourceVisibilityLookup.NONE;
  }

  /**
   * Returns true if the given resource is private
   *
   * @param type the type of the resource
   * @param name the name of the resource
   * @return true if the given resource is private
   */
  public boolean isPrivate(@NotNull ResourceType type, @NotNull String name) {
    if (myResourceVisibility == null) {
      ResourceVisibilityLookup.Provider provider = getResourceVisibilityProvider();
      if (provider == null) {
        return false;
      }
      // TODO: b/23032391
      AndroidGradleModel androidModel = AndroidGradleModel.get(myFacet);
      if (androidModel == null) {
        // normally doesn't happen since we check in getResourceVisibility,
        // but can be triggered during a sync (b/22523040)
        return false;
      }
      myResourceVisibility = provider.get(androidModel.getAndroidProject(), androidModel.getSelectedVariant());
    }

    return myResourceVisibility.isPrivate(type, name);
  }

  // For LayoutlibCallback

  // Project resource ints are defined as 0x7FXX#### where XX is the resource type (layout, drawable,
  // etc...). Using FF as the type allows for 255 resource types before we get a collision
  // which should be fine.
  private static final int DYNAMIC_ID_SEED_START = 0x7fff0000;

  /** Map of (name, id) for resources of type {@link ResourceType#ID} coming from R.java */
  private Map<ResourceType, TObjectIntHashMap<String>> myResourceValueMap;
  /** Map of (id, [name, resType]) for all resources coming from R.java */
  @SuppressWarnings("deprecation")  // For Pair
  private TIntObjectHashMap<Pair<ResourceType, String>> myResIdValueToNameMap;
  /** Map of (int[], name) for styleable resources coming from R.java */
  private Map<IntArrayWrapper, String> myStyleableValueToNameMap;

  private final TObjectIntHashMap<TypedResourceName> myName2DynamicIdMap = new TObjectIntHashMap<TypedResourceName>();
  private final TIntObjectHashMap<TypedResourceName> myDynamicId2ResourceMap = new TIntObjectHashMap<TypedResourceName>();
  private int myDynamicSeed = DYNAMIC_ID_SEED_START;
  private final IntArrayWrapper myWrapper = new IntArrayWrapper(null);


  @Nullable
  @SuppressWarnings("deprecation")  // For Pair
  public Pair<ResourceType, String> resolveResourceId(int id) {
    Pair<ResourceType, String> result = null;
    if (myResIdValueToNameMap != null) {
      result = myResIdValueToNameMap.get(id);
    }

    if (result == null) {
      final TypedResourceName pair = myDynamicId2ResourceMap.get(id);
      if (pair != null) {
        result = pair.toPair();
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

  @NotNull
  public Integer getResourceId(ResourceType type, String name) {
    final TObjectIntHashMap<String> map = myResourceValueMap != null ? myResourceValueMap.get(type) : null;

    if (map == null || !map.containsKey(name)) {
      return getDynamicId(type, name);
    }
    return map.get(name);
  }

  @Nullable
  Integer[] getDeclaredArrayValues(List<AttrResourceValue> attrs, String styleableName) {
    ListIterator<FileResourceRepository> iter = myAarLibraries.listIterator();
    while (iter.hasNext()) {
      FileResourceRepository repo = iter.next();
      File resourceTextFile = repo.getResourceTextFile();
      if (resourceTextFile == null) {
        continue;
      }
      Integer[] in = RDotTxtParser.getDeclareStyleableArray(resourceTextFile, attrs, styleableName);
      if (in != null) {
        // Reorder the list to place this library first. It's likely that there will be more calls to the same library.
        iter.remove();
        myAarLibraries.addFirst(repo);
        return in;
      }
    }
    return null;
  }

  private int getDynamicId(ResourceType type, String name) {
    TypedResourceName key = new TypedResourceName(type, name);
    synchronized (myName2DynamicIdMap) {
      if (myName2DynamicIdMap.containsKey(key)) {
        return myName2DynamicIdMap.get(key);
      }
      final int value = ++myDynamicSeed;
      myName2DynamicIdMap.put(key, value);
      myDynamicId2ResourceMap.put(value, key);
      return value;
    }
  }

  public void setCompiledResources(@SuppressWarnings("deprecation") TIntObjectHashMap<Pair<ResourceType, String>> id2res,
                                   Map<IntArrayWrapper, String> styleableId2name,
                                   Map<ResourceType, TObjectIntHashMap<String>> res2id) {
    resetDynamicIds(true);
    myResourceValueMap = res2id;
    myResIdValueToNameMap = id2res;
    myStyleableValueToNameMap = styleableId2name;
  }

  void resetDynamicIds(boolean clearAarResourceRegistry) {
    // The dynamic ids are referenced by the generated R classes. Ensure that the R classes cache is also cleared
    // if the dynamic ids are reset.
    if (clearAarResourceRegistry) {
      AarResourceClassRegistry.get(myFacet.getModule().getProject()).clearCache(this);
    }
    synchronized (myName2DynamicIdMap) {
      myDynamicSeed = DYNAMIC_ID_SEED_START;
      myName2DynamicIdMap.clear();
      myDynamicId2ResourceMap.clear();
    }
  }

  private static final class TypedResourceName {
    @Nullable
    final ResourceType myType;
    @NotNull
    final String myName;
    @SuppressWarnings("deprecation") Pair<ResourceType, String> myPair;

    public TypedResourceName(@Nullable ResourceType type, @NotNull String name) {
      myType = type;
      myName = name;
    }

    @SuppressWarnings("deprecation")
    public Pair<ResourceType, String> toPair() {
      if (myPair == null) {
        myPair = Pair.of(myType, myName);
      }
      return myPair;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      TypedResourceName that = (TypedResourceName)o;

      if (myType != that.myType) return false;
      if (!myName.equals(that.myName)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myType != null ? myType.hashCode() : 0;
      result = 31 * result + (myName.hashCode());
      return result;
    }

    @Override
    public String toString() {
      return String.format("Type=%1$s, value=%2$s", myType, myName);
    }
  }
}
