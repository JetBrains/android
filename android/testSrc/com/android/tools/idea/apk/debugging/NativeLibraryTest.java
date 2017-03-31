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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link NativeLibrary}.
 */
public class NativeLibraryTest extends IdeaTestCase {
  public void testSetFilePaths() throws IOException {
    List<VirtualFile> files = createLibraryFiles("x86", "arm65-v8a");
    List<String> filePaths = getPaths(files);

    NativeLibrary library = new NativeLibrary("library");
    library.setFilePaths(filePaths);

    assertThat(library.abis).containsExactly("arm65-v8a", "x86"); // They should be sorted.
    assertThat(library.files).containsAllIn(files);
    assertThat(library.getFilePaths()).containsAllIn(filePaths);
  }

  public void testSetFilePathsWithNonExistingPaths() {
    List<String> filePaths = new ArrayList<>();
    filePaths.add("abc.so");

    NativeLibrary library = new NativeLibrary("library");
    library.setFilePaths(filePaths);

    assertThat(library.abis).isEmpty();
    assertThat(library.files).isEmpty();
  }

  public void testAddFilesWithFileList() throws IOException {
    List<VirtualFile> files = createLibraryFiles("x86", "arm65-v8a");
    NativeLibrary library = new NativeLibrary("library");
    library.addFiles(files);

    assertThat(library.abis).containsExactly("arm65-v8a", "x86"); // They should be sorted.
    assertThat(library.files).containsAllIn(files);
    assertThat(library.getFilePaths()).containsAllIn(getPaths(files));
  }

  public void testAddFilesWithFileArray() throws IOException {
    List<VirtualFile> files = createLibraryFiles("x86", "arm65-v8a");
    NativeLibrary library = new NativeLibrary("library");
    library.addFiles(files.toArray(new VirtualFile[files.size()]));

    assertThat(library.abis).containsExactly("arm65-v8a", "x86"); // They should be sorted.
    assertThat(library.files).containsAllIn(files);
    assertThat(library.getFilePaths()).containsAllIn(getPaths(files));
  }

  @NotNull
  private List<VirtualFile> createLibraryFiles(@NotNull String... abis) throws IOException {
    return ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<List<VirtualFile>, IOException>() {
      @Override
      public List<VirtualFile> compute() throws IOException {
        List<VirtualFile> files = new ArrayList<>();
        for (String abi : abis) {
          VirtualFile folder = myProject.getBaseDir().createChildDirectory(this, abi);
          files.add(folder.createChildData(this, "library.so"));
        }
        return files;
      }
    });
  }

  @NotNull
  private static List<String> getPaths(@NotNull List<VirtualFile> files) {
    return files.stream().map(VirtualFile::getPath).collect(Collectors.toList());
  }
}