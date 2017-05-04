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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;

public class NativeLibrary {
  // These fields get serialized to/from XML in ApkFacet.
  @NotNull public String name = "";
  @NotNull public List<String> sourceFolderPaths = new ArrayList<>();

  // Key: ABI, value: debuggable .so file.
  @NotNull public Map<String, DebuggableSharedObjectFile> debuggableSharedObjectFilesByAbi = new HashMap<>();

  // Key: original path (found in a debuggable .so file), value: path in the local file system.
  @NotNull public Map<String, String> pathMappings = new HashMap<>();

  @NotNull private List<String> sharedObjectFilePaths = new ArrayList<>();

  @Transient @NotNull public List<VirtualFile> sharedObjectFiles = new ArrayList<>();
  @Transient @NotNull public List<String> abis = new ArrayList<>();

  public boolean hasDebugSymbols;

  public NativeLibrary() {
  }

  public NativeLibrary(@NotNull String name) {
    this.name = name;
  }

  @NotNull
  public List<String> getSharedObjectFilePaths() {
    return sharedObjectFilePaths;
  }

  // This is being invoked when NativeLibrary is deserialized from XML.
  public void setSharedObjectFilePaths(@NotNull List<String> sharedObjectFilePaths) {
    this.sharedObjectFilePaths = sharedObjectFilePaths;

    List<String> nonExistingPaths = new ArrayList<>();
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    for (String path : sharedObjectFilePaths) {
      VirtualFile file = fileSystem.findFileByPath(path);
      if (file != null) {
        this.sharedObjectFiles.add(file);
        abis.add(extractAbiFrom(file));
        continue;
      }
      nonExistingPaths.add(path);
    }
    sortAbis();

    if (!nonExistingPaths.isEmpty()) {
      // Remove paths not found in the file system.
      this.sharedObjectFilePaths.removeAll(nonExistingPaths);
    }
  }

  @TestOnly
  public void addSharedObjectFile(@NotNull VirtualFile file) {
    doAddSharedObjectFile(file);
    sortAbis();
  }

  public void addSharedObjectFiles(@NotNull List<VirtualFile> files) {
    for (VirtualFile file : files) {
      doAddSharedObjectFile(file);
    }
    sortAbis();
  }

  private void sortAbis() {
    if (abis.size() > 1) {
      abis.sort(String::compareTo);
    }
  }

  private void doAddSharedObjectFile(@NotNull VirtualFile file) {
    sharedObjectFiles.add(file);
    abis.add(extractAbiFrom(file));
    sharedObjectFilePaths.add(file.getPath());
  }

  @NotNull
  private static String extractAbiFrom(@NotNull VirtualFile file) {
    VirtualFile parent = file.getParent();
    assert parent != null;
    return parent.getName();
  }

  public boolean isMissingPathMappings() {
    for (String mappedPath : pathMappings.values()) {
      if (isEmpty(mappedPath)) {
        return true;
      }
    }
    return false;
  }

  public void addDebuggableSharedObjectFile(@NotNull Abi abi, @NotNull VirtualFile file) {
    hasDebugSymbols = true;
    DebuggableSharedObjectFile sharedObjectFile = new DebuggableSharedObjectFile(abi.toString(), file);
    debuggableSharedObjectFilesByAbi.put(sharedObjectFile.abi, sharedObjectFile);
    pathMappings.clear();
    sourceFolderPaths.clear();
  }

  @NotNull
  public List<String> getUserSelectedPathsInMappings() {
    Collection<String> values = pathMappings.values();
    return values.isEmpty() ? Collections.emptyList() : values.stream().filter(StringUtil::isNotEmpty).collect(Collectors.toList());
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
           Objects.equals(sharedObjectFilePaths, library.sharedObjectFilePaths) &&
           Objects.equals(debuggableSharedObjectFilesByAbi, library.debuggableSharedObjectFilesByAbi);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, sourceFolderPaths, pathMappings, sharedObjectFilePaths, debuggableSharedObjectFilesByAbi, hasDebugSymbols);
  }

  @Override
  public String toString() {
    return name;
  }
}
