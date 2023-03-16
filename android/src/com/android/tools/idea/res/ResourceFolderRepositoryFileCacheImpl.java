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

import static com.android.tools.res.AndroidPluginVersion.getAndroidPluginVersion;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.ide.caches.CachesInvalidator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages a local file cache for ResourceFolderRepository state, for faster project reload.
 */
class ResourceFolderRepositoryFileCacheImpl implements ResourceFolderRepositoryFileCache {
  private static final String CACHE_DIRECTORY = "caches/project_resources";
  private static final String INVALIDATION_MARKER_FILE = "invalidated.txt";

  @NotNull private final Path myRootDir;

  ResourceFolderRepositoryFileCacheImpl() {
    myRootDir = Paths.get(PathManager.getSystemPath()).resolve(CACHE_DIRECTORY);
  }

  ResourceFolderRepositoryFileCacheImpl(@NotNull Path rootDirParent) {
    myRootDir = rootDirParent.resolve(CACHE_DIRECTORY);
  }

  private static Logger getLogger() {
    return Logger.getInstance(ResourceFolderRepositoryFileCacheImpl.class);
  }

  @Override
  @Nullable
  public ResourceFolderRepositoryCachingData getCachingData(
      @NotNull Project project, @NotNull VirtualFile resourceDir, @Nullable Executor cacheCreationExecutor) {
    String codeVersion = getAndroidPluginVersion();
    if (codeVersion == null) {
      return null;
    }

    Path cacheFile = getCacheFile(project, resourceDir);
    if (cacheFile == null) {
      return null;
    }
    return new ResourceFolderRepositoryCachingData(cacheFile, isValid(), codeVersion, cacheCreationExecutor);
  }

  @Override
  public void createDirForProject(@NotNull Project project) throws IOException {
    Path dir = getProjectDir(project);
    if (dir == null) {
      throw new IOException();
    }
    FileUtil.ensureExists(dir.toFile());
  }

  @Nullable
  private Path getCacheFile(@NotNull Project project, @NotNull VirtualFile resourceDir) {
    // If directory is dirty, we cannot use the cache yet.
    if (!isValid()) {
      return null;
    }
    // Make up filename with hash codes. Try to tolerate hash collisions:
    // The cache file contents will list the original resourceDir as data source, so a potential
    // hash collision will be detected during loading and the cache file will be ignored.
    Path projectComponent = getProjectDir(project);
    if (projectComponent == null) {
      return null;
    }
    String dirComponent =
        FileUtil.sanitizeFileName(resourceDir.getParent().getName()) + '_' + Integer.toHexString(resourceDir.hashCode()) + ".dat";
    return projectComponent.resolve(dirComponent);
  }

  @VisibleForTesting
  @Nullable
  Path getRootDir() {
    if (!Files.isDirectory(myRootDir, LinkOption.NOFOLLOW_LINKS)) {
      try {
        Files.createDirectories(myRootDir);
      }
      catch (IOException e) {
        getLogger().error("Failed to create cache root directory " + myRootDir, e);
        return null;
      }
    }
    return myRootDir;
  }

  /**
   * Returns the parent directory where caches for a given project is stored.
   * Doesn't matter if the cache is invalidated.
   *
   * @return the project cache dir, or null on IO exceptions
   */
  @VisibleForTesting
  @Nullable
  Path getProjectDir(@NotNull Project project) {
    Path rootDir = getRootDir();
    if (rootDir == null) {
      return null;
    }
    return ProjectUtil.getProjectCachePath(project, rootDir);
  }

  @Override
  public void invalidate() {
    if (!isValid()) {
      return;
    }
    Path rootDir = getRootDir();
    if (rootDir == null) {
      return;
    }
    Path stampFile = rootDir.resolve(INVALIDATION_MARKER_FILE);
    try {
      Files.createFile(stampFile);
    }
    catch (IOException e) {
      getLogger().error("Failed to write cache invalidating stamp file " + stampFile, e);
    }
  }

