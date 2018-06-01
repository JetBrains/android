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
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.utils.concurrency.CacheUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * A project services that manages {@link ResourceFolderRepository} instances, creating them an necessary and reusing repositories for the
 * same directories when multiple modules need them. For every directory a namespaced and non-namespaced repository may be created, if
 * needed.
 *
 * <p>The repositories are stored using weak references, so can be garbage collected once no module uses them anymore. The repositories are
 * strongly referenced from {@link MultiResourceRepository} as children of the module resources repository.
 */
public class ResourceFolderRegistry {
  private final Cache<VirtualFile, ResourceFolderRepository> myNamespacedCache = buildCache();
  private final Cache<VirtualFile, ResourceFolderRepository> myNonNamespacedCache = buildCache();

  @NotNull
  private static Cache<VirtualFile, ResourceFolderRepository> buildCache() {
    return CacheBuilder.newBuilder().weakValues().build();
  }

  @NotNull
  public static ResourceFolderRegistry getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ResourceFolderRegistry.class);
  }

  public void reset() {
    myNamespacedCache.invalidateAll();
    myNonNamespacedCache.invalidateAll();
  }

  @NotNull
  public ResourceFolderRepository get(@NotNull final AndroidFacet facet, @NotNull final VirtualFile dir) {
    // ResourceFolderRepository.create may require the IDE read lock. To avoid deadlocks it is always obtained first, before the caches
    // locks.
    return ReadAction.compute(() -> get(facet, dir, ResourceRepositoryManager.getOrCreateInstance(facet).getNamespace()));
  }

  @NotNull
  @VisibleForTesting
  ResourceFolderRepository get(@NotNull final AndroidFacet facet, @NotNull final VirtualFile dir, @NotNull ResourceNamespace namespace) {
    Cache<VirtualFile, ResourceFolderRepository> cache =
      namespace == ResourceNamespace.RES_AUTO ? myNonNamespacedCache : myNamespacedCache;

    ResourceFolderRepository repository = CacheUtils.getAndUnwrap(cache, dir, () -> ResourceFolderRepository.create(facet, dir, namespace));
    assert repository.getNamespace().equals(namespace);

    // TODO(b/80179120): figure out why this is not always true.
    // assert repository.getFacet().equals(facet);

    return repository;
  }

  @Nullable
  public CachedRepositories getCached(@NotNull VirtualFile directory) {
    ResourceFolderRepository namespaced = myNamespacedCache.getIfPresent(directory);
    ResourceFolderRepository nonNamespaced = myNonNamespacedCache.getIfPresent(directory);
    return namespaced == null && nonNamespaced == null ? null : new CachedRepositories(namespaced, nonNamespaced);
  }

  /**
   * Populate the registry's in-memory ResourceFolderRepository caches (if not already cached).
   */
  public static class PopulateCachesTask extends DumbModeTask {
    @NotNull private final Project myProject;

    public PopulateCachesTask(@NotNull Project project) {
      myProject = project;
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
      Map<VirtualFile, AndroidFacet> resDirectories = AndroidResourceUtil.getResourceDirectoriesForFacets(facets);
      // Might already be done, as there can be a race for filling the memory caches.
      if (resDirectories.isEmpty()) {
        return;
      }
      // Make sure the cache root is created before parallel execution to avoid racing to create the root.
      Path projectCacheRoot = ResourceFolderRepositoryFileCacheService.get().getProjectDir(myProject);
      if (projectCacheRoot == null) {
        return;
      }
      try {
        FileUtil.ensureExists(projectCacheRoot.toFile());
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

      // Cap the threads to 4 for now. Scaling is okay from 1 to 2, but not necessarily much better as we go higher.
      int maxThreads = Math.min(4, Runtime.getRuntime().availableProcessors());
      ExecutorService
        parallelExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("ResourceFolderRegistry", maxThreads);
      List<Future<ResourceFolderRepository>> repositoryJobs = Lists.newArrayList();
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

  public static class CachedRepositories {
    @Nullable
    public final ResourceFolderRepository namespaced;

    @Nullable
    public final ResourceFolderRepository nonNamespaced;

    public CachedRepositories(@Nullable ResourceFolderRepository namespaced, @Nullable ResourceFolderRepository nonNamespaced) {
      this.namespaced = namespaced;
      this.nonNamespaced = nonNamespaced;
    }
  }
}
