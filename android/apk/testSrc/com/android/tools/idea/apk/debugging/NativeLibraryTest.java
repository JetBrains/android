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
package com.android.tools.idea.apk.debugging;

import static com.android.sdklib.devices.Abi.ARM64_V8A;
import static com.android.sdklib.devices.Abi.ARMEABI;
import static com.android.sdklib.devices.Abi.X86;
import static com.android.tools.idea.apk.debugging.SharedObjectFiles.createSharedObjectFiles;
import static com.android.tools.idea.testing.ProjectFiles.createFile;
import static com.android.tools.idea.testing.ProjectFiles.createFolderInProjectRoot;
import static com.android.utils.FileUtils.toSystemIndependentPath;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static org.mockito.Mockito.mock;

import com.android.sdklib.devices.Abi;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link NativeLibrary}.
 */
public class NativeLibraryTest extends HeavyPlatformTestCase {
  private NativeLibrary myLibrary;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myLibrary = new NativeLibrary("library.so");
  }

  public void testSetFilePaths() throws IOException {
    Collection<VirtualFile> files = doCreateSharedObjectFiles(X86, ARM64_V8A);
    List<String> filePaths = getPaths(files);

    myLibrary.setSharedObjectFilePaths(filePaths);
    assertThat(myLibrary.abis).containsExactly(X86, ARM64_V8A);
    assertEquals(ARM64_V8A, myLibrary.abis.get(0)); // Should be sorted.
    assertThat(myLibrary.getSharedObjectFiles()).containsAllIn(files);
    assertThat(myLibrary.getSharedObjectFilePaths()).containsAllIn(filePaths);
  }

  public void testSetFilePathsWithNonExistingPaths() {
    List<String> filePaths = new ArrayList<>();
    filePaths.add("abc.so");

    myLibrary.setSharedObjectFilePaths(filePaths);
    assertThat(myLibrary.abis).isEmpty();
    assertThat(myLibrary.sharedObjectFilesByAbi).isEmpty();
  }

  public void testSetFilePathsAgain() throws IOException {
    Collection<VirtualFile> files = doCreateSharedObjectFiles(X86, ARM64_V8A);
    List<String> filePaths = getPaths(files);

    myLibrary.setSharedObjectFilePaths(filePaths);
    assertThat(myLibrary.abis).containsExactly(X86, ARM64_V8A);
    assertEquals(ARM64_V8A, myLibrary.abis.get(0)); // Should be sorted.
    assertThat(myLibrary.getSharedObjectFiles()).containsAllIn(files);
    assertThat(myLibrary.getSharedObjectFilePaths()).containsAllIn(filePaths);


    // Set it again and verify that nothing is accumulated from previous call.
    myLibrary.setSharedObjectFilePaths(filePaths);
    assertThat(myLibrary.abis).containsExactly(X86, ARM64_V8A);
    assertEquals(ARM64_V8A, myLibrary.abis.get(0)); // Should be sorted.
    assertThat(myLibrary.getSharedObjectFiles()).containsAllIn(files);
    assertThat(myLibrary.getSharedObjectFilePaths()).containsAllIn(filePaths);

    // Set it again, this time to non-existing paths to verify nothing is carried over from previous call.
    filePaths.clear();
    filePaths.add("abc.so");
    myLibrary.setSharedObjectFilePaths(filePaths);
    assertThat(myLibrary.abis).isEmpty();
    assertThat(myLibrary.sharedObjectFilesByAbi).isEmpty();
  }

  public void testAddFilesWithFileList() throws IOException {
    Collection<VirtualFile> files = doCreateSharedObjectFiles(X86, ARM64_V8A);
    myLibrary.addSharedObjectFiles(files);

    assertThat(myLibrary.abis).containsExactly(X86, ARM64_V8A);
    assertEquals(ARM64_V8A, myLibrary.abis.get(0)); // Should be sorted.
    assertThat(myLibrary.getSharedObjectFiles()).containsAllIn(files);
    assertThat(myLibrary.getSharedObjectFilePaths()).containsAllIn(getPaths(files));
  }

  public void testAddFilesWithFileArray() throws IOException {
    Collection<VirtualFile> files = doCreateSharedObjectFiles(X86, ARM64_V8A);
    myLibrary.addSharedObjectFiles(files);

    assertThat(myLibrary.abis).containsExactly(X86, ARM64_V8A);
    assertEquals(ARM64_V8A, myLibrary.abis.get(0)); // Should be sorted.
    assertThat(myLibrary.getSharedObjectFiles()).containsAllIn(files);
    assertThat(myLibrary.getSharedObjectFilePaths()).containsAllIn(getPaths(files));
  }

  @NotNull
  private List<VirtualFile> doCreateSharedObjectFiles(@NotNull Abi... abis) throws IOException {
    return createSharedObjectFiles(PlatformTestUtil.getOrCreateProjectBaseDir(myProject), myLibrary.name, abis);
  }

  @NotNull
  private static List<String> getPaths(@NotNull Collection<VirtualFile> files) {
    return ContainerUtil.map(files, VirtualFile::getPath);
  }

  public void testIsMissingPathMappingsWithLocalPaths() {
    assertFalse(myLibrary.isMissingPathMappings()); // The map is empty
  }

  public void testAddPathMapping() {
    myLibrary.sourceFolderPaths = new ArrayList<>();
    myLibrary.addPathMapping("abc.so", "xyz.so");
    verifySourceFolderPathCacheWasCleared();
    assertEquals("xyz.so", myLibrary.pathMappings.get("abc.so"));
  }

  public void testIsMissingPathMappingsWithNonEmptyMappings() {
    myLibrary.addPathMapping("abc.so", "abc.so");
    assertFalse(myLibrary.isMissingPathMappings()); // The all values in the map are not empty
  }

  public void testIsMissingPathMappingsWithEmptyMappings() {
    myLibrary.addPathMapping("abc.so", "");
    assertTrue(myLibrary.isMissingPathMappings());
  }

  public void testAddDebuggableSharedObjectFile() throws IOException {
    myLibrary.sourceFolderPaths = new ArrayList<>();
    myLibrary.hasDebugSymbols = false;
    myLibrary.addPathMapping("abc.so", "");

    VirtualFile debuggableFile = ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<VirtualFile, IOException>() {
      @Override
      public VirtualFile compute() throws IOException {
        return PlatformTestUtil.getOrCreateProjectBaseDir(getProject()).createChildData(this, "debuggable.so");
      }
    });

    myLibrary.addDebuggableSharedObjectFile(X86, debuggableFile);
    assertTrue(myLibrary.hasDebugSymbols);
    verifySourceFolderPathCacheWasCleared();

    DebuggableSharedObjectFile stored = myLibrary.debuggableSharedObjectFilesByAbi.get(X86);
    assertEquals(debuggableFile.getPath(), toSystemIndependentPath(stored.path));
  }

  public void testGetUserSelectedPathsInMappings() {
    assertThat(myLibrary.getUserSelectedPathsInMappings()).isEmpty();

    myLibrary.addPathMapping("abc.so", "");
    assertThat(myLibrary.getUserSelectedPathsInMappings()).isEmpty();

    myLibrary.addPathMapping("xyz.so", "123.so");
    assertThat(myLibrary.getUserSelectedPathsInMappings()).containsExactly("123.so");
  }

  public void testClearDebugSymbols() {
    myLibrary.sourceFolderPaths = new ArrayList<>();
    myLibrary.debuggableSharedObjectFilesByAbi.put(ARMEABI, mock(DebuggableSharedObjectFile.class));
    myLibrary.pathMappings.put("abc.so", "xyz.so");
    myLibrary.hasDebugSymbols = true;

    myLibrary.clearDebugSymbols();

    verifySourceFolderPathCacheWasCleared();
    assertThat(myLibrary.debuggableSharedObjectFilesByAbi).isEmpty();
    assertThat(myLibrary.pathMappings).isEmpty();
    assertFalse(myLibrary.hasDebugSymbols);
  }

  public void testReplacePathMappingsWith() {
    myLibrary.sourceFolderPaths = new ArrayList<>();
    myLibrary.pathMappings.put("1.so", "9.so");
    myLibrary.pathMappings.put("2.so", "8.so");

    Map<String, String> newPathMappings = new HashMap<>();
    newPathMappings.put("3.so", "7.so");
    newPathMappings.put("4.so", "6.so");

    myLibrary.replacePathMappingsWith(newPathMappings);
    assertEquals(newPathMappings, myLibrary.pathMappings);
    verifySourceFolderPathCacheWasCleared();
  }

  public void testRemovePathMapping() {
    myLibrary.sourceFolderPaths = new ArrayList<>();
    myLibrary.pathMappings.put("abc.so", "xyz.so");
    myLibrary.pathMappings.put("def.so", "vuw.so");

    myLibrary.removePathMapping("def.so");
    assertThat(myLibrary.pathMappings).hasSize(1);
    assertEquals("xyz.so", myLibrary.pathMappings.get("abc.so"));
    verifySourceFolderPathCacheWasCleared();
  }

  private void verifySourceFolderPathCacheWasCleared() {
    assertNull(myLibrary.sourceFolderPaths);
  }

  public void testGetSourceFolderPaths() throws IOException {
    myLibrary.sourceFolderPaths = null; // Make sure the cache has not been created yet.

    Project project = getProject();
    VirtualFile libFolder = createFolderInProjectRoot(project, "lib");

    // Local paths.
    String javaFolderPath = createFolderAndGetPath("java");
    String cppFolderPath = createFolderAndGetPath("cpp");
    String locallyMappedPath = createFolderAndGetPath("localMappedPath");
    String locallyMappedPath2 = createFolderAndGetPath("localMappedPath2");

    // lib1.so: An .so file with absolute local paths.
    {
      DebuggableSharedObjectFile soFile1 = new DebuggableSharedObjectFile(createFile(libFolder, "lib1.so"));
      myLibrary.debuggableSharedObjectFilesByAbi.put(X86, soFile1);

      // Absolute, locally-existing paths.
      soFile1.debugSymbolPaths.add(javaFolderPath);
      soFile1.debugSymbolPaths.add(cppFolderPath);
    }

    // lib2.so: An .so file with relative paths that are not mapped.
    {
      DebuggableSharedObjectFile soFile2 = new DebuggableSharedObjectFile(createFile(libFolder, "lib2.so"));
      myLibrary.debuggableSharedObjectFilesByAbi.put(ARMEABI, soFile2);

      // Relative paths. Unmapped.
      soFile2.debugSymbolPaths.add("remotePath1");
      soFile2.debugSymbolPaths.add("./remotePath2");
      soFile2.debugSymbolPaths.add("../remotePath3");
      soFile2.debugSymbolPaths.add("../../remotePath4");
    }

    // lib3.so: An .so file with an absolute path that does not exist in the local system, and is not mapped.
    {
      DebuggableSharedObjectFile soFile3 = new DebuggableSharedObjectFile(createFile(libFolder, "lib3.so"));
      myLibrary.debuggableSharedObjectFilesByAbi.put(ARMEABI, soFile3);

      // Absolute, non-existing path. Unmapped.
      soFile3.debugSymbolPaths.add("/a/non-existing/folder/on/local/drive/remotePath");
    }

    // lib4.so: An .so file with relative and absolute paths that are mapped.
    {
      DebuggableSharedObjectFile soFile4 = new DebuggableSharedObjectFile(createFile(libFolder, "lib4.so"));
      myLibrary.debuggableSharedObjectFilesByAbi.put(ARMEABI, soFile4);

      // Relative path. Mapped.
      String remotePath = "../relativeRemotePath";
      soFile4.debugSymbolPaths.add(remotePath);
      myLibrary.pathMappings.put(remotePath, locallyMappedPath);

      // Absolute, non-existing path. Mapped.
      String remotePath2 = "/another/non-existing/folder/on/local/drive/remotePath";
      soFile4.debugSymbolPaths.add(remotePath2);
      myLibrary.pathMappings.put(remotePath2, locallyMappedPath2);
    }

    // Recalculate the source folder paths.
    List<String> sourceFolderPaths = myLibrary.getSourceFolderPaths();
    assertThat(sourceFolderPaths).containsExactly(javaFolderPath, cppFolderPath, locallyMappedPath, locallyMappedPath2);

    // Verify we return the same instance once calculated.
    assertSame(sourceFolderPaths, myLibrary.getSourceFolderPaths());
  }

  @NotNull
  private String createFolderAndGetPath(@NotNull String folderName) throws IOException {
    return toSystemDependentName(createFolderInProjectRoot(getProject(), folderName).getPath());
  }
}