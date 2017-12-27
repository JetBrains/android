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

import com.android.sdklib.devices.Abi;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.android.sdklib.devices.Abi.*;
import static com.android.tools.idea.apk.debugging.SharedObjectFiles.createSharedObjectFiles;
import static com.android.tools.idea.testing.ProjectFiles.createFile;
import static com.android.tools.idea.testing.ProjectFiles.createFolderInProjectRoot;
import static com.android.utils.FileUtils.toSystemIndependentPath;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link NativeLibrary}.
 */
public class NativeLibraryTest extends IdeaTestCase {
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
    return createSharedObjectFiles(myProject.getBaseDir(), myLibrary.name, abis);
  }

  @NotNull
  private static List<String> getPaths(@NotNull Collection<VirtualFile> files) {
    return files.stream().map(VirtualFile::getPath).collect(Collectors.toList());
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
        return getProject().getBaseDir().createChildData(this, "debuggable.so");
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

    DebuggableSharedObjectFile soFile1 = new DebuggableSharedObjectFile(createFile(libFolder, "lib1.so"));
    myLibrary.debuggableSharedObjectFilesByAbi.put(X86, soFile1);

    DebuggableSharedObjectFile soFile2 = new DebuggableSharedObjectFile(createFile(libFolder, "lib2.so"));
    myLibrary.debuggableSharedObjectFilesByAbi.put(ARMEABI, soFile2);

    String javaFolderPath = createFolderAndGetPath("java");
    soFile1.debugSymbolPaths.add(javaFolderPath);

    String remotePath = "remotePath";
    String kotlinFolderPath = createFolderAndGetPath("kotlin");
    soFile1.debugSymbolPaths.add(remotePath);
    myLibrary.pathMappings.put(remotePath, kotlinFolderPath);

    String cppFolderPath = createFolderAndGetPath("cpp");
    soFile2.debugSymbolPaths.add(cppFolderPath);

    List<String> sourceFolderPaths = myLibrary.getSourceFolderPaths();
    assertThat(sourceFolderPaths).containsExactly(javaFolderPath, kotlinFolderPath, cppFolderPath);

    assertSame(sourceFolderPaths, myLibrary.getSourceFolderPaths()); // verify we return the same instance once calculated.
  }

  @NotNull
  private String createFolderAndGetPath(@NotNull String folderName) throws IOException {
    return toSystemDependentName(createFolderInProjectRoot(getProject(), folderName).getPath());
  }
}