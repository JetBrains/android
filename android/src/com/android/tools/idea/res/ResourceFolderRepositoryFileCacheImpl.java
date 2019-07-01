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

import static com.android.tools.idea.res.AndroidPluginVersion.getAndroidPluginVersion;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.ide.caches.CachesInvalidator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages a local file cache for ResourceFolderRepository state, for faster project reload.
 */
class ResourceFolderRepositoryFileCacheImpl implements ResourceFolderRepositoryFileCache {
  private static final String CACHE_DIRECTORY = "caches/project_resources";
  private static final String INVALIDATE_CACHE_STAMP = "invalidate_caches_stamp.dat";

  @NotNull private final File myRootDir;

  ResourceFolderRepositoryFileCacheImpl() {
    myRootDir = new File(PathManager.getSystemPath(), CACHE_DIRECTORY);
  }

  ResourceFolderRepositoryFileCacheImpl(@NotNull File rootDirParent) {
    myRootDir = new File(rootDirParent, CACHE_DIRECTORY);
  }

  private static Logger getLogger() {
    return Logger.getInstance(ResourceFolderRepositoryFileCacheImpl.class);
  }

  @Override
  @Nullable
  public ResourceFolderRepositoryCachingData getCachingData(@NotNull Project project,
                                                            @NotNull VirtualFile resourceDir,
                                                            @Nullable Executor cacheCreationExecutor) {
    String codeVersion = getAndroidPluginVersion();
    if (codeVersion == null) {
      return null;
    }

    File cacheFile = getCacheFile(project, resourceDir);
    if (cacheFile == null) {
      return null;
    }
    return new ResourceFolderRepositoryCachingData(cacheFile.toPath(), isValid(), codeVersion, cacheCreationExecutor);
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
  private File getCacheFile(@NotNull Project project, @NotNull VirtualFile resourceDir) {
    // If directory is dirty, we cannot use the cache yet.
    if (!isValid()) {
      return null;
    }
    // Make up filename with hashCodes. Try to tolerate hash collisions:
    // The cache file contents will list the original resourceDir as data source, so a potential
    // hash collision will be detected during loading and the cache file will be ignored.
    Path projectComponent = getProjectDir(project);
    if (projectComponent == null) {
      return null;
    }
    String dirComponent = FileUtil.sanitizeFileName(resourceDir.getParent().getName() + "_" +
                                                    Integer.toHexString(resourceDir.hashCode()));
    return projectComponent.resolve(dirComponent).toFile();
  }

  @Nullable
  @VisibleForTesting
  File getRootDir() {
    File cacheRootDir = myRootDir;
    if (!cacheRootDir.exists()) {
      // If this is the first time the root directory is created, stamp it with a version.
      if (!cacheRootDir.mkdirs()) {
        getLogger().error(String.format("Failed to create cache root directory %1$s", cacheRootDir));
        return null;
      }
    }
    if (!cacheRootDir.isDirectory()) {
      getLogger().error(String.format("Cache root dir %1$s is not a directory", cacheRootDir));
      return null;
    }
    return cacheRootDir;
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
    File rootDir = getRootDir();
    if (rootDir == null) {
      return null;
    }
    return ProjectUtil.getProjectCachePath(project, rootDir.toPath());
  }

  @Override
  public void invalidate() {
    if (!isValid()) {
      return;
    }
    File rootDir = getRootDir();
    if (rootDir == null) {
      return;
    }
    File stamp = new File(rootDir, INVALIDATE_CACHE_STAMP);
    String errMsg = "Failed to write cache invalidating stamp file " + stamp;
    try {
      if (!stamp.createNewFile()) {
        getLogger().error(errMsg);
      }
    }
    catch (IOException e) {
      getLogger().error(errMsg, e);
    }
  }

  /**
   * Deletes the cache from disk, clearing the invalidation stamp.
   */
  @VisibleForTesting
  void delete() {
    File cacheRootDir = getRootDir();
    if (cacheRootDir == null) {
      return;
    }
    // First delete all the subdirectories except for the stamp.
    File[] subCaches = cacheRootDir.listFiles();
    if (subCaches == null) {
      return;
    }
    File stamp = new File(cacheRootDir, INVALIDATE_CACHE_STAMP);
    for (File subCache : subCaches) {
      if (!FileUtil.filesEqual(stamp, subCache)) {
        FileUtil.delete(subCache);
      }
    }

    // Finally, delete the stamp and the directory.
    FileUtil.delete(cacheRootDir);
  }

  /**
   * Checks if the cache is valid (not invalidated).
   */
  @VisibleForTesting
  boolean isValid() {
    File rootDir = getRootDir();
    if (rootDir == null) {
      return false;
    }
    File stamp = new File(rootDir, INVALIDATE_CACHE_STAMP);
    return !stamp.exists();
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
    private static final String LRU_FILE = "project_lru_list.dat";

    private static final int MAX_PROJECT_CACHES = 12;

    ManageLruProjectFilesTask(@NotNull Project project) {
      myProject = project;
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
      File cacheRootDir = cache.getRootDir();
      if (cacheRootDir == null) {
        return;
      }
      // If invalid, clear the whole cache directory (including any invalidation stamps).
      if (!cache.isValid()) {
        cache.delete();
        return;
      }

      synchronized (PROJECT_LRU_LOCK) {
        try {
          List<File> projectsList = loadListOfProjectCaches(cacheRootDir);
          List<File> projectsToRemove = updateLruList(myProject, projectsList, maxProjectCaches);
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
    static List<File> loadListOfProjectCaches(@NotNull File cacheRootDir) throws IOException {
      File lruFile = new File(cacheRootDir, LRU_FILE);
      try (ObjectInputStream stream = new ObjectInputStream(new FileInputStream(lruFile))) {
        return (List<File>)stream.readObject();
      }
      catch (FileNotFoundException e) {
        return new ArrayList<>();
      }
      catch (ClassNotFoundException e) {
        throw new IOException(e);
      }
      catch (ClassCastException e) {
        throw new IOException(e);
      }
    }

    @VisibleForTesting
    static void writeListOfProjectCaches(@NotNull File cacheRootDir, List<File> projectsList) throws IOException {
      File lruFile = new File(cacheRootDir, LRU_FILE);
      try (ObjectOutputStream stream = new ObjectOutputStream(new FileOutputStream(lruFile))) {
        stream.writeObject(projectsList);
      }
    }

    @VisibleForTesting
    static List<File> updateLruList(@NotNull Project currentProject, @NotNull List<File> projectsList, int maxProjectCaches) {
      List<File> projectsToRemove = new ArrayList<>();
      Path currentProjectPath = getCache().getProjectDir(currentProject);
      if (currentProjectPath == null) {
        return projectsToRemove;
      }

      File currentProjectDir = currentProjectPath.toFile();
      projectsList.remove(currentProjectDir);
      projectsList.add(0, currentProjectDir);
      for (int i = projectsList.size() - 1; i >= maxProjectCaches && i >= 0; --i) {
        projectsToRemove.add(projectsList.remove(i));
      }
      return projectsToRemove;
    }

    private static void pruneOldProjects(@NotNull File cacheRootDir, @NotNull List<File> projectsToRemove) {
      // Only attempt to remove directories that are a sub dir of the root.
      File[] subCaches = cacheRootDir.listFiles();
      if (subCaches == null) {
        getLogger().error(String.format("Cache root %1$s is not a directory", cacheRootDir));
        return;
      }
      List<File> subCacheList = Arrays.asList(subCaches);
      for (File projectDir : projectsToRemove) {
        if (!subCacheList.contains(projectDir)) {
          continue;
        }
        if (!FileUtil.delete(projectDir)) {
          getLogger().error(String.format("Failed to prune dir %1$s", projectDir));
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
      Map<VirtualFile, AndroidFacet> resDirectories = AndroidResourceUtil.getResourceDirectoriesForFacets(facets);
      Set<File> usedCacheDirectories = new HashSet<>();
      for (VirtualFile resourceDir : resDirectories.keySet()) {
        File dir = cache.getCacheFile(myProject, resourceDir);
        ContainerUtil.addIfNotNull(usedCacheDirectories, dir);
      }
      File[] cacheFiles = projectCacheBase.toFile().listFiles();
      if (cacheFiles == null) {
        getLogger().error("Failed to prune cache files from " + projectCacheBase);
        return;
      }
      for (File child : cacheFiles) {
        if (!usedCacheDirectories.contains(child)) {
          if (!FileUtil.delete(child)) {
            getLogger().error("Failed to prune child " + child);
          }
        }
      }
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
  public static class MaintenanceActivity implements StartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
      if (ApplicationManager.getApplication().isUnitTestMode()) return;

      DumbService dumbService = DumbService.getInstance(project);

      // Prune directories within the current project.
      PruneTask pruneTask = new PruneTask(project);
      dumbService.queueTask(pruneTask);

      // Prune stale projects, and manage LRU list (putting current project in front).
      ManageLruProjectFilesTask manageProjectsTask = new ManageLruProjectFilesTask(project);
      dumbService.queueTask(manageProjectsTask);
    }
  }

  public static class PopulateCachesActivity implements StartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
      if (ApplicationManager.getApplication().isUnitTestMode()) return;

      DumbService dumbService = DumbService.getInstance(project);

      // Pre-populate the in-memory resource folder registry for the project.
      ResourceFolderRegistry.PopulateCachesTask task = new ResourceFolderRegistry.PopulateCachesTask(project);
      dumbService.queueTask(task);
    }
  }
}