  /**
   * Deletes the cache files from disk, clearing the invalidation stamp.
   */
  @VisibleForTesting
  void clear() {
    Path rootDir = getRootDir();
    if (rootDir == null) {
      return;
    }
    // First delete all the subdirectories but leave the invalidation marker intact.
    boolean[] errorDeletingDirectories = new boolean[1];
    try (Stream<Path> stream = Files.list(rootDir)) {
      stream.forEach(subCache -> {
        if (!subCache.getFileName().toString().equals(INVALIDATION_MARKER_FILE)) {
          try {
            FileUtil.delete(subCache);
          }
          catch (IOException e) {
            getLogger().error("Failed to delete " + subCache + " directory", e);
            errorDeletingDirectories[0] = true;
          }
        }
      });
    }
    catch (IOException ignored) {
    }

    if (!errorDeletingDirectories[0]) {
      // Finally, delete the invalidation marker file.
      Path invalidationMarker = rootDir.resolve(INVALIDATION_MARKER_FILE);
      try {
        FileUtil.delete(invalidationMarker);
      }
      catch (IOException e) {
        getLogger().error("Failed to delete " + invalidationMarker + " file", e);
      }
    }
  }

  /**
   * Checks if the cache is valid (not invalidated).
   */
  @VisibleForTesting
  boolean isValid() {
    Path rootDir = getRootDir();
    if (rootDir == null) {
      return false;
    }
    Path stampFile = rootDir.resolve(INVALIDATION_MARKER_FILE);
    return Files.notExists(stampFile);
  }

  /**
   * A task that manages and tracks the LRU state of resource repository file caches across projects.
   * - It deletes the cache if marked invalid (or version is unexpectedly different).
   * - When a project opens, it will place the project in the front of the LRU queue, and delete
   * caches for projects that have been bumped out of the queue. This helps limit the storage
   * used to be up to only N projects at a time.
   */
  static class ManageLruProjectFilesTask extends DumbModeTask {
    @NotNull private final Project myProject;

    private final static Object PROJECT_LRU_LOCK = new Object();
    private static final String LRU_FILE = "project_lru_list.txt";

    private static final int MAX_PROJECT_CACHES = 12;

    ManageLruProjectFilesTask(@NotNull Project project) {
      myProject = project;
    }

    @Nullable
    @Override
    public DumbModeTask tryMergeWith(@NotNull DumbModeTask taskFromQueue) {
      if (taskFromQueue instanceof ManageLruProjectFilesTask && ((ManageLruProjectFilesTask)taskFromQueue).myProject.equals(myProject)) {
        return this;
      }
      return null;
    }

    @Override
    public void performInDumbMode(@NotNull ProgressIndicator indicator) {
      if (myProject.isDisposed()) {
        return;
      }
      maintainLruCache(MAX_PROJECT_CACHES);
    }

    @VisibleForTesting
    void maintainLruCache(int maxProjectCaches) {
      assert maxProjectCaches > 0;
      ResourceFolderRepositoryFileCacheImpl cache = getCache();
      Path cacheRootDir = cache.getRootDir();
      if (cacheRootDir == null) {
        return;
      }
      // If invalid, clear the whole cache directory (including any invalidation stamps).
      if (!cache.isValid()) {
        cache.clear();
        return;
      }

      synchronized (PROJECT_LRU_LOCK) {
        try {
          List<String> projectsList = loadListOfProjectCaches(cacheRootDir);
          List<String> projectsToRemove = updateLruList(myProject, projectsList, maxProjectCaches);
          pruneOldProjects(cacheRootDir, projectsToRemove);
          writeListOfProjectCaches(cacheRootDir, projectsList);
        }
        catch (IOException e) {
          getLogger().error("Failed to maintain projects LRU cache for dir " + cacheRootDir, e);
        }
      }
    }

    @VisibleForTesting
    @NotNull
    static List<String> loadListOfProjectCaches(@NotNull Path cacheRootDir) throws IOException {
      Path lruFile = cacheRootDir.resolve(LRU_FILE);
      try {
        return Files.readAllLines(lruFile).stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
      }
      catch (NoSuchFileException e) {
        return new ArrayList<>();
      }
    }

    @VisibleForTesting
    static void writeListOfProjectCaches(@NotNull Path cacheRootDir, @NotNull List<String> projectsList) throws IOException {
      Path lruFile = cacheRootDir.resolve(LRU_FILE);
      Files.write(lruFile, projectsList);
    }

