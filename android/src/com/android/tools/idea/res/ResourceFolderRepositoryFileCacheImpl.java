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
import com.google.common.collect.Sets;
import com.google.common.io.Closeables;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.ide.caches.CachesInvalidator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages a local file cache for ResourceFolderRepository state, for faster project reload.
 */
class ResourceFolderRepositoryFileCacheImpl implements ResourceFolderRepositoryFileCache {

  private static final String CACHE_DIRECTORY = "resource_folder_cache";
  private static final String INVALIDATE_CACHE_STAMP = "invalidate_caches_stamp.dat";

  static final int EXPECTED_CACHE_VERSION = 1;
  private static final String CACHE_VERSION_FILENAME = "cache_version";
  // The cache version previously read from the CACHE_VERSION_FILENAME (to avoid re-reading).
  private Integer myCacheVersion = null;

  private final File myRootDir;

  public ResourceFolderRepositoryFileCacheImpl() {
    myRootDir = new File(PathManager.getSystemPath(), CACHE_DIRECTORY);
  }

  public ResourceFolderRepositoryFileCacheImpl(File rootDirParent) {
    myRootDir = new File(rootDirParent, CACHE_DIRECTORY);
  }

  private static Logger getLogger() {
    return Logger.getInstance(ResourceFolderRepositoryFileCacheImpl.class);
  }

  @Override
  @Nullable
  public File getResourceDir(@NotNull Project project, @NotNull VirtualFile resourceDir) {
    // If directory is dirty, we cannot use the cache yet.
    if (!isValid()) {
      return null;
    }
    // Make up filename with hashCodes. Try to tolerate hash collisions:
    // The blob file contents will list the original resourceDir as data source, so if there there is a
    // hash collision we will detect during load and ignore the blob.
    File projectComponent = getProjectDir(project);
    if (projectComponent == null) {
      return null;
    }
    String dirComponent = FileUtil.sanitizeFileName(resourceDir.getParent().getName() + "_" +
                                                    Integer.toHexString(resourceDir.hashCode()));
    return new File(projectComponent, dirComponent);
  }

  @Override
  @Nullable
  public File getRootDir() {
    File cacheRootDir = myRootDir;
    if (!cacheRootDir.exists()) {
      // If this is the first time the root directory is created, stamp it with a version.
      if (!cacheRootDir.mkdirs()) {
        getLogger().error(String.format("Failed to create cache root directory %1$s", cacheRootDir));
        return null;
      }
      stampVersion(cacheRootDir, EXPECTED_CACHE_VERSION);
    }
    if (!cacheRootDir.isDirectory()) {
      getLogger().error(String.format("Cache root dir %1$s is not a directory", cacheRootDir));
      return null;
    }
    return cacheRootDir;
  }

  @Override
  @Nullable
  public File getProjectDir(@NotNull Project project) {
    File rootDir = getRootDir();
    if (rootDir == null) {
      return null;
    }
    String projectComponent = FileUtil.sanitizeFileName(project.getName() + "_" + project.getLocationHash());
    return new File(rootDir, projectComponent);
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
    String errMsg = "failed to write cache invalidating stamp file " + stamp;
    try {
      if (!stamp.createNewFile()) {
        getLogger().error(errMsg);
      }
    }
    catch (IOException e) {
      getLogger().error(errMsg, e);
    }
  }

  @Override
  public void delete() {
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
    myCacheVersion = null;
  }

  @Override
  public boolean isValid() {
    File rootDir = getRootDir();
    if (rootDir == null) {
      return false;
    }
    if (!isVersionSame(rootDir)) {
      return false;
    }
    File stamp = new File(rootDir, INVALIDATE_CACHE_STAMP);
    return !stamp.exists();
  }

  @VisibleForTesting
  boolean isVersionSame(@NotNull File rootDir) {
    if (myCacheVersion != null) {
      return myCacheVersion == EXPECTED_CACHE_VERSION;
    }
    File versionFile = new File(rootDir, CACHE_VERSION_FILENAME);
    if (!versionFile.exists()) {
      return false;
    }
    try {
      final DataInputStream in = new DataInputStream(new FileInputStream(versionFile));
      try {
        myCacheVersion = in.readInt();
      }
      finally {
        in.close();
      }
    }
    catch (FileNotFoundException e) {
      getLogger().error("Could not read cache version from file: " + versionFile, e);
      return false;
    }
    catch (IOException e) {
      getLogger().error("Could not read cache version from file: " + versionFile, e);
      return false;
    }
    return myCacheVersion == EXPECTED_CACHE_VERSION;
  }

