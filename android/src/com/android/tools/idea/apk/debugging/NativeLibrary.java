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

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class NativeLibrary {
  // These fields get serialized to/from XML in ApkFacet.
  @NotNull public String name = "";
  @NotNull public List<String> sourceFolderPaths = new ArrayList<>();
  @NotNull public Map<String, String> pathMappings = new HashMap<>();

  @NotNull private List<String> filePaths = new ArrayList<>();
  @Transient @NotNull public List<VirtualFile> files = new ArrayList<>();
  @Transient @NotNull public List<String> abis = new ArrayList<>();

  @Nullable public String debuggableFilePath;

  public boolean hasDebugSymbols;

  public NativeLibrary() {
  }

  public NativeLibrary(@NotNull NativeLibrary library) {
    copyFrom(library);
  }

  public void copyFrom(@NotNull NativeLibrary library) {
    name = library.name;

    sourceFolderPaths.clear();
    sourceFolderPaths.addAll(library.sourceFolderPaths);

    pathMappings.clear();
    pathMappings.putAll(library.pathMappings);

    filePaths.clear();
    filePaths.addAll(library.filePaths);

    files.clear();
    files.addAll(library.files);

    abis.clear();
    abis.addAll(library.abis);

    debuggableFilePath = library.debuggableFilePath;
    hasDebugSymbols = library.hasDebugSymbols;
  }

  public NativeLibrary(@NotNull String name) {
    this.name = name;
  }

  @NotNull
  public List<String> getFilePaths() {
    return filePaths;
  }

  // This is being invoked when NativeLibrary is deserialized from XML.
  public void setFilePaths(@NotNull List<String> filePaths) {
    this.filePaths = filePaths;

    List<String> nonExistingPaths = new ArrayList<>();
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    for (String path : filePaths) {
      VirtualFile file = fileSystem.findFileByPath(path);
      if (file != null) {
        this.files.add(file);
        abis.add(extractAbiFrom(file));
        continue;
      }
      nonExistingPaths.add(path);
    }
    sortAbis();

    if (!nonExistingPaths.isEmpty()) {
      // Remove paths not found in the file system.
      this.filePaths.removeAll(nonExistingPaths);
    }
  }

  public void addFiles(@NotNull VirtualFile... files) {
    for (VirtualFile file : files) {
      addFile(file);
    }
    sortAbis();
  }

  public void addFiles(@NotNull List<VirtualFile> files) {
    for (VirtualFile file : files) {
      addFile(file);
    }
    sortAbis();
  }

  private void sortAbis() {
    if (abis.size() > 1) {
      abis.sort(String::compareTo);
    }
  }

  private void addFile(@NotNull VirtualFile file) {
    files.add(file);
    abis.add(extractAbiFrom(file));
    filePaths.add(file.getPath());
  }

  @NotNull
  private static String extractAbiFrom(@NotNull VirtualFile file) {
    VirtualFile parent = file.getParent();
    assert parent != null;
    return parent.getName();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    NativeLibrary library = (NativeLibrary)o;
    return hasDebugSymbols == library.hasDebugSymbols &&
           Objects.equals(name, library.name) &&
           Objects.equals(sourceFolderPaths, library.sourceFolderPaths) &&
           Objects.equals(pathMappings, library.pathMappings) &&
           Objects.equals(filePaths, library.filePaths) &&
           Objects.equals(debuggableFilePath, library.debuggableFilePath);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, sourceFolderPaths, pathMappings, filePaths, debuggableFilePath, hasDebugSymbols);
  }

  @Override
  public String toString() {
    return name;
  }
}
