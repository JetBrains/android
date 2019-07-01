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

import com.android.tools.idea.res.ResourceFolderRepositoryFileCacheImpl.CacheInvalidator;
import com.android.tools.idea.res.ResourceFolderRepositoryFileCacheImpl.ManageLruProjectFilesTask;
import com.android.tools.idea.res.ResourceFolderRepositoryFileCacheImpl.PruneTask;
import com.intellij.mock.MockProgressIndicator;
import com.intellij.mock.MockProjectEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.MutablePicoContainer;

/**
 * Tests for {@link ResourceFolderRepositoryFileCacheImpl}.
 */
public class ResourceFolderRepositoryFileCacheTest extends AndroidTestCase {
  private ResourceFolderRepositoryFileCacheImpl myOldFileCacheService;

  @NotNull
  private static ResourceFolderRepositoryFileCacheImpl getCache() {
    return (ResourceFolderRepositoryFileCacheImpl)ResourceFolderRepositoryFileCacheService.get();
  }

  @NotNull
  private static ResourceFolderRepositoryFileCacheImpl overrideCacheService(ResourceFolderRepositoryFileCacheImpl newCache) {
    MutablePicoContainer applicationContainer = (MutablePicoContainer)ApplicationManager.getApplication().getPicoContainer();

    // Use a file cache that has per-test root directories instead of sharing the system directory.
    // Swap out cache services. We have to be careful. All tests share the same Application and PicoContainer.
    ResourceFolderRepositoryFileCacheImpl oldCache =
        (ResourceFolderRepositoryFileCacheImpl)applicationContainer.getComponentInstance(ResourceFolderRepositoryFileCache.class.getName());
    applicationContainer.unregisterComponent(ResourceFolderRepositoryFileCache.class.getName());
    applicationContainer.registerComponentInstance(ResourceFolderRepositoryFileCache.class.getName(), newCache);
    return oldCache;
  }