  @Override
  @VisibleForTesting
  public void stampVersion(@NotNull File rootDir, int version) {
    File versionFile = new File(rootDir, CACHE_VERSION_FILENAME);
    try {
      FileUtil.ensureExists(rootDir);
      final DataOutputStream out = new DataOutputStream(new FileOutputStream(versionFile));
      try {
        out.writeInt(version);
        myCacheVersion = version;
      }
      finally {
        out.close();
      }
    }
    catch (FileNotFoundException e) {
      getLogger().error("Could not write cache version to file: " + versionFile, e);
    }
    catch (IOException e) {
      getLogger().error("Could not write cache version to file: " + versionFile, e);
    }
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

    public ManageLruProjectFilesTask(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void performInDumbMode(@NotNull ProgressIndicator indicator) {
      maintainLRUCache(MAX_PROJECT_CACHES);
    }

    @VisibleForTesting
    void maintainLRUCache(int maxProjectCaches) {
      assert maxProjectCaches > 0;
      ResourceFolderRepositoryFileCache cache = ResourceFolderRepositoryFileCacheService.get();
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
          List<File> projectsToRemove = updateLRUList(myProject, projectsList, maxProjectCaches);
          pruneOldProjects(cacheRootDir, projectsToRemove);
          writeListOfProjectCaches(cacheRootDir, projectsList);
        }
        catch (IOException e) {
          getLogger().error("Failed to maintain projects LRU cache for dir " + cacheRootDir, e);
        }
      }
    }

    @VisibleForTesting
    static List<File> loadListOfProjectCaches(@NotNull File cacheRootDir) throws IOException {
      File lruFile = new File(cacheRootDir, LRU_FILE);
      if (!lruFile.exists()) {
        return ContainerUtil.newArrayList();
      }
      FileInputStream fin = null;
      try {
        fin = new FileInputStream(lruFile);
        ObjectInputStream ois = new ObjectInputStream(fin);
        try {
          return (List<File>)ois.readObject();
        }
        catch (ClassNotFoundException e) {
          throw new IOException(e);
        }
        catch (ClassCastException e) {
          throw new IOException(e);
        }
        finally {
          Closeables.close(ois, false);
        }
      }
      finally {
        Closeables.close(fin, false);
      }
    }

    @VisibleForTesting
    static void writeListOfProjectCaches(@NotNull File cacheRootDir, List<File> projectsList) throws IOException {
      File lruFile = new File(cacheRootDir, LRU_FILE);
      FileOutputStream fos = null;
      try {
        fos = new FileOutputStream(lruFile);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        try {
          oos.writeObject(projectsList);
        }
        finally {
          Closeables.close(oos, false);
        }
      }
      finally {
        Closeables.close(fos, false);
      }
    }

    @VisibleForTesting
    static List<File> updateLRUList(Project currentProject, List<File> projectsList, int maxProjectCaches) {
      List<File> projectsToRemove = ContainerUtil.newArrayList();
      File currentProjectDir = ResourceFolderRepositoryFileCacheService.get().getProjectDir(currentProject);
      if (currentProjectDir == null) {
        return projectsToRemove;
      }
      projectsList.remove(currentProjectDir);
      projectsList.add(0, currentProjectDir);
      for (int i = projectsList.size() - 1; i >= maxProjectCaches && i >= 0; --i) {
        projectsToRemove.add(projectsList.remove(i));
      }
      return projectsToRemove;
    }

    private static void pruneOldProjects(@NotNull File cacheRootDir, List<File> projectsToRemove) {
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

  /**
   * Task that prunes unused resource directory caches within a given Project (which may come about
   * e.g., if one has moved a resource directory from one module to another).
   */
  static class PruneTask extends DumbModeTask {
    @NotNull private final Project myProject;

    public PruneTask(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void performInDumbMode(@NotNull ProgressIndicator indicator) {
      ResourceFolderRepositoryFileCache cache = ResourceFolderRepositoryFileCacheService.get();
      File projectCacheBase = cache.getProjectDir(myProject);
      if (projectCacheBase == null || !projectCacheBase.exists()) {
        return;
      }
      List<AndroidFacet> facets = ProjectFacetManager.getInstance(myProject).getFacets(AndroidFacet.ID);
      Map<VirtualFile, AndroidFacet> resDirectories = ResourceFolderRegistry.getResourceDirectoriesForFacets(facets);
      Set<File> usedCacheDirectories = Sets.newHashSet();
      for (VirtualFile resourceDir : resDirectories.keySet()) {
        File dir = cache.getResourceDir(myProject, resourceDir);
        ContainerUtil.addIfNotNull(usedCacheDirectories, dir);
      }
      File[] cacheFiles = projectCacheBase.listFiles();
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
