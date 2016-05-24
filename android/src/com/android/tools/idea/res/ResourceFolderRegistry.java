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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.BoundedTaskExecutor;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.PooledThreadExecutor;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class ResourceFolderRegistry {
  private final static Object DIR_MAP_LOCK = new Object();
  private final static Map<VirtualFile, ResourceFolderRepository> ourDirMap = Maps.newHashMap();

  public static void reset() {
    synchronized (DIR_MAP_LOCK) {
      for (Map.Entry<VirtualFile, ResourceFolderRepository> entry : ourDirMap.entrySet()) {
        VirtualFile dir = entry.getKey();
        ResourceFolderRepository repository = entry.getValue();
        Project project = repository.getFacet().getModule().getProject();
        PsiProjectListener.removeRoot(project, dir, repository);
      }
      ourDirMap.clear();
    }
  }

  @NotNull
  public static ResourceFolderRepository get(@NotNull final AndroidFacet facet, @NotNull final VirtualFile dir) {
    synchronized (DIR_MAP_LOCK) {
      ResourceFolderRepository repository = ourDirMap.get(dir);
      if (repository == null) {
        Project project = facet.getModule().getProject();
        repository = ResourceFolderRepository.create(facet, dir, null);
        putRepositoryInCache(project, dir, repository);
      }
      return repository;
    }
  }

  private static void putRepositoryInCache(@NotNull Project project, @NotNull final VirtualFile dir,
                                           @NotNull ResourceFolderRepository repository) {
    synchronized (DIR_MAP_LOCK) {
      PsiProjectListener.addRoot(project, dir, repository);
      // Some of the resources in the ResourceFolderRepository might actually contain pointers to the Project instance so we need
      // to make sure we invalidate those whenever the project is closed.
      Disposer.register(project, new Disposable() {
        @Override
        public void dispose() {
          synchronized (DIR_MAP_LOCK) {
            ResourceFolderRepository repository = ourDirMap.remove(dir);
            if (repository != null) {
              repository.dispose();
            }
          }
        }
      });

      ourDirMap.put(dir, repository);
    }
  }

  /**
   * Filter out directories that are already cached in the registry.
   * @param resDirectories
   */
  private static void filterOutCached(Map<VirtualFile, AndroidFacet> resDirectories) {
    resDirectories.keySet().removeAll(ourDirMap.keySet());
  }

  /**
   * Grabs resource directories from the given facets and pairs the directory with an arbitrary
   * AndroidFacet which happens to depend on the directory.
   *
   * @param facets set of facets which may have resource directories
   */
  @NotNull
  static Map<VirtualFile, AndroidFacet> getResourceDirectoriesForFacets(@NotNull List<AndroidFacet> facets) {
    Map<VirtualFile, AndroidFacet> resDirectories = Maps.newHashMap();
    for (AndroidFacet facet : facets) {
      for (VirtualFile resourceDir : facet.getAllResourceDirectories()) {
        if (!resDirectories.containsKey(resourceDir)) {
          resDirectories.put(resourceDir, facet);
        }
      }
    }
    return resDirectories;
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
      synchronized (DIR_MAP_LOCK) {
        indicator.setText("Indexing resources");
        indicator.setIndeterminate(false);
        Map<VirtualFile, AndroidFacet> resDirectories = getResourceDirectoriesForFacets(facets);
        filterOutCached(resDirectories);
        // Might already be done, as there can be a race for filling the memory caches.
        if (resDirectories.isEmpty()) {
          return;
        }
        // Make sure the cache root is created before parallel execution to avoid racing to create the root.
        File projectCacheRoot = ResourceFolderRepositoryFileCacheService.get().getProjectDir(myProject);
        if (projectCacheRoot == null) {
          return;
        }
        try {
          FileUtil.ensureExists(projectCacheRoot);
        }
        catch (IOException e) {
          return;
        }
        Application application = ApplicationManager.getApplication();
        List<ResourceFolderRepository> repositories;
        // Beware if the current thread is holding the write lock. The current thread will
        // end up waiting for helper threads to finish, and the helper threads will be
        // acquiring a read lock (which would then block because of the write lock).
        assert !application.isWriteAccessAllowed();
        repositories = executeParallel(indicator, resDirectories);
        for (ResourceFolderRepository repository : repositories) {
          putRepositoryInCache(myProject, repository.getResourceDir(), repository);
        }
      }
    }

    private static List<ResourceFolderRepository> executeParallel(@NotNull ProgressIndicator indicator,
                                                                  @NotNull Map<VirtualFile, AndroidFacet> resDirectories) {
      int numDone = 0;
      List<ResourceFolderRepository> repositories = Lists.newArrayList();
      // Cap the threads to 4 for now. Scaling is okay from 1 to 2, but not necessarily much better as we go higher.
      int maxThreads = Math.min(4, Runtime.getRuntime().availableProcessors());
      BoundedTaskExecutor
        parallelExecutor = new BoundedTaskExecutor(PooledThreadExecutor.INSTANCE, maxThreads);
      List<Future<ResourceFolderRepository>> repositoryJobs = Lists.newArrayList();
      for (Map.Entry<VirtualFile, AndroidFacet> entry : resDirectories.entrySet()) {
        repositoryJobs.add(queueRepositoryFuture(parallelExecutor, entry.getValue(), entry.getKey()));
      }
      for (Future<ResourceFolderRepository> job : repositoryJobs) {
        if (indicator.isCanceled()) {
          break;
        }
        indicator.setFraction((double)numDone / resDirectories.size());
        try {
          repositories.add(job.get());
        }
        catch (InterruptedException e) {
          // If we get an exception, that's okay -- we stop pre-populating the cache, which is just for performance.
        }
        catch (ExecutionException ignored) {
        }
        ++numDone;
      }
      return repositories;
    }

    private static Future<ResourceFolderRepository> queueRepositoryFuture(
      @NotNull final BoundedTaskExecutor myParallelBuildExecutor,
      @NotNull final AndroidFacet facet,
      @NotNull final VirtualFile dir) {
      return myParallelBuildExecutor.submit(() -> ResourceFolderRepository.create(facet, dir, null));
    }
  }

}