  @NotNull
  private VirtualFile getResourceDir() {
    List<VirtualFile> resourceDirectories = ResourceFolderManager.getInstance(myFacet).getFolders();
    assertNotNull(resourceDirectories);
    assertSize(1, resourceDirectories);
    return resourceDirectories.get(0);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ResourceFolderRepositoryFileCacheImpl cache = new ResourceFolderRepositoryFileCacheImpl(new File(myFixture.getTempDirPath()));
    myOldFileCacheService = overrideCacheService(cache);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      overrideCacheService(myOldFileCacheService);
    }
    finally {
      super.tearDown();
    }
  }

  public void testInvalidationBlocksDirectoryQuery() {
    ResourceFolderRepositoryFileCacheImpl cache = getCache();

    VirtualFile resDir = getResourceDir();
    Path resCacheDir = cache.getCachingData(getProject(), resDir, null).getCacheFile();
    assertNotNull(resCacheDir);
    assertTrue(cache.isValid());

    // Now invalidate, and check blocked.
    cache.invalidate();
    assertFalse(cache.isValid());
    ResourceFolderRepositoryCachingData cachingData = cache.getCachingData(getProject(), resDir, null);
    assertNull(cachingData);

    // Remove the invalidation stamp. We can use the cache again.
    cache.delete();
    resCacheDir = cache.getCachingData(getProject(), resDir, null).getCacheFile();
    assertNotNull(resCacheDir);
    assertTrue(cache.isValid());
  }

  public void testLRUListManagement() throws Exception {
    ResourceFolderRepositoryFileCacheImpl cache = getCache();
    File rootDir = cache.getRootDir();
    assertNotNull(rootDir);

    List<File> projectCacheList = ManageLruProjectFilesTask.loadListOfProjectCaches(rootDir);
    assertEmpty(projectCacheList);

    // Try adding one.
    int lruLimit = 4;
    List<Pair<Project, File>> curProjectFiles = new ArrayList<>();
    List<File> projectsToRemove = ManageLruProjectFilesTask.updateLruList(getProject(), projectCacheList, lruLimit);
    assertEmpty(projectsToRemove);
    assertSize(1, projectCacheList);
    curProjectFiles.add(Pair.create(getProject(), cache.getProjectDir(getProject()).toFile()));

    // Try serializing just the one.
    ManageLruProjectFilesTask.writeListOfProjectCaches(rootDir, projectCacheList);
    List<File> projectCacheList2 = ManageLruProjectFilesTask.loadListOfProjectCaches(rootDir);
    assertSameElements(projectCacheList2, projectCacheList);

    // Try adding some over the limit.
    int numOverLimit = 0;
    for (int i = 0; i < lruLimit + 1; ++i) {
      Project mockProject = new MockProjectWithName(getProject(), "p" + i);
      Path mockProjectCacheDir = cache.getProjectDir(mockProject);
      assertNotNull(mockProjectCacheDir);
      curProjectFiles.add(Pair.create(mockProject, mockProjectCacheDir.toFile()));
      projectsToRemove = ManageLruProjectFilesTask.updateLruList(mockProject, projectCacheList, lruLimit);
      if (curProjectFiles.size() > lruLimit) {
        assertSize(1, projectsToRemove);
        assertContainsElements(projectsToRemove, curProjectFiles.get(numOverLimit).second);
        assertSize(lruLimit, projectCacheList);
        ++numOverLimit;
      }
      else {
        assertEmpty(projectsToRemove);
        assertSize(curProjectFiles.size(), projectCacheList);
      }
    }

    // Try pushing one of the elements still on the list to the front.
    projectsToRemove = ManageLruProjectFilesTask.updateLruList(curProjectFiles.get(numOverLimit + 1).first, projectCacheList, lruLimit);
    assertEmpty(projectsToRemove);
    assertEquals(projectCacheList.get(0), curProjectFiles.get(numOverLimit + 1).second);

    // Serialize and deserialize again.
    ManageLruProjectFilesTask.writeListOfProjectCaches(rootDir, projectCacheList);
    List<File> projectCacheList3 = ManageLruProjectFilesTask.loadListOfProjectCaches(rootDir);
    assertSameElements(projectCacheList3, projectCacheList);
  }

  public void testManageLRUProjectFilesTrims() throws IOException {
    int lruLimit = 3;
    int overLimit = 1;
    List<File> projectDirList = addProjectDirectories(lruLimit, overLimit);

    // Now add current project to front, sweep, and check.
    ResourceFolderRepositoryFileCacheImpl cache = getCache();
    Path curProjectCacheDir = cache.getProjectDir(getProject());
    assertNotNull(curProjectCacheDir);
    FileUtil.ensureExists(curProjectCacheDir.toFile());
    List<File> removed = ManageLruProjectFilesTask.updateLruList(getProject(), projectDirList, lruLimit * 2);
    assertEmpty(removed);
    ++overLimit;
    ManageLruProjectFilesTask task = new ManageLruProjectFilesTask(getProject());
    task.maintainLruCache(lruLimit);

    for (int i = 0; i < lruLimit + overLimit; ++i) {
      if (i < lruLimit) {
        assertTrue(projectDirList.get(i).exists());
      }
      else {
        assertFalse(projectDirList.get(i).exists());
      }
    }
  }

  public void testManagerLRUProjectFilesInvalidationClears() throws IOException {
    int lruLimit = 3;
    int overLimit = 1;
    List<File> projectDirList = addProjectDirectories(lruLimit, overLimit);

    // Check that the expected directories exist before invalidation.
    ResourceFolderRepositoryFileCacheImpl cache = getCache();
    assertTrue(cache.isValid());
    ManageLruProjectFilesTask task = new ManageLruProjectFilesTask(getProject());
    task.maintainLruCache(lruLimit);
    Path curProjectCacheDir = cache.getProjectDir(getProject());
    assertNotNull(curProjectCacheDir);
    FileUtil.ensureExists(curProjectCacheDir.toFile());
    List<File> removed = ManageLruProjectFilesTask.updateLruList(getProject(), projectDirList, lruLimit * 2);
    assertEmpty(removed);
    ++overLimit;
    for (int i = 0; i < lruLimit + overLimit; ++i) {
      if (i < lruLimit) {
        assertTrue(projectDirList.get(i).exists());
      }
      else {
        assertFalse(projectDirList.get(i).exists());
      }
    }

    // Now invalidate and check that everything is deleted, including the stamp.
    CacheInvalidator invalidator = new CacheInvalidator();
    invalidator.invalidateCaches();
    assertFalse(cache.isValid());
    task.maintainLruCache(lruLimit);
    for (int i = 0; i < lruLimit + overLimit; ++i) {
      assertFalse(projectDirList.get(i).exists());
    }
    assertTrue(cache.isValid());
  }

  private List<File> addProjectDirectories(int lruLimit, int overLimit) throws IOException {
    ResourceFolderRepositoryFileCacheImpl cache = getCache();
    List<File> projectDirList = new ArrayList<>();
    // Fill the cache directory with a bunch of mock project directories.
    for (int i = 0; i < lruLimit + overLimit; ++i) {
      Project mockProject = new MockProjectWithName(getProject(), "p" + i);
      Path mockProjectCacheDir = cache.getProjectDir(mockProject);
      assertNotNull(mockProjectCacheDir);
      FileUtil.ensureExists(mockProjectCacheDir.toFile());
      assertTrue(Files.exists(mockProjectCacheDir));
      List<File> removed = ManageLruProjectFilesTask.updateLruList(mockProject, projectDirList, lruLimit * 2);
      assertEmpty(removed);
    }
    // Serialize it.
    File rootDir = cache.getRootDir();
    assertNotNull(rootDir);
    ManageLruProjectFilesTask.writeListOfProjectCaches(rootDir, projectDirList);
    return projectDirList;
  }

  public void testPruneResourceCachesInProject() throws IOException {
    ResourceFolderRepositoryFileCacheImpl cache = getCache();
    VirtualFile resourceDir = getResourceDir();
    File resourceCacheDir = cache.getCachingData(getProject(), resourceDir, null).getCacheFile().toFile();
    assertNotNull(resourceCacheDir);
    FileUtil.ensureExists(resourceCacheDir);
    // Add a dummy directories alongside the real cache directory.
    File dummyDirectory = new File(resourceCacheDir.getParent(), "dummyResDirCache");
    FileUtil.ensureExists(dummyDirectory);
    assertTrue(dummyDirectory.exists());

    // Now prune.
    PruneTask pruneTask = new PruneTask(getProject());
    pruneTask.performInDumbMode(new MockProgressIndicator());
    assertFalse(dummyDirectory.exists());
    assertTrue(resourceCacheDir.exists());
  }

  /**
   * Mock project that will use different directory names (vs the standard Mock, which has an empty name).
   */
  private static class MockProjectWithName extends MockProjectEx {
    private final String myName;

    MockProjectWithName(@NotNull Disposable parentDisposable, @NotNull String name) {
      super(parentDisposable);
      myName = name;
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }
  }
}