    @VisibleForTesting
    static List<String> updateLruList(@NotNull Project currentProject, @NotNull List<String> projectsList, int maxProjectCaches) {
      List<String> projectsToRemove = new ArrayList<>();
      Path currentProjectPath = getCache().getProjectDir(currentProject);
      if (currentProjectPath == null) {
        return projectsToRemove;
      }

      String currentProjectDir = currentProjectPath.getFileName().toString();
      projectsList.remove(currentProjectDir);
      projectsList.add(0, currentProjectDir);
      for (int i = projectsList.size(); --i >= maxProjectCaches;) {
        projectsToRemove.add(projectsList.remove(i));
      }
      return projectsToRemove;
    }

    private static void pruneOldProjects(@NotNull Path cacheRootDir, @NotNull List<String> childNames) {
      // Only attempt to remove directories that are subdirectories of the root.
      for (String child : childNames) {
        Preconditions.checkArgument(!child.isEmpty());
        Path path = cacheRootDir.resolve(child);
        if (!FileUtil.delete(path.toFile())) {
          if (Files.exists(path)) {
            getLogger().error("Failed to prune directory " + path);
          }
        }
      }
    }
  }

  @NotNull
  private static ResourceFolderRepositoryFileCacheImpl getCache() {
    return (ResourceFolderRepositoryFileCacheImpl)ResourceFolderRepositoryFileCacheService.get();
  }

  /**
   * Task that prunes unused resource directory caches within a given Project (which may come about
   * e.g., if one has moved a resource directory from one module to another).
   */
  static class PruneTask extends DumbModeTask {
    @NotNull private final Project myProject;

    PruneTask(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void performInDumbMode(@NotNull ProgressIndicator indicator) {
      if (myProject.isDisposed()) {
        return;
      }

      ResourceFolderRepositoryFileCacheImpl cache = getCache();
      Path projectCacheBase = cache.getProjectDir(myProject);
      if (projectCacheBase == null || !Files.exists(projectCacheBase)) {
        return;
      }
      List<AndroidFacet> facets = ProjectFacetManager.getInstance(myProject).getFacets(AndroidFacet.ID);
      Map<VirtualFile, AndroidFacet> resDirectories = IdeResourcesUtil.getResourceDirectoriesForFacets(facets);
      Set<Path> usedCacheDirectories = new HashSet<>();
      for (VirtualFile resourceDir : resDirectories.keySet()) {
        Path dir = cache.getCacheFile(myProject, resourceDir);
        if (dir != null) {
          usedCacheDirectories.add(dir);
        }
      }
      try (Stream<Path> stream = Files.list(projectCacheBase)) {
        stream.forEach(file -> {
          if (!usedCacheDirectories.contains(file) && !FileUtil.delete(file.toFile())) {
            getLogger().error("Failed to delete " + file);
          }
        });
      }
      catch (IOException e) {
        getLogger().error("Failed to prune cache files from " + projectCacheBase);
      }
    }

    @Nullable
    @Override
    public DumbModeTask tryMergeWith(@NotNull DumbModeTask taskFromQueue) {
      if (taskFromQueue instanceof PruneTask && ((PruneTask)taskFromQueue).myProject.equals(myProject)) return this;
      return null;
    }
  }

  /**
   * Hook to invalidate the cache (but deletes only after IDE restart).
   */
  public static class CacheInvalidator extends CachesInvalidator {
    @Override
    public void invalidateCaches() {
      ResourceFolderRepositoryFileCacheService.get().invalidate();
    }
  }

  /**
   * A startup task that manages ResourceFolderRepositoryFileCache.
   * - prunes unused module caches from the current project
   * - trims and limit caches to only cover a certain number of projects
   */
  public static class MaintenanceActivity implements StartupActivity.DumbAware {
    @Override
    public void runActivity(@NotNull Project project) {
      if (ApplicationManager.getApplication().isUnitTestMode()) return;

      // Prune directories within the current project.
      PruneTask pruneTask = new PruneTask(project);
      pruneTask.queue(project);

      // Prune stale projects, and manage LRU list (putting current project in front).
      ManageLruProjectFilesTask manageProjectsTask = new ManageLruProjectFilesTask(project);
      manageProjectsTask.queue(project);
    }
  }

  public static class PopulateCachesActivity implements StartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
      if (ApplicationManager.getApplication().isUnitTestMode()) return;

      // Pre-populate the in-memory resource folder registry for the project.
      ResourceFolderRegistry.PopulateCachesTask task = new ResourceFolderRegistry.PopulateCachesTask(project);
      task.queue(project);
    }
  }
}
