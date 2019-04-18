/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.annotations.concurrency.GuardedBy;
import com.android.annotations.concurrency.Slow;
import com.android.builder.model.AaptOptions;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.builder.model.level2.Library;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.repository.ResourceVisibilityLookup;
import com.android.ide.common.resources.ResourceRepository;
import com.android.projectmodel.ExternalLibrary;
import com.android.tools.idea.AndroidProjectModelUtils;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.res.LocalResourceRepository.EmptyRepository;
import com.android.tools.idea.res.SampleDataResourceRepository.SampleDataRepositoryManager;
import com.android.tools.idea.resources.aar.AarResourceRepository;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.function.Function;
import org.jetbrains.android.dom.manifest.AndroidManifestUtils;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ResourceRepositoryManager implements Disposable {
  private static final Key<ResourceRepositoryManager> KEY = Key.create(ResourceRepositoryManager.class.getName());

  private static final Object APP_RESOURCES_LOCK = new Object();
  private static final Object PROJECT_RESOURCES_LOCK = new Object();
  private static final Object MODULE_RESOURCES_LOCK = new Object();
  private static final Object TEST_APP_RESOURCES_LOCK = new Object();

  @NotNull private final AndroidFacet myFacet;
  @NotNull private final AaptOptions.Namespacing myNamespacing;

  /**
   * If the module is namespaced, this is the shared {@link ResourceNamespace} instance corresponding to the package name from the manifest.
   */
  @Nullable private ResourceNamespace myCachedNamespace;

  @GuardedBy("APP_RESOURCES_LOCK")
  private AppResourceRepository myAppResources;

  @GuardedBy("PROJECT_RESOURCES_LOCK")
  private ProjectResourceRepository myProjectResources;

  @GuardedBy("MODULE_RESOURCES_LOCK")
  private LocalResourceRepository myModuleResources;

  @GuardedBy("TEST_APP_RESOURCES_LOCK")
  private LocalResourceRepository myTestAppResources;

  /** Libraries and their corresponding resource repositories. */
  @GuardedBy("myLibraryLock")
  private Map<ExternalLibrary, AarResourceRepository> myLibraryResourceMap;
  @GuardedBy("myLibraryLock")
  @Nullable private ResourceVisibilityLookup.Provider myResourceVisibilityProvider;

  private final Object myLibraryLock = new Object();

  @NotNull
  public static ResourceRepositoryManager getInstance(@NotNull AndroidFacet facet) {
    AaptOptions.Namespacing namespacing = AndroidProjectModelUtils.getNamespacing(facet);
    ResourceRepositoryManager instance = facet.getUserData(KEY);

    if (instance != null && instance.myNamespacing != namespacing) {
      if (facet.replace(KEY, instance, null)) {
        Disposer.dispose(instance);
      }
      instance = null;
    }

    if (instance == null) {
      ResourceRepositoryManager manager = new ResourceRepositoryManager(facet, namespacing);
      instance = facet.putUserDataIfAbsent(KEY, manager);
      if (instance == manager) {
        // Our object ended up stored in the facet.
        Disposer.register(facet, instance);
        AndroidProjectRootListener.ensureSubscribed(facet.getModule().getProject());
      }
    }

    return instance;
  }

  @Nullable
  public static ResourceRepositoryManager getInstance(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet == null ? null : getInstance(facet);
  }

  @Nullable
  public static ResourceRepositoryManager getInstance(@NotNull PsiElement element) {
    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module == null) {
      return null;
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return null;
    }

    return getInstance(facet);
  }

  /**
   * Computes and returns the app resources.
   *
   * <p><b>Note:</b> This method should not be called on the event dispatch thread since it may take long time, or block waiting for a read
   * action lock.
   *
   * @return the resource repository or null if the module is not an Android module
   * @see #getAppResources()
   */
  @Slow
  @Nullable
  public static LocalResourceRepository getAppResources(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet != null ? getAppResources(facet) : null;
  }

  /**
   * Computes and returns the app resources.
   *
   * <p><b>Note:</b> This method should not be called on the event dispatch thread since it may take long time, or block waiting for a read
   * action lock.
   *
   * @see #getAppResources()
   */
  @Slow
  @NotNull
  public static LocalResourceRepository getAppResources(@NotNull AndroidFacet facet) {
    return getInstance(facet).getAppResources();
  }

  /**
   * Computes and returns the project resources.
   *
   * <p><b>Note:</b> This method should not be called on the event dispatch thread since it may take long time, or block waiting for a read
   * action lock.
   *
   * @return the resource repository or null if the module is not an Android module
   * @see #getProjectResources()
   */
  @Slow
  @Nullable
  public static LocalResourceRepository getProjectResources(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet != null ? getProjectResources(facet) : null;
  }

  /**
   * Computes and returns the project resources.
   *
   * <p><b>Note:</b> This method should not be called on the event dispatch thread since it may take long time, or block waiting for a read
   * action lock.
   *
   * @see #getProjectResources()
   */
  @Slow
  @NotNull
  public static LocalResourceRepository getProjectResources(@NotNull AndroidFacet facet) {
    return getInstance(facet).getProjectResources();
  }

  /**
   * Computes and returns the module resources.
   *
   * <p><b>Note:</b> This method should not be called on the event dispatch thread since it may take long time, or block waiting for a read
   * action lock.
   *
   * @return the resource repository or null if the module is not an Android module
   * @see #getModuleResources()
   */
  @Slow
  @Nullable
  public static LocalResourceRepository getModuleResources(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet != null ? getModuleResources(facet) : null;
  }

  /**
   * Computes and returns the module resources.
   *
   * <p><b>Note:</b> This method should not be called on the event dispatch thread since it may take long time, or block waiting for a read
   * action lock.
   *
   * @see #getModuleResources()
   */
  @Slow
  @NotNull
  public static LocalResourceRepository getModuleResources(@NotNull AndroidFacet facet) {
    return getInstance(facet).getModuleResources();
  }

  private ResourceRepositoryManager(@NotNull AndroidFacet facet, @NotNull AaptOptions.Namespacing namespacing) {
    myFacet = facet;
    myNamespacing = namespacing;
  }

  /**
   * Returns true if this project is build with Gradle but the AndroidModuleModel did not exist when the resources were cached.
   * And reset the state.
   */
  public static boolean testAndClearTempResourceCached(@NotNull Project project) {
    if (project.getUserData(AppResourceRepository.TEMPORARY_RESOURCE_CACHE) != Boolean.TRUE) {
      return false;
    }
    project.putUserData(AppResourceRepository.TEMPORARY_RESOURCE_CACHE, null);
    return true;
  }

  @NotNull
  public static Collection<Library> findAarLibraries(@NotNull AndroidFacet facet) {
    List<Library> libraries = new ArrayList<>();
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

  private static void addGradleLibraries(@NotNull List<Library> list, @NotNull AndroidModuleModel androidModuleModel) {
    list.addAll(androidModuleModel.getSelectedMainCompileLevel2Dependencies().getAndroidLibraries());
  }

  /**
   * Returns the repository with all non-framework resources available to a given module (in the current variant). This includes not just
   * the resources defined in this module, but in any other modules that this module depends on, as well as any libraries those modules may
   * depend on (e.g. appcompat). This repository also contains sample data resources associated with the {@link ResourceNamespace#TOOLS}
   * namespace.
   *
   * <p>When a layout is rendered in the layout editor, it is getting resources from the app resource repository: it should see all
   * the resources just like the app does.
   *
   * <p><b>Note:</b> This method should not be called on the event dispatch thread since it may take long time, or block waiting for a read
   * action lock.
   *
   * @return the computed repository
   * @see #getExistingAppResources()
   */
  @Slow
  @NotNull
  public LocalResourceRepository getAppResources() {
    LocalResourceRepository appResources = getExistingAppResources();
    if (appResources != null) {
      return appResources;
    }

    getLibraryResources(); // Precompute library resources to do less work inside the read action below.

    return ApplicationManager.getApplication().runReadAction((Computable<LocalResourceRepository>)() -> {
      synchronized (APP_RESOURCES_LOCK) {
        if (myAppResources == null) {
          if (myFacet.isDisposed()) {
            return new EmptyRepository(getNamespace());
          }
          myAppResources = AppResourceRepository.create(myFacet, getLibraryResources());
          Disposer.register(this, myAppResources);
        }
        return myAppResources;
      }
    });
  }

  /**
   * Returns the previously computed repository with all non-framework resources available to a given module (in the current variant).
   * This includes not just the resources defined in this module, but in any other modules that this module depends on, as well as any AARs
   * those modules depend on (e.g. appcompat). This repository also contains sample data resources associated with
   * the {@link ResourceNamespace#TOOLS} namespace.
   *
   * @return the repository, or null if the repository hasn't been created yet
   * @see #getAppResources()
   */
  @Nullable
  public LocalResourceRepository getExistingAppResources() {
    synchronized (APP_RESOURCES_LOCK) {
      return myAppResources;
    }
  }

  /**
   * Returns the resource repository for a module along with all its (local) module dependencies.
   * The repository doesn't contain resources from AAR dependencies.
   *
   * <p><b>Note:</b> This method should not be called on the event dispatch thread since it may take long time, or block waiting for a read
   * action lock.
   *
   * @return the computed repository
   * @see #getExistingProjectResources()
   */
  @Slow
  @NotNull
  public LocalResourceRepository getProjectResources() {
    LocalResourceRepository projectResources = getExistingProjectResources();
    if (projectResources != null) {
      return projectResources;
    }

    return ApplicationManager.getApplication().runReadAction((Computable<LocalResourceRepository>)() -> {
      synchronized (PROJECT_RESOURCES_LOCK) {
        if (myProjectResources == null) {
          if (myFacet.isDisposed()) {
            return new EmptyRepository(getNamespace());
          }
          myProjectResources = ProjectResourceRepository.create(myFacet);
          Disposer.register(this, myProjectResources);
        }
        return myProjectResources;
      }
    });
  }

  /**
   * Returns the previously computed resource repository for a module along with all its (local) module dependencies.
   * The repository doesn't contain resources from AAR dependencies.
   *
   * @return the repository, or null if the repository hasn't been created yet
   * @see #getProjectResources()
   */
  @Nullable
  public LocalResourceRepository getExistingProjectResources() {
    synchronized (PROJECT_RESOURCES_LOCK) {
      return myProjectResources;
    }
  }

  /**
   * Returns the resource repository for a single module (which can possibly have multiple resource folders). Does not include resources
   * from any dependencies.
   *
   * <p><b>Note:</b> This method should not be called on the event dispatch thread since it may take long time, or block waiting for a read
   * action lock.
   *
   * @return the computed repository
   * @see #getExistingModuleResources()
   */
  @Slow
  @NotNull
  public LocalResourceRepository getModuleResources() {
    LocalResourceRepository moduleResources = getExistingModuleResources();
    if (moduleResources != null) {
      return moduleResources;
    }

    return ApplicationManager.getApplication().runReadAction((Computable<LocalResourceRepository>)() -> {
      synchronized (MODULE_RESOURCES_LOCK) {
        if (myModuleResources == null) {
          if (myFacet.isDisposed()) {
            return new EmptyRepository(getNamespace());
          }
          myModuleResources = ModuleResourceRepository.forMainResources(myFacet, getNamespace());
          Disposer.register(this, myModuleResources);
        }
        return myModuleResources;
      }
    });
  }

  /**
   * Returns the previously computed resource repository for a single module (which can possibly have multiple resource folders).
   * Does not include resources from any dependencies.
   *
   * @return the repository, or null if the repository hasn't been created yet
   * @see #getModuleResources()
   */
  @Nullable
  public LocalResourceRepository getExistingModuleResources() {
    synchronized (MODULE_RESOURCES_LOCK) {
      return myModuleResources;
    }
  }

  /**
   * Returns the resource repository with all non-framework test resources available to a given module.
   */
  @NotNull
  public LocalResourceRepository getTestAppResources() {
    return ApplicationManager.getApplication().runReadAction((Computable<LocalResourceRepository>)() -> {
      synchronized (TEST_APP_RESOURCES_LOCK) {
        if (myTestAppResources == null) {
          if (myFacet.isDisposed()) {
            return new EmptyRepository(getTestNamespace());
          }
          myTestAppResources = computeTestAppResources();
          Disposer.register(this, myTestAppResources);
        }
        return myTestAppResources;
      }
    });
  }

  @NotNull
  private LocalResourceRepository computeTestAppResources() {
    // For disposal, the newly created test module repository ends up owned by the repository manager if returned from this method or the
    // TestAppResourceRepository if passed to it. This is slightly different to the main module repository, which is always owned by the
    // manager and stored in myModuleResources.
    LocalResourceRepository moduleTestResources = ModuleResourceRepository.forTestResources(myFacet, getTestNamespace());

    if (myNamespacing == AaptOptions.Namespacing.REQUIRED) {
      // TODO(namespaces): Confirm that's how test resources will work.
      return moduleTestResources;
    }

    AndroidModuleModel model = AndroidModuleModel.get(myFacet);
    if (model == null) {
      return moduleTestResources;
    }

    TestAppResourceRepository testAppRepo = TestAppResourceRepository.create(myFacet, moduleTestResources, model);
    Disposer.register(testAppRepo, moduleTestResources);
    return testAppRepo;
  }

  /**
   * Returns the resource repository with Android framework resources, for the module's compile SDK.
   *
   * <p><b>Note:</b> This method should not be called on the event dispatch thread since it may take long time, or block waiting for a read
   * action lock.
   *
   * @param needLocales if the return repository should contain resources defined using a locale qualifier (e.g. all translation strings).
   *                    This makes creating the repository noticeably slower.
   * @return the framework repository or null if the SDK resources directory cannot be determined for the module.
   */
  @Slow
  @Nullable
  public ResourceRepository getFrameworkResources(boolean needLocales) {
    AndroidPlatform androidPlatform = AndroidPlatform.getInstance(myFacet.getModule());
    if (androidPlatform == null) {
      return null;
    }

    return androidPlatform.getSdkData().getTargetData(androidPlatform.getTarget()).getFrameworkResources(needLocales);
  }

  /**
   * If namespacing is disabled, the namespace parameter is ignored and the method returns a list containing the single resource repository
   * returned by {@link #getAppResources()}. Otherwise the method returns a list of module, library, or sample data resource
   * repositories for the given namespace. Usually the returned list will contain at most two resource repositories, one for a module and
   * another for its user-defined sample data. More repositories may be returned only when there is a package name collision between modules
   * or libraries.
   *
   * <p><b>Note:</b> This method should not be called on the event dispatch thread since it may take long time, or block waiting for a read
   * action lock.
   *
   * @param namespace the namespace to return resource repositories for
   * @return the repositories for the given namespace
   */
  @Slow
  @NotNull
  public List<ResourceRepository> getAppResourcesForNamespace(@NotNull ResourceNamespace namespace) {
    AppResourceRepository appRepository = (AppResourceRepository)getAppResources();
    if (myNamespacing == AaptOptions.Namespacing.DISABLED) {
      return ImmutableList.of(appRepository);
    }
    return appRepository.getRepositoriesForNamespace(namespace);
  }

  @SuppressWarnings("Duplicates") // No way to refactor this without something like Variable Handles.
  public void resetResources() {
    resetVisibility();
    resetLibraries();
    SampleDataRepositoryManager.getInstance(myFacet).reset();

    synchronized (MODULE_RESOURCES_LOCK) {
      if (myModuleResources != null) {
        Disposer.dispose(myModuleResources);
        myModuleResources = null;
      }
    }

    synchronized (PROJECT_RESOURCES_LOCK) {
      if (myProjectResources != null) {
        Disposer.dispose(myProjectResources);
        myProjectResources = null;
      }
    }

    synchronized (APP_RESOURCES_LOCK) {
      if (myAppResources != null) {
        Disposer.dispose(myAppResources);
        myAppResources = null;
      }
    }

    synchronized (TEST_APP_RESOURCES_LOCK) {
      if (myTestAppResources != null) {
        Disposer.dispose(myTestAppResources);
        myTestAppResources = null;
      }
    }
  }

  @Override
  public void dispose() {
    // There's nothing to dispose in this object, but the actual resource repositories may need to do clean-up and they are children
    // of this object in the Disposer hierarchy.
  }

  public void resetAllCaches() {
    resetResources();
    ConfigurationManager.getOrCreateInstance(myFacet.getModule()).getResolverCache().reset();
    ResourceFolderRegistry.getInstance(myFacet.getModule().getProject()).reset();
    AarResourceRepositoryCache.getInstance().clear();
  }

  private void resetVisibility() {
    synchronized (myLibraryLock) {
      myResourceVisibilityProvider = null;
    }
  }

  private void resetLibraries() {
    synchronized (myLibraryLock) {
      myLibraryResourceMap = null;
    }
  }

  void updateRootsAndLibraries() {
    resetVisibility();

    ProjectResourceRepository projectResources = (ProjectResourceRepository)getExistingProjectResources();
    AppResourceRepository appResources = (AppResourceRepository)getExistingAppResources();
    if (projectResources != null) {
      projectResources.updateRoots();

      if (appResources != null) {
        appResources.invalidateCache(projectResources);
      }
    }

    Map<ExternalLibrary, AarResourceRepository> oldLibraryResourceMap;
    synchronized (myLibraryLock) {
      // Preserve the old library resources during update to prevent them from being garbage collected prematurely.
      oldLibraryResourceMap = myLibraryResourceMap;
      myLibraryResourceMap = null;
    }
    if (appResources != null) {
      appResources.updateRoots(getLibraryResources());
    }

    if (oldLibraryResourceMap != null) {
      oldLibraryResourceMap.size(); // Access oldLibraryResourceMap to make sure that it is still in scope at this point.
    }
  }

  @NotNull
  public AaptOptions.Namespacing getNamespacing() {
    return myNamespacing;
  }

  /**
   * Returns the {@link ResourceNamespace} used by the current module.
   *
   * <p>This is read from the manifest, so needs to be run inside a read action.
   */
  @NotNull
  public ResourceNamespace getNamespace() {
    if (myNamespacing == AaptOptions.Namespacing.DISABLED) {
      return ResourceNamespace.RES_AUTO;
    }

    String packageName = AndroidManifestUtils.getPackageName(myFacet);
    if (packageName == null) {
      return ResourceNamespace.RES_AUTO;
    }

    if (myCachedNamespace == null || !packageName.equals(myCachedNamespace.getPackageName())) {
      myCachedNamespace = ResourceNamespace.fromPackageName(packageName);
    }

    return myCachedNamespace;
  }

  /**
   * Returns the {@link ResourceNamespace} used by test resources of the current module.
   */
  @NotNull
  public ResourceNamespace getTestNamespace() {
    return ResourceNamespace.TODO(); // TODO(namespaces): figure out semantics of test resources with namespaces.
  }

  @Nullable
  public ResourceVisibilityLookup.Provider getResourceVisibilityProvider() {
    synchronized (myLibraryLock) {
      if (myResourceVisibilityProvider == null) {
        if (!myFacet.requiresAndroidModel() || myFacet.getConfiguration().getModel() == null) {
          return null;
        }
        myResourceVisibilityProvider = new ResourceVisibilityLookup.Provider();
      }

      return myResourceVisibilityProvider;
    }
  }

  @NotNull
  public ResourceVisibilityLookup getResourceVisibility() {
    AndroidModuleModel androidModel = AndroidModuleModel.get(myFacet);
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
   * Returns all resource directories.
   *
   * <p><b>Note:</b> This method should not be called on the event dispatch thread since it may take long time, or block waiting for a read
   * action lock.
   */
  @Slow
  @NotNull
  public Collection<VirtualFile> getAllResourceDirs() {
    // TODO(b/76128326): manage the set of directories here.
    return ((AppResourceRepository)getAppResources()).getAllResourceDirs();
  }

  /**
   * Looks up a resource repository for the given library.
   *
   * @param library the library to get resources for
   * @return the corresponding resource repository, or null if not found
   */
  @Nullable
  public AarResourceRepository findLibraryResources(@NotNull ExternalLibrary library) {
    return getLibraryResourceMap().get(library);
  }

  /**
   * Returns resource repositories for all libraries the app depends upon directly or indirectly.
   *
   * <p><b>Note:</b> This method should not be called on the event dispatch thread since it may take long time, or block waiting for a read
   * action lock.
   */
  @Slow
  @NotNull
  public Collection<AarResourceRepository> getLibraryResources() {
    return getLibraryResourceMap().values();
  }

  @NotNull
  private Map<ExternalLibrary, AarResourceRepository> getLibraryResourceMap() {
    synchronized (myLibraryLock) {
      if (myLibraryResourceMap == null) {
        myLibraryResourceMap = computeLibraryResourceMap();
      }
      return myLibraryResourceMap;
    }
  }

  @NotNull
  private Map<ExternalLibrary, AarResourceRepository> computeLibraryResourceMap() {
    Collection<ExternalLibrary> libraries = AndroidProjectModelUtils.findDependenciesWithResources(myFacet.getModule()).values();

    AarResourceRepositoryCache aarResourceRepositoryCache = AarResourceRepositoryCache.getInstance();
    Function<ExternalLibrary, AarResourceRepository> factory = myNamespacing == AaptOptions.Namespacing.DISABLED ?
                                                               aarResourceRepositoryCache::getSourceRepository :
                                                               aarResourceRepositoryCache::getProtoRepository;

    int maxThreads = ForkJoinPool.getCommonPoolParallelism();
    ExecutorService executorService =
        AppExecutorUtil.createBoundedApplicationPoolExecutor(ResourceRepositoryManager.class.getName(), maxThreads);

    // Construct the repositories in parallel.
    Map<ExternalLibrary, Future<AarResourceRepository>> futures = Maps.newHashMapWithExpectedSize(libraries.size());
    for (ExternalLibrary library : libraries) {
      futures.put(library, executorService.submit(() -> factory.apply(library)));
    }

    // Gather all the results.
    ImmutableMap.Builder<ExternalLibrary, AarResourceRepository> map = ImmutableMap.builder();
    for (Map.Entry<ExternalLibrary, Future<AarResourceRepository>> entry : futures.entrySet()) {
      try {
        map.put(entry.getKey(), entry.getValue().get());
      }
      catch (ExecutionException e) {
        cancelPendingTasks(futures.values());
        Throwables.throwIfUnchecked(e.getCause());
        throw new UncheckedExecutionException(e.getCause());
      }
      catch (InterruptedException e) {
        cancelPendingTasks(futures.values());
        throw new ProcessCanceledException(e);
      }
    }

    return map.build();
  }

  private static void cancelPendingTasks(Collection<Future<AarResourceRepository>> futures) {
    futures.forEach(f -> f.cancel(true));
  }
}
