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
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.Locale;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceRepositoryUtil;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.projectmodel.ExternalAndroidLibrary;
import com.android.resources.aar.AarResourceRepository;
import com.android.tools.idea.AndroidProjectModelUtils;
import com.android.tools.concurrency.AndroidIoManager;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.model.Namespacing;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.res.LocalResourceRepository.EmptyRepository;
import com.android.tools.idea.res.SampleDataResourceRepository.SampleDataRepositoryManager;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
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
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;
import org.jetbrains.android.facet.AndroidFacet;
import com.android.tools.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidPlatforms;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class StudioResourceRepositoryManager implements Disposable, ResourceRepositoryManager {
  private static final Key<StudioResourceRepositoryManager> KEY = Key.create(StudioResourceRepositoryManager.class.getName());

  private static final Object APP_RESOURCES_LOCK = new Object();
  private static final Object PROJECT_RESOURCES_LOCK = new Object();
  private static final Object MODULE_RESOURCES_LOCK = new Object();
  private static final Object TEST_RESOURCES_LOCK = new Object();

  @NotNull private final AndroidFacet myFacet;
  @NotNull private final Namespacing myNamespacing;

  /**
   * If the module is namespaced, this is the shared {@link ResourceNamespace} instance corresponding to the package name from the manifest.
   */
  @Nullable private ResourceNamespace mySharedNamespaceInstance;
  @Nullable private ResourceNamespace mySharedTestNamespaceInstance;

  @GuardedBy("APP_RESOURCES_LOCK")
  private AppResourceRepository myAppResources;

  @GuardedBy("PROJECT_RESOURCES_LOCK")
  private ProjectResourceRepository myProjectResources;

  @GuardedBy("MODULE_RESOURCES_LOCK")
  private LocalResourceRepository myModuleResources;

  @GuardedBy("TEST_RESOURCES_LOCK")
  private LocalResourceRepository myTestAppResources;

  @GuardedBy("TEST_RESOURCES_LOCK")
  private LocalResourceRepository myTestModuleResources;

  @GuardedBy("PROJECT_RESOURCES_LOCK")
  private CachedValue<LocalesAndLanguages> myLocalesAndLanguages;

  /** Libraries and their corresponding resource repositories. */
  @GuardedBy("myLibraryLock")
  private Map<ExternalAndroidLibrary, AarResourceRepository> myLibraryResourceMap;

  private final Object myLibraryLock = new Object();

  @NotNull
  public static StudioResourceRepositoryManager getInstance(@NotNull AndroidFacet facet) {
    Namespacing namespacing = AndroidProjectModelUtils.getNamespacing(facet);
    StudioResourceRepositoryManager instance = facet.getUserData(KEY);

    if (instance != null && instance.myNamespacing != namespacing) {
      if (facet.replace(KEY, instance, null)) {
        Disposer.dispose(instance);
      }
      instance = null;
    }

    if (instance == null) {
      StudioResourceRepositoryManager manager = new StudioResourceRepositoryManager(facet, namespacing);
      instance = facet.putUserDataIfAbsent(KEY, manager);
      if (instance == manager) {
        // Our object ended up stored in the facet.
        Disposer.register(facet, instance);
        AndroidProjectRootListener.ensureSubscribed(manager.getProject());
      }
    }

    return instance;
  }

  @Nullable
  public static StudioResourceRepositoryManager getInstance(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet == null ? null : getInstance(facet);
  }

  @Nullable
  public static StudioResourceRepositoryManager getInstance(@NotNull PsiElement element) {
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

  private StudioResourceRepositoryManager(@NotNull AndroidFacet facet, @NotNull Namespacing namespacing) {
    myFacet = facet;
    myNamespacing = namespacing;
  }

  /**
   * Returns the repository for the given namespace.
   *
   * @return the computed repository, or null if the namespace is {@link ResourceNamespace#ANDROID}
   *     and the framework resources cannot be determined for the module
   * @see #getAppResources()
   * @see #getFrameworkResources(Set)
   * @see #getCachedResourcesForNamespace(ResourceNamespace)
   */
  @Slow
  @Nullable
  public ResourceRepository getResourcesForNamespace(@NotNull ResourceNamespace namespace) {
    return namespace.equals(ResourceNamespace.ANDROID) ? getFrameworkResources(Collections.emptySet()) : getAppResources();
  }

  /**
   * Returns the repository with all non-framework resources available to a given module (in the current variant).
   * This includes not just the resources defined in this module, but in any other modules that this module depends
   * on, as well as any libraries those modules may depend on (e.g. appcompat). This repository also contains sample
   * data resources associated with the {@link ResourceNamespace#TOOLS} namespace.
   *
   * <p>When a layout is rendered in the layout editor, it is getting resources from the app resource repository:
   * it should see all the resources just like the app does.
   *
   * <p><b>Note:</b> This method should not be called on the event dispatch thread since it may take long time,
   * or block waiting for a read action lock.
   *
   * @return the computed repository
   * @see #getCachedAppResources()
   */
  @Override
  @Slow
  @NotNull
  public LocalResourceRepository getAppResources() {
    LocalResourceRepository appResources = getCachedAppResources();
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
   * Returns the previously computed repository with all non-framework resources available to a given module
   * (in the current variant). This includes not just the resources defined in this module, but in any other
   * modules that this module depends on, as well as any AARs those modules depend on (e.g. appcompat). This
   * repository also contains sample data resources associated with the {@link ResourceNamespace#TOOLS} namespace.
   *
   * @return the repository, or null if the repository hasn't been created yet
   * @see #getAppResources()
   */
  @Nullable
  public LocalResourceRepository getCachedAppResources() {
    synchronized (APP_RESOURCES_LOCK) {
      return myAppResources;
    }
  }

  /**
   * Returns the resource repository for a module along with all its (local) module dependencies.
   * The repository doesn't contain resources from AAR dependencies.
   *
   * <p><b>Note:</b> This method should not be called on the event dispatch thread since it may take long time,
   * or block waiting for a read action lock.
   *
   * @return the computed repository
   * @see #getCachedProjectResources()
   */
  @Override
  @Slow
  @NotNull
  public LocalResourceRepository getProjectResources() {
    LocalResourceRepository projectResources = getCachedProjectResources();
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
  public LocalResourceRepository getCachedProjectResources() {
    synchronized (PROJECT_RESOURCES_LOCK) {
      return myProjectResources;
    }
  }

  /**
   * Returns the resource repository for a single module (which can possibly have multiple resource folders).
   * Does not include resources from any dependencies.
   *
   * <p><b>Note:</b> This method should not be called on the event dispatch thread since it may take long time,
   * or block waiting for a read action lock.
   *
   * @return the computed repository
   * @see #getCachedModuleResources()
   */
  @Slow
  @NotNull
  public LocalResourceRepository getModuleResources() {
    LocalResourceRepository moduleResources = getCachedModuleResources();
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
          registerIfDisposable(this, myModuleResources);
        }
        return myModuleResources;
      }
    });
  }

  /**
   * Returns the previously computed resource repository for a single module (which can possibly have multiple
   * resource folders). Does not include resources from any dependencies.
   *
   * @return the repository, or null if the repository hasn't been created yet
   * @see #getModuleResources()
   */
  @Nullable
  public LocalResourceRepository getCachedModuleResources() {
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
      synchronized (TEST_RESOURCES_LOCK) {
        if (myTestAppResources == null) {
          if (myFacet.isDisposed()) {
            return new EmptyRepository(getTestNamespace());
          }
          myTestAppResources = computeTestAppResources();
          registerIfDisposable(this, myTestAppResources);
        }
        return myTestAppResources;
      }
    });
  }

  /**
   * Returns the resource repository with test resources defined in the given module.
   */
  @NotNull
  public LocalResourceRepository getTestModuleResources() {
    return ApplicationManager.getApplication().runReadAction((Computable<LocalResourceRepository>)() -> {
      synchronized (TEST_RESOURCES_LOCK) {
        if (myTestModuleResources == null) {
          if (myFacet.isDisposed()) {
            return new EmptyRepository(getTestNamespace());
          }
          myTestModuleResources = ModuleResourceRepository.forTestResources(myFacet, getTestNamespace());
          registerIfDisposable(this, myTestModuleResources);
        }
        return myTestModuleResources;
      }
    });
  }

  @NotNull
  private LocalResourceRepository computeTestAppResources() {
    LocalResourceRepository moduleTestResources = getTestModuleResources();
    return TestAppResourceRepository.create(myFacet, moduleTestResources);
  }

  /**
   * Returns the resource repository with Android framework resources, for the module's compile SDK.
   *
   * <p><b>Note:</b> This method should not be called on the event dispatch thread since it may take long time.
   *
   * @param languages the set of ISO 639 language codes determining the subset of resources to load.
   *     May be empty to load only the language-neutral resources. The returned repository may contain resources
   *     for more languages than was requested.
   * @return the framework repository, or null if the SDK resources directory cannot be determined for the module
   */
  @Slow
  @Nullable
  public ResourceRepository getFrameworkResources(@NotNull Set<String> languages) {
    AndroidPlatform androidPlatform = AndroidPlatforms.getInstance(myFacet.getModule());
    if (androidPlatform == null) {
      return null;
    }

    return AndroidTargetData.get(androidPlatform.getSdkData(), androidPlatform.getTarget()).getFrameworkResources(languages);
  }

  /**
   * If namespacing is disabled, the namespace parameter is ignored and the method returns a list containing
   * the single resource repository returned by {@link #getAppResources()}. Otherwise the method returns
   * a list of module, library, or sample data resource repositories for the given namespace. Usually the returned
   * list will contain at most two resource repositories, one for a module and another for its user-defined sample
   * data. More repositories may be returned only when there is a package name collision between modules or
   * libraries.
   *
   * <p><b>Note:</b> This method should not be called on the event dispatch thread since it may take long time,
   * or block waiting for a read action lock.
   *
   * @param namespace the namespace to return resource repositories for
   * @return the repositories for the given namespace
   */
  @Slow
  @NotNull
  public List<ResourceRepository> getAppResourcesForNamespace(@NotNull ResourceNamespace namespace) {
    AppResourceRepository appRepository = (AppResourceRepository)getAppResources();
    if (myNamespacing == Namespacing.DISABLED) {
      return ImmutableList.of(appRepository);
    }
    return ImmutableList.copyOf(appRepository.getRepositoriesForNamespace(namespace));
  }

  @SuppressWarnings("Duplicates") // No way to refactor this without something like Variable Handles.
  public void resetResources() {
    resetLibraries();
    SampleDataRepositoryManager.getInstance(myFacet).reset();

    synchronized (MODULE_RESOURCES_LOCK) {
      if (myModuleResources != null) {
        disposeIfDisposable(myModuleResources);
        myModuleResources = null;
      }
    }

    synchronized (PROJECT_RESOURCES_LOCK) {
      if (myProjectResources != null) {
        Disposer.dispose(myProjectResources);
        myProjectResources = null;
        myLocalesAndLanguages = null;
      }
    }

    synchronized (APP_RESOURCES_LOCK) {
      if (myAppResources != null) {
        Disposer.dispose(myAppResources);
        myAppResources = null;
      }
    }

    synchronized (TEST_RESOURCES_LOCK) {
      if (myTestAppResources != null) {
        disposeIfDisposable(myTestAppResources);
        myTestAppResources = null;
      }
      if (myTestModuleResources != null) {
        disposeIfDisposable(myTestModuleResources);
        myTestModuleResources = null;
      }
    }
  }

  private static void disposeIfDisposable(@NotNull Object object) {
    if (object instanceof Disposable) {
      Disposer.dispose((Disposable)object);
    }
  }

  private static void registerIfDisposable(@NotNull Disposable parent, @NotNull Object object) {
    if (object instanceof Disposable) {
      Disposer.register(parent, (Disposable)object);
    }
  }

  @Override
  public void dispose() {
    // There's nothing to dispose in this object, but the actual resource repositories may need to do
    // clean-up and they are children of this object in the Disposer hierarchy.
  }

  public void resetAllCaches() {
    resetResources();
    ConfigurationManager.getOrCreateInstance(myFacet.getModule()).getResolverCache().reset();
    ResourceFolderRegistry.getInstance(getProject()).reset();
    AarResourceRepositoryCache.getInstance().clear();
  }

  @NotNull
  private Project getProject() {
    return myFacet.getModule().getProject();
  }

  private void resetLibraries() {
    synchronized (myLibraryLock) {
      myLibraryResourceMap = null;
    }
  }

  void updateRootsAndLibraries() {
    try {
      ProjectResourceRepository projectResources = (ProjectResourceRepository)getCachedProjectResources();
      AppResourceRepository appResources = (AppResourceRepository)getCachedAppResources();
      if (projectResources != null) {
        projectResources.updateRoots();
      }

      Map<ExternalAndroidLibrary, AarResourceRepository> oldLibraryResourceMap;
      synchronized (myLibraryLock) {
        // Preserve the old library resources during update to prevent them from being garbage collected prematurely.
        oldLibraryResourceMap = myLibraryResourceMap;
        myLibraryResourceMap = null;
      }
      if (appResources != null) {
        appResources.updateRoots(getLibraryResources());
      }

      // Access oldLibraryResourceMap to make sure that it is still in scope at this point.
      // This condition should never be true, but exists to prevent the compiler from optimizing
      // away oldLibraryResourceMap.
      if (oldLibraryResourceMap != null && oldLibraryResourceMap.size() == Integer.MAX_VALUE) {
        throw new AssertionError();
      }
    }
    catch (IllegalStateException e) {
      if (!myFacet.isDisposed()) {
        throw e;
      }
      // Ignore exceptions caused by the facet disposal (b/162211246).
    }
  }

  @Override
  @NotNull
  public Namespacing getNamespacing() {
    return myNamespacing;
  }

  /**
   * Returns the {@link ResourceNamespace} used by the current module.
   *
   * <p>This is read from the manifest, so needs to be run inside a read action.
   */
  @Override
  @NotNull
  public ResourceNamespace getNamespace() {
    if (myNamespacing == Namespacing.DISABLED) {
      return ResourceNamespace.RES_AUTO;
    }

    String packageName = ProjectSystemUtil.getModuleSystem(myFacet).getPackageName();
    if (packageName == null) {
      return ResourceNamespace.RES_AUTO;
    }

    if (mySharedNamespaceInstance == null || !packageName.equals(mySharedNamespaceInstance.getPackageName())) {
      mySharedNamespaceInstance = ResourceNamespace.fromPackageName(packageName);
    }

    return mySharedNamespaceInstance;
  }

  /**
   * Returns the {@link ResourceNamespace} used by test resources of the current module.
   *
   * TODO(namespaces): figure out semantics of test resources with namespaces.
   */
  @NotNull
  public ResourceNamespace getTestNamespace() {
    if (myNamespacing == Namespacing.DISABLED) {
      return ResourceNamespace.RES_AUTO;
    }

    String testPackageName = ProjectSystemUtil.getModuleSystem(myFacet).getTestPackageName();
    if (testPackageName == null) {
      return ResourceNamespace.RES_AUTO;
    }

    if (mySharedTestNamespaceInstance == null || !testPackageName.equals(mySharedTestNamespaceInstance.getPackageName())) {
      mySharedTestNamespaceInstance = ResourceNamespace.fromPackageName(testPackageName);
    }

    return mySharedTestNamespaceInstance;
  }

  /**
   * Returns all resource directories.
   *
   * <p><b>Note:</b> This method should not be called on the event dispatch thread since it may take long time,
   * or block waiting for a read action lock.
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
  public AarResourceRepository findLibraryResources(@NotNull ExternalAndroidLibrary library) {
    return getLibraryResourceMap().get(library);
  }

  /**
   * Returns resource repositories for all libraries the app depends upon directly or indirectly.
   *
   * <p><b>Note:</b> This method should not be called on the event dispatch thread since it may take long time,
   * or block waiting for a read action lock.
   */
  @Slow
  @NotNull
  public Collection<AarResourceRepository> getLibraryResources() {
    return getLibraryResourceMap().values();
  }

  @NotNull
  private Map<ExternalAndroidLibrary, AarResourceRepository> getLibraryResourceMap() {
    synchronized (myLibraryLock) {
      if (myLibraryResourceMap == null) {
        myLibraryResourceMap = computeLibraryResourceMap();
      }
      return myLibraryResourceMap;
    }
  }

  @NotNull
  private Map<ExternalAndroidLibrary, AarResourceRepository> computeLibraryResourceMap() {
    Collection<ExternalAndroidLibrary> libraries = AndroidProjectModelUtils.findDependenciesWithResources(myFacet.getModule()).values();

    AarResourceRepositoryCache aarResourceRepositoryCache = AarResourceRepositoryCache.getInstance();
    Function<ExternalAndroidLibrary, AarResourceRepository> factory = myNamespacing == Namespacing.DISABLED ?
                                                                      aarResourceRepositoryCache::getSourceRepository :
                                                                      aarResourceRepositoryCache::getProtoRepository;

    ExecutorService executor = AndroidIoManager.getInstance().getBackgroundDiskIoExecutor();

    // Construct the repositories in parallel.
    Map<ExternalAndroidLibrary, Future<AarResourceRepository>> futures = Maps.newHashMapWithExpectedSize(libraries.size());
    for (ExternalAndroidLibrary library : libraries) {
      futures.put(library, executor.submit(() -> factory.apply(library)));
    }

    // Gather all the results.
    ImmutableMap.Builder<ExternalAndroidLibrary, AarResourceRepository> map = ImmutableMap.builder();
    for (Map.Entry<ExternalAndroidLibrary, Future<AarResourceRepository>> entry : futures.entrySet()) {
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

  /**
   * Returns all locales of the project resources.
   */
  @Override
  @NotNull
  public ImmutableList<Locale> getLocalesInProject() {
    return getLocalesAndLanguages().locales;
  }

  /**
   * Returns a set of ISO 639 language codes derived from locales of the project resources.
   */
  @NotNull
  public ImmutableSortedSet<String> getLanguagesInProject() {
    return getLocalesAndLanguages().languages;
  }

  @NotNull
  private LocalesAndLanguages getLocalesAndLanguages() {
    synchronized (PROJECT_RESOURCES_LOCK) {
      if (myLocalesAndLanguages == null) {
        myLocalesAndLanguages = CachedValuesManager.getManager(getProject()).createCachedValue(
          () -> {
            // Get locales from modules, but not libraries.
            LocalResourceRepository projectResources = getProjectResources(myFacet);
            SortedSet<LocaleQualifier> localeQualifiers = ResourceRepositoryUtil.getLocales(projectResources);
            ImmutableList.Builder<Locale> localesBuilder = ImmutableList.builderWithExpectedSize(localeQualifiers.size());
            ImmutableSortedSet.Builder<String> languagesBuilder = ImmutableSortedSet.naturalOrder();
            for (LocaleQualifier localeQualifier : localeQualifiers) {
              localesBuilder.add(Locale.create(localeQualifier));
              String language = localeQualifier.getLanguage();
              if (language != null) {
                languagesBuilder.add(language);
              }
            }
            return CachedValueProvider.Result.create(new LocalesAndLanguages(localesBuilder.build(), languagesBuilder.build()),
                                                     projectResources);
          });
      }
      return myLocalesAndLanguages.getValue();
    }
  }

  private static class LocalesAndLanguages {
    @NotNull final ImmutableList<Locale> locales;
    @NotNull final ImmutableSortedSet<String> languages;

    LocalesAndLanguages(@NotNull ImmutableList<Locale> locales, @NotNull ImmutableSortedSet<String> languages) {
      this.locales = locales;
      this.languages = languages;
    }
  }
}
