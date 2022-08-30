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

import static com.android.tools.idea.res.ResourceUpdateTracer.pathForLogging;
import static com.android.tools.idea.res.ResourceUpdateTracer.pathsForLogging;
import static com.android.utils.TraceUtils.getCurrentStack;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.tools.idea.concurrency.AndroidIoManager;
import com.android.tools.idea.model.Namespacing;
import com.android.utils.TraceUtils;
import com.android.utils.concurrency.CacheUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.intellij.ProjectTopics;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiTreeChangeListener;
import com.intellij.util.Consumer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * A project service that manages {@link ResourceFolderRepository} instances, creating them as necessary and reusing repositories for
 * the same directories when multiple modules need them. For every directory a namespaced and non-namespaced repository may be created,
 * if needed.
 */
public class ResourceFolderRegistry implements Disposable {
  @NotNull private final Project myProject;
  @NotNull private final Cache<VirtualFile, ResourceFolderRepository> myNamespacedCache = buildCache();
  @NotNull private final Cache<VirtualFile, ResourceFolderRepository> myNonNamespacedCache = buildCache();
  @NotNull private final ImmutableList<Cache<VirtualFile, ResourceFolderRepository>> myCaches =
      ImmutableList.of(myNamespacedCache, myNonNamespacedCache);

