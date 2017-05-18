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
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static com.android.sdklib.devices.Abi.ARM64_V8A;
import static com.android.sdklib.devices.Abi.X86;
import static com.android.tools.idea.apk.debugging.SharedObjectFiles.createSharedObjectFiles;
import static com.android.utils.FileUtils.toSystemIndependentPath;
import static com.google.common.truth.Truth.assertThat;

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
    assertThat(myLibrary.sharedObjectFiles).containsAllIn(files);
    assertThat(myLibrary.getSharedObjectFilePaths()).containsAllIn(filePaths);
  }

  public void testSetFilePathsWithNonExistingPaths() {
    List<String> filePaths = new ArrayList<>();
    filePaths.add("abc.so");

    myLibrary.setSharedObjectFilePaths(filePaths);

    assertThat(myLibrary.abis).isEmpty();
    assertThat(myLibrary.sharedObjectFiles).isEmpty();
  }

  public void testAddFilesWithFileList() throws IOException {
    Collection<VirtualFile> files = doCreateSharedObjectFiles(X86, ARM64_V8A);
    myLibrary.addSharedObjectFiles(files);

    assertThat(myLibrary.abis).containsExactly(X86, ARM64_V8A);
    assertEquals(ARM64_V8A, myLibrary.abis.get(0)); // Should be sorted.
    assertThat(myLibrary.sharedObjectFiles).containsAllIn(files);
    assertThat(myLibrary.getSharedObjectFilePaths()).containsAllIn(getPaths(files));
  }

  public void testAddFilesWithFileArray() throws IOException {
    Collection<VirtualFile> files = doCreateSharedObjectFiles(X86, ARM64_V8A);
    myLibrary.addSharedObjectFiles(files);

    assertThat(myLibrary.abis).containsExactly(X86, ARM64_V8A);
    assertEquals(ARM64_V8A, myLibrary.abis.get(0)); // Should be sorted.
    assertThat(myLibrary.sharedObjectFiles).containsAllIn(files);
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

  public void testIsMissingPathMappingsWithNonEmptyMappings() {
    myLibrary.pathMappings.put("abc.so", "abc.so");
    assertFalse(myLibrary.isMissingPathMappings()); // The all values in the map are not empty
  }

  public void testIsMissingPathMappingsWithEmptyMappings() {
    myLibrary.pathMappings.put("abc.so", "");
    assertTrue(myLibrary.isMissingPathMappings());
  }

  public void testAddDebuggableSharedObjectFile() throws IOException {
    myLibrary.hasDebugSymbols = false;
    myLibrary.pathMappings.put("abc.so", "");
    myLibrary.sourceFolderPaths.add("source1");

    VirtualFile debuggableFile = ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<VirtualFile, IOException>() {
      @Override
      public VirtualFile compute() throws IOException {
        return getProject().getBaseDir().createChildData(this, "debuggable.so");
      }
    });

    Abi abi = X86;
    myLibrary.addDebuggableSharedObjectFile(abi, debuggableFile);
    assertTrue(myLibrary.hasDebugSymbols);

    DebuggableSharedObjectFile stored = myLibrary.debuggableSharedObjectFilesByAbi.get(abi);
    assertEquals(debuggableFile.getPath(), toSystemIndependentPath(stored.path));
  }

  public void testGetUserSelectedPathsInMappings() {
    assertThat(myLibrary.getUserSelectedPathsInMappings()).isEmpty();

    myLibrary.pathMappings.put("abc.so", "");
    assertThat(myLibrary.getUserSelectedPathsInMappings()).isEmpty();

    myLibrary.pathMappings.put("xyz.so", "123.so");
    assertThat(myLibrary.getUserSelectedPathsInMappings()).containsExactly("123.so");
  }
}