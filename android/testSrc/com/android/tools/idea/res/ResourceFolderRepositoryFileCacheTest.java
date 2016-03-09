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
import com.google.common.collect.Lists;
import com.intellij.mock.MockProgressIndicator;
import com.intellij.mock.MockProjectEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.MutablePicoContainer;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Tests the ResourceFolderRepositoryFileCache.
 */
public class ResourceFolderRepositoryFileCacheTest extends AndroidTestCase {

  private ResourceFolderRepositoryFileCache myOldFileCacheService;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ResourceFolderRepositoryFileCache cache = new ResourceFolderRepositoryFileCacheImpl(
      new File(myFixture.getTempDirPath()));
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

  private static ResourceFolderRepositoryFileCache overrideCacheService(ResourceFolderRepositoryFileCache newCache) {
    MutablePicoContainer applicationContainer = (MutablePicoContainer)
      ApplicationManager.getApplication().getPicoContainer();

    // Use a file cache that has per-test root directories instead of sharing the system directory.
    // Swap out cache services. We have to be careful. All tests share the same Application and PicoContainer.
    ResourceFolderRepositoryFileCache oldCache =
      (ResourceFolderRepositoryFileCache)applicationContainer.getComponentInstance(ResourceFolderRepositoryFileCache.class.getName());
    applicationContainer.unregisterComponent(ResourceFolderRepositoryFileCache.class.getName());
    applicationContainer.registerComponentInstance(ResourceFolderRepositoryFileCache.class.getName(), newCache);
    return oldCache;
  }

  private VirtualFile getResourceDir() {
    List<VirtualFile> resourceDirectories = myFacet.getAllResourceDirectories();
    assertNotNull(resourceDirectories);
    assertSize(1, resourceDirectories);
    return resourceDirectories.get(0);
  }

  public void testInvalidationBlocksDirectoryQuery() throws Exception {
    ResourceFolderRepositoryFileCache cache = ResourceFolderRepositoryFileCacheService.get();

    VirtualFile resDir = getResourceDir();
    File resCacheDir = cache.getResourceDir(getProject(), resDir);
    assertNotNull(resCacheDir);
    assertTrue(cache.isValid());

    // Now invalidate, and check blocked.
    cache.invalidate();
    assertFalse(cache.isValid());
    resCacheDir = cache.getResourceDir(getProject(), resDir);
    assertNull(resCacheDir);

    // Remove the invalidation stamp. We can use the cache again.
    cache.delete();
    resCacheDir = cache.getResourceDir(getProject(), resDir);
    assertNotNull(resCacheDir);
    assertTrue(cache.isValid());
  }

  public void testLRUListManagement() throws Exception {
    ResourceFolderRepositoryFileCache cache = ResourceFolderRepositoryFileCacheService.get();
    File rootDir = cache.getRootDir();
    assertNotNull(rootDir);

    List<File> projectCacheList = ManageLruProjectFilesTask.loadListOfProjectCaches(rootDir);
    assertEmpty(projectCacheList);

    // Try adding one.
    int lruLimit = 4;
    List<Pair<Project, File>> curProjectFiles = Lists.newArrayList();
    List<File> projectsToRemove = ManageLruProjectFilesTask.updateLRUList(getProject(), projectCacheList, lruLimit);
    assertEmpty(projectsToRemove);
    assertSize(1, projectCacheList);
    curProjectFiles.add(Pair.create(getProject(), cache.getProjectDir(getProject())));

    // Try serializing just the one.
    ManageLruProjectFilesTask.writeListOfProjectCaches(rootDir, projectCacheList);
    List<File> projectCacheList2 = ManageLruProjectFilesTask.loadListOfProjectCaches(rootDir);
    assertSameElements(projectCacheList2, projectCacheList);

    // Try adding some over the limit.
    int numOverLimit = 0;
    for (int i = 0; i < lruLimit + 1; ++i) {
      Project mockProject = new MockProjectWithName(getTestRootDisposable(), "p" + i);
      File mockProjectCacheDir = cache.getProjectDir(mockProject);
      assertNotNull(mockProjectCacheDir);
      curProjectFiles.add(Pair.create(mockProject, mockProjectCacheDir));
      projectsToRemove = ManageLruProjectFilesTask.updateLRUList(mockProject, projectCacheList, lruLimit);
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
    projectsToRemove = ManageLruProjectFilesTask.updateLRUList(curProjectFiles.get(numOverLimit + 1).first, projectCacheList, lruLimit);
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
    ResourceFolderRepositoryFileCache cache = ResourceFolderRepositoryFileCacheService.get();
    File curProjectCacheDir = cache.getProjectDir(getProject());
    assertNotNull(curProjectCacheDir);
    FileUtil.ensureExists(curProjectCacheDir);
    List<File> removed = ManageLruProjectFilesTask.updateLRUList(getProject(), projectDirList, lruLimit * 2);
    assertEmpty(removed);
    ++overLimit;
    ManageLruProjectFilesTask task = new ManageLruProjectFilesTask(getProject());
    task.maintainLRUCache(lruLimit);

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
    ResourceFolderRepositoryFileCache cache = ResourceFolderRepositoryFileCacheService.get();
    assertTrue(cache.isValid());
    ManageLruProjectFilesTask task = new ManageLruProjectFilesTask(getProject());
    task.maintainLRUCache(lruLimit);
    File curProjectCacheDir = cache.getProjectDir(getProject());
    assertNotNull(curProjectCacheDir);
    FileUtil.ensureExists(curProjectCacheDir);
    List<File> removed = ManageLruProjectFilesTask.updateLRUList(getProject(), projectDirList, lruLimit * 2);
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
    task.maintainLRUCache(lruLimit);
    for (int i = 0; i < lruLimit + overLimit; ++i) {
      assertFalse(projectDirList.get(i).exists());
    }
    assertTrue(cache.isValid());
  }

  private List<File> addProjectDirectories(int lruLimit, int overLimit) throws IOException {
    ResourceFolderRepositoryFileCache cache = ResourceFolderRepositoryFileCacheService.get();
    List<File> projectDirList = Lists.newArrayList();
    // Fill the cache directory with a bunch of mock project directories.
    for (int i = 0; i < lruLimit + overLimit; ++i) {
      Project mockProject = new MockProjectWithName(getTestRootDisposable(), "p" + i);
      File mockProjectCacheDir = cache.getProjectDir(mockProject);
      assertNotNull(mockProjectCacheDir);
      FileUtil.ensureExists(mockProjectCacheDir);
      assertTrue(mockProjectCacheDir.exists());
      List<File> removed = ManageLruProjectFilesTask.updateLRUList(mockProject, projectDirList, lruLimit * 2);
      assertEmpty(removed);
    }
    // Serialize it.
    File rootDir = cache.getRootDir();
    assertNotNull(rootDir);
    ManageLruProjectFilesTask.writeListOfProjectCaches(rootDir, projectDirList);
    return projectDirList;
  }

  public void testPruneResourceCachesInProject() throws IOException {
    ResourceFolderRepositoryFileCache cache = ResourceFolderRepositoryFileCacheService.get();
    VirtualFile resourceDir = getResourceDir();
    File resourceCacheDir = cache.getResourceDir(getProject(), resourceDir);
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

  public void testCacheVersionCheck() throws IOException {
    ResourceFolderRepositoryFileCacheImpl cache =
      (ResourceFolderRepositoryFileCacheImpl)ResourceFolderRepositoryFileCacheService.get();
    // Creating the root dir the first time gives us a stamp file.
    File rootDir = cache.getRootDir();
    assertNotNull(rootDir);
    assertTrue(cache.isVersionSame(rootDir));

    // If we delete the cache, it'll be empty without a stamp file.
    cache.delete();
    assertFalse(rootDir.exists());
    assertFalse(cache.isVersionSame(rootDir));

    // If we imagine a situation before the stamp file was introduced into the codebase
    // (root dir is made and it has content, but no stamp file) it is not valid.
    assertTrue(rootDir.mkdirs());
    List<File> files = addProjectDirectories(3, 0);
    for (File file : files) {
      assertTrue(file.exists());
    }
    assertFalse(cache.isVersionSame(rootDir));
    assertFalse(cache.isValid());

    // Now try setting up the correct version and a few directories (stamping happens automatically).
    cache.delete();
    files = addProjectDirectories(3, 0);
    for (File file : files) {
      assertTrue(file.exists());
    }
    assertTrue(cache.isVersionSame(rootDir));
    assertTrue(cache.isValid());

    // Now try overwriting version stamp with a too old version.
    cache.stampVersion(rootDir, ResourceFolderRepositoryFileCacheImpl.EXPECTED_CACHE_VERSION - 1);
    assertFalse(cache.isVersionSame(rootDir));
    assertFalse(cache.isValid());
    // The management task should clean up old files if the version is invalid.
    ManageLruProjectFilesTask task = new ManageLruProjectFilesTask(getProject());
    task.performInDumbMode(new MockProgressIndicator());
    assertFalse(rootDir.exists());
    for (File file : files) {
      assertFalse(file.exists());
    }
    assertFalse(cache.isVersionSame(rootDir));
    // However, once anything grabs the root dir again, it is stamped and valid again.
    rootDir = cache.getRootDir();
    assertNotNull(rootDir);
    assertTrue(cache.isVersionSame(rootDir));
    assertTrue(cache.isValid());
  }

  /**
   * Mock project that will use different directory names (vs the standard Mock, which has an empty name).
   */
  private static class MockProjectWithName extends MockProjectEx {
    private final String myName;

    public MockProjectWithName(@NotNull Disposable parentDisposable, String name) {
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