  public ResourceFolderRegistry(@NotNull Project project) {
    myProject = project;
    project.getMessageBus().connect(this).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        removeStaleEntries();
      }
    });
  }

  private static @NotNull Cache<VirtualFile, ResourceFolderRepository> buildCache() {
    return CacheBuilder.newBuilder().build();
  }

  public static @NotNull ResourceFolderRegistry getInstance(@NotNull Project project) {
    return project.getService(ResourceFolderRegistry.class);
  }

  public @NotNull ResourceFolderRepository get(@NotNull AndroidFacet facet, @NotNull VirtualFile dir) {
    // ResourceFolderRepository.create may require the IDE read lock. To avoid deadlocks it is always obtained first, before the caches
    // locks.
    return ReadAction.compute(() -> get(facet, dir, ResourceRepositoryManager.getInstance(facet).getNamespace()));
  }

  @VisibleForTesting
  @NotNull ResourceFolderRepository get(@NotNull AndroidFacet facet, @NotNull VirtualFile dir, @NotNull ResourceNamespace namespace) {
    Cache<VirtualFile, ResourceFolderRepository> cache =
        namespace == ResourceNamespace.RES_AUTO ? myNonNamespacedCache : myNamespacedCache;

    ResourceFolderRepository repository = CacheUtils.getAndUnwrap(cache, dir, () -> createRepository(facet, dir, namespace));

    assert repository.getNamespace().equals(namespace);

    // TODO(b/80179120): figure out why this is not always true.
    // assert repository.getFacet().equals(facet);

    return repository;
  }

  /**
   * Returns the resource repository for the given directory, or null if such repository doesn't already exist.
   */
  public @Nullable ResourceFolderRepository getCached(@NotNull VirtualFile dir, @NotNull Namespacing namespacing) {
    var cache = namespacing == Namespacing.REQUIRED ? myNamespacedCache : myNonNamespacedCache;
    return cache.getIfPresent(dir);
  }

  private static @NotNull ResourceFolderRepository createRepository(@NotNull AndroidFacet facet,
                                                                    @NotNull VirtualFile dir,
                                                                    @NotNull ResourceNamespace namespace) {
    if (ResourceUpdateTracer.isTracingActive() &&
        dir.getParent().getName().equals("main") && facet.getModule() != facet.getMainModule()) {
      ResourceUpdateTracer.logDirect(() -> "Incorrect facet " + facet.getModule().getName() + " is associated with the " +
                                           pathForLogging(dir) + " directory\n" + getCurrentStack());
      Module module = facet.getModule();
      ResourceUpdateTracer.logDirect(() -> "Content roots of " + module.getName() + ": " +
                                           pathsForLogging(getContentRoots(module), module.getProject()));
      Module mainModule = facet.getMainModule();
      ResourceUpdateTracer.logDirect(() -> "Content roots of " + mainModule.getName() + ": " +
                                           pathsForLogging(getContentRoots(mainModule), mainModule.getProject()));
    }

    // Don't create a persistent cache in tests to avoid unnecessary overhead.
    Executor executor = ApplicationManager.getApplication().isUnitTestMode() ?
                        runnable -> {} : AndroidIoManager.getInstance().getBackgroundDiskIoExecutor();
    ResourceFolderRepositoryCachingData cachingData =
        ResourceFolderRepositoryFileCacheService.get().getCachingData(facet.getModule().getProject(), dir, executor);
    return ResourceFolderRepository.create(facet, dir, namespace, cachingData);
  }

  private static @NotNull List<VirtualFile> getContentRoots(@NotNull Module module) {
    return Arrays.asList(ModuleRootManager.getInstance(module).getContentRoots());
  }

  public void reset() {
    ResourceUpdateTracer.logDirect(() -> TraceUtils.getSimpleId(this) + ".reset()");
    myNamespacedCache.invalidateAll();
    myNonNamespacedCache.invalidateAll();
  }

  private void removeStaleEntries() {
    // TODO(namespaces): listen to changes in modules' namespacing modes and dispose repositories which are no longer needed.
    ResourceUpdateTracer.logDirect(() -> TraceUtils.getSimpleId(this) + ".removeStaleEntries()");
    removeStaleEntries(myNamespacedCache);
    removeStaleEntries(myNonNamespacedCache);
  }

  private void removeStaleEntries(@NotNull Cache<VirtualFile, ResourceFolderRepository> cache) {
    Map<VirtualFile, ResourceFolderRepository> cacheAsMap = cache.asMap();
    if (cacheAsMap.isEmpty()) {
      ResourceUpdateTracer.logDirect(() -> TraceUtils.getSimpleId(this) + ".removeStaleEntries: cache is empty");
      return;
    }
    Set<AndroidFacet> facets = Sets.newHashSetWithExpectedSize(cacheAsMap.size());
    Set<VirtualFile> newResourceFolders = Sets.newHashSetWithExpectedSize(cacheAsMap.size());
    for (ResourceFolderRepository repository : cacheAsMap.values()) {
      AndroidFacet facet = repository.getFacet();
      if (!facet.isDisposed() && facets.add(facet)) {
        ResourceFolderManager folderManager = ResourceFolderManager.getInstance(facet);
        newResourceFolders.addAll(folderManager.getFolders());
        newResourceFolders.addAll(folderManager.getTestFolders());
      }
    }
    ResourceUpdateTracer.logDirect(() ->
        TraceUtils.getSimpleId(this) + ".removeStaleEntries retained " + pathsForLogging(newResourceFolders, myProject)
    );

    cacheAsMap.keySet().retainAll(newResourceFolders);
  }

  @Override
  public void dispose() {
    reset();
  }

  void dispatchToRepositories(@NotNull VirtualFile file, @NotNull BiConsumer<ResourceFolderRepository, VirtualFile> handler) {
    ResourceUpdateTracer.log(() -> "ResourceFolderRegistry.dispatchToRepositories(" +  pathForLogging(file) + ", ...) VFS change");
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    for (VirtualFile dir = file.isDirectory() ? file : file.getParent(); dir != null; dir = dir.getParent()) {
      for (Cache<VirtualFile, ResourceFolderRepository> cache : myCaches) {
        ResourceFolderRepository repository = cache.getIfPresent(dir);
        if (repository != null) {
          handler.accept(repository, file);
        }
      }
    }
  }

  void dispatchToRepositories(@NotNull VirtualFile file, @NotNull Consumer<PsiTreeChangeListener> invokeCallback) {
    ResourceUpdateTracer.log(() -> "ResourceFolderRegistry.dispatchToRepositories(" +  pathForLogging(file) + ", ...) PSI change");
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    for (VirtualFile dir = file.isDirectory() ? file : file.getParent(); dir != null; dir = dir.getParent()) {
      for (Cache<VirtualFile, ResourceFolderRepository> cache : myCaches) {
        ResourceFolderRepository repository = cache.getIfPresent(dir);
        if (repository != null) {
          invokeCallback.consume(repository.getPsiListener());
        }
      }
    }
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  /**
   * Populate the registry's in-memory ResourceFolderRepository caches (if not already cached).
   */
  public static class PopulateCachesTask extends DumbModeTask {
    @NotNull private final Project myProject;

    public PopulateCachesTask(@NotNull Project project) {
      myProject = project;
    }

    @Nullable
    @Override
    public DumbModeTask tryMergeWith(@NotNull DumbModeTask taskFromQueue) {
      if (taskFromQueue instanceof PopulateCachesTask && ((PopulateCachesTask)taskFromQueue).myProject.equals(myProject)) return this;
      return null;
    }

    @Override
    public void performInDumbMode(@NotNull ProgressIndicator indicator) {
      List<AndroidFacet> facets = ProjectFacetManager.getInstance(myProject).getFacets(AndroidFacet.ID);
      if (facets.isEmpty()) {
        return;
      }
      // Some directories in the registry may already be populated by this point, so filter them out.
      indicator.setText("Indexing resources");
      indicator.setIndeterminate(false);
      Map<VirtualFile, AndroidFacet> resDirectories = IdeResourcesUtil.getResourceDirectoriesForFacets(facets);
      // Might already be done, as there can be a race for filling the memory caches.
      if (resDirectories.isEmpty()) {
        return;
      }

      // Make sure the cache root is created before parallel execution to avoid racing to create the root.
      try {
        ResourceFolderRepositoryFileCacheService.get().createDirForProject(myProject);
      }
      catch (IOException e) {
        return;
      }

      Application application = ApplicationManager.getApplication();
      // Beware if the current thread is holding the write lock. The current thread will
      // end up waiting for helper threads to finish, and the helper threads will be
      // acquiring a read lock (which would then block because of the write lock).
      assert !application.isWriteAccessAllowed();

      int numDone = 0;

      ExecutorService parallelExecutor = AndroidIoManager.getInstance().getBackgroundDiskIoExecutor();
      List<Future<ResourceFolderRepository>> repositoryJobs = new ArrayList<>();
      for (Map.Entry<VirtualFile, AndroidFacet> entry : resDirectories.entrySet()) {
        AndroidFacet facet = entry.getValue();
        VirtualFile dir = entry.getKey();
        ResourceFolderRegistry registry = getInstance(myProject);
        repositoryJobs.add(parallelExecutor.submit(() -> registry.get(facet, dir)));
      }

      for (Future<ResourceFolderRepository> job : repositoryJobs) {
        if (indicator.isCanceled()) {
          break;
        }
        indicator.setFraction((double)numDone / resDirectories.size());
        try {
          job.get();
        }
        catch (ExecutionException e) {
          // If we get an exception, that's okay -- we stop pre-populating the cache, which is just for performance.
        }
        catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        ++numDone;
      }
    }
  }
}
