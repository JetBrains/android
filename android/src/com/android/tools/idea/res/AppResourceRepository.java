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
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.Library;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.repository.GradleVersion;
import com.android.ide.common.repository.ResourceVisibilityLookup;
import com.android.resources.ResourceType;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.projectsystem.FilenameConstants;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.ModuleClassLoader;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.android.SdkConstants.FD_RES;
import static com.android.tools.idea.LogAnonymizerUtil.anonymizeClassName;
import static org.jetbrains.android.facet.ResourceFolderManager.addAarsFromModuleLibraries;

/**
 * This repository gives you a merged view of all the resources available to the app, as seen from the given module, with the current
 * variant. That includes not just the resources defined in this module, but in any other modules that this module depends on, as well as
 * any libraries those modules may depend on (such as appcompat).
 *
 * <p>When a layout is rendered in the layout, it is fetching resources from the app resource repository: it should see all the resources
 * just like the app does.
 *
 * <p>This class also keeps track of IDs assigned to resources and can generate unused IDs.
 */
public class AppResourceRepository extends MultiResourceRepository {
  private static final Logger LOG = Logger.getInstance(AppResourceRepository.class);
  private static final Key<Boolean> TEMPORARY_RESOURCE_CACHE = Key.create("TemporaryResourceCache");

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

  /**
   * Map from library name to resource dirs.
   * The key library name may be null.
   */
  private final Object RESOURCE_MAP_LOCK = new Object();
  @Nullable private Multimap<String, VirtualFile> myResourceDirMap;

  @Nullable
  public static AppResourceRepository getOrCreateInstance(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet != null ? getOrCreateInstance(facet) : null;
  }

  @NotNull
  public static AppResourceRepository getOrCreateInstance(@NotNull AndroidFacet facet) {
    return findAppResources(facet, true);
  }

  @Nullable
  public static AppResourceRepository findExistingInstance(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet != null ? findExistingInstance(facet) : null;
  }

  @Nullable
  public static AppResourceRepository findExistingInstance(@NotNull AndroidFacet facet) {
    return findAppResources(facet, false);
  }

  @Contract("_, true -> !null")
  @Nullable
  private static AppResourceRepository findAppResources(@NotNull AndroidFacet facet, boolean createIfNecessary) {
    return ResourceRepositoryManager.getOrCreateInstance(facet).getAppResources(createIfNecessary);
  }

  @NotNull
  static AppResourceRepository create(@NotNull AndroidFacet facet) {
    List<FileResourceRepository> libraries = computeLibraries(facet);
    AppResourceRepository repository = new AppResourceRepository(facet, computeRepositories(facet, libraries), libraries);
    ProjectResourceRepositoryRootListener.ensureSubscribed(facet.getModule().getProject());

    return repository;
  }

  /**
   * Return true if this project is build with Gradle but the AndroidModuleModel did not exist when the resources were cached.
   * And reset the state.
   */
  public static boolean testAndClearTempResourceCached(@NotNull Project project) {
    if (project.getUserData(TEMPORARY_RESOURCE_CACHE) != Boolean.TRUE) {
      return false;
    }
    project.putUserData(TEMPORARY_RESOURCE_CACHE, null);
    return true;
  }

  public Multimap<String, VirtualFile> getAllResourceDirs() {
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
                                                                   List<FileResourceRepository> libraries) {
    List<LocalResourceRepository> repositories = Lists.newArrayListWithExpectedSize(10);
    LocalResourceRepository resources = ProjectResourceRepository.getOrCreateInstance(facet);
    repositories.addAll(libraries);
    repositories.add(resources);
    if (StudioFlags.NELE_SAMPLE_DATA.get()) {
      repositories.add(new SampleDataResourceRepository(facet));
    }
    return repositories;
  }

  private static List<FileResourceRepository> computeLibraries(@NotNull final AndroidFacet facet) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("computeLibraries");
    }

    List<AndroidFacet> dependentFacets = AndroidUtils.getAllAndroidDependencies(facet.getModule(), true);
    Map<File, String> aarDirs = findAarLibraries(facet, dependentFacets);

    if (aarDirs.isEmpty()) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("  No AARs");
      }

      return Collections.emptyList();
    }

    List<File> dirs = Lists.newArrayList(aarDirs.keySet());

    // Sort alphabetically to ensure that we keep a consistent order of these libraries;
    // otherwise when we jump from libraries initialized from IntelliJ library binary paths
    // to gradle project state, the order difference will cause the merged project resource
    // maps to have to be recomputed
    Collections.sort(dirs);

    if (LOG.isDebugEnabled()) {
      for (File root : dirs) {
        LOG.debug("  Dependency: " + anonymizeClassName(aarDirs.get(root)));
      }
    }

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
    AndroidModuleModel androidModuleModel = AndroidModuleModel.get(facet);
    if (androidModuleModel != null) {
      List<Library> libraries = Lists.newArrayList();
      addGradleLibraries(libraries, androidModuleModel);
      for (AndroidFacet dependentFacet : dependentFacets) {
        AndroidModuleModel dependentGradleModel = AndroidModuleModel.get(dependentFacet);
        if (dependentGradleModel != null) {
          addGradleLibraries(libraries, dependentGradleModel);
        }
      }
      GradleVersion modelVersion = androidModuleModel.getModelVersion();
      assert modelVersion != null;
      return findAarLibrariesFromGradle(modelVersion, dependentFacets, libraries);
    }
    Project project = facet.getModule().getProject();
    if (GradleProjectInfo.getInstance(project).isBuildWithGradle()) {
      project.putUserData(TEMPORARY_RESOURCE_CACHE, true);
    }
    return findAarLibrariesFromIntelliJ(facet, dependentFacets);
  }

  @NotNull
  public static Collection<Library> findAarLibraries(@NotNull AndroidFacet facet) {
    List<Library> libraries = Lists.newArrayList();
    if (facet.requiresAndroidModel()) {
      AndroidModuleModel androidModel = AndroidModuleModel.get(facet);
      if (androidModel != null) {
        List<AndroidFacet> dependentFacets = AndroidUtils.getAllAndroidDependencies(facet.getModule(), true);
        addGradleLibraries(libraries, androidModel);
        for (AndroidFacet dependentFacet : dependentFacets) {
          AndroidModuleModel dependentGradleModel = AndroidModuleModel.get(dependentFacet);
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
  private static Map<File, String> findAarLibrariesFromGradle(@NotNull GradleVersion modelVersion,
                                                              List<AndroidFacet> dependentFacets,
                                                              List<Library> libraries) {
    // Pull out the unique directories, in case multiple modules point to the same .aar folder
    Map<File, String> files = new HashMap<>(libraries.size());

    Set<String> moduleNames = Sets.newHashSet();
    for (AndroidFacet f : dependentFacets) {
      moduleNames.add(f.getModule().getName());
    }
    try {
      for (Library library : libraries) {
        // We should only add .aar dependencies if they aren't already provided as modules.
        // For now, the way we associate them with each other is via the library name;
        // in the future the model will provide this for us
        String libraryName = library.getArtifactAddress();
        if (!moduleNames.contains(libraryName)) {
          File resFolder = new File(library.getResFolder());
          if (resFolder.exists()) {
            files.put(resFolder, libraryName);
            // Don't add it again!
            moduleNames.add(libraryName);
          }
        }
      }
    }
    catch (UnsupportedOperationException e) {
      // This happens when there is an incompatibility between the builder-model interfaces embedded in Android Studio and the
      // cached model.
      // If we got here is because this code got invoked before project sync happened (e.g. when reopening a project with open editors).
      // Project sync now is smart enough to handle this case and will trigger a full sync.
      LOG.warn("Incompatibility found between the IDE's builder-model and the cached Gradle model", e);
    }
    return files;
  }

  // TODO: b/23032391
  private static void addGradleLibraries(List<Library> list, AndroidModuleModel androidModuleModel) {
    list.addAll(androidModuleModel.getSelectedMainCompileLevel2Dependencies().getAndroidLibraries());
  }

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

  @Override
  public void dispose() {
    super.dispose();
  }

  /**
   * Returns the libraries among the app resources, if any
   */
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
          myIds.addAll(library.getAllDeclaredIds().keySet());
        }
      }
      // Also add all ids from resource types, just in case it contains things that are not in the libraries.
      myIds.addAll(super.getItemsOfType(ResourceNamespace.TODO, ResourceType.ID));
    }
    return myIds;
  }

  @Override
  @NotNull
  public Collection<String> getItemsOfType(@NotNull ResourceNamespace namespace, @NotNull ResourceType type) {
    synchronized (ITEM_MAP_LOCK) {
      // TODO(namespaces): store all ID resources in the repositories and stop reading R.txt.
      return type == ResourceType.ID ? getAllIds() : super.getItemsOfType(namespace, type);
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
    synchronized (RESOURCE_MAP_LOCK) {
      myResourceDirMap = null;
    }
    invalidateResourceDirs();

    if (resources.equals(getChildren())) {
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
    ResourceIdManager.get(myFacet.getModule()).resetDynamicIds();
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
        if (r instanceof FileResourceRepository) {
          FileResourceRepository repository = (FileResourceRepository)r;
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

  private ResourceVisibilityLookup myResourceVisibility;
  private ResourceVisibilityLookup.Provider myResourceVisibilityProvider;

  @Nullable
  public ResourceVisibilityLookup.Provider getResourceVisibilityProvider() {
    if (myResourceVisibilityProvider == null) {
      if (!myFacet.requiresAndroidModel() || myFacet.getConfiguration().getModel() == null) {
        return null;
      }
      myResourceVisibilityProvider = new ResourceVisibilityLookup.Provider();
    }

    return myResourceVisibilityProvider;
  }

  @NotNull
  public ResourceVisibilityLookup getResourceVisibility(@NotNull AndroidFacet facet) {
    // TODO: b/23032391
    AndroidModuleModel androidModel = AndroidModuleModel.get(facet);
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
      AndroidModuleModel androidModel = AndroidModuleModel.get(myFacet);
      if (androidModel == null) {
        // normally doesn't happen since we check in getResourceVisibility,
        // but can be triggered during a sync (b/22523040)
        return false;
      }
      myResourceVisibility = provider.get(androidModel.getAndroidProject(), androidModel.getSelectedVariant());
    }

    return myResourceVisibility.isPrivate(type, name);
  }
}
