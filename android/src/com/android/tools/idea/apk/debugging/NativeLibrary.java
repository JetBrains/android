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

import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

import com.android.sdklib.devices.Abi;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.serialization.ClassUtil;
import com.intellij.util.xmlb.annotations.Transient;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NativeLibrary {

  // These fields get serialized to/from XML in ApkFacet.
  @NotNull public String name = "";

  // Key: ABI, value: debuggable .so file.
  @NotNull public Map<Abi, DebuggableSharedObjectFile> debuggableSharedObjectFilesByAbi = new HashMap<>();

  // Key: original path (found in a debuggable .so file), value: path in the local file system.
  @NotNull public Map<String, String> pathMappings = new HashMap<>();

  // Paths of .so files inside APK.
  // b/169230027: This MUST be initialized to an immutable list. Otherwise, deserializer does NOT call
  // the setSharedObjectFilePaths() method.
  @NotNull private List<String> mySharedObjectFilePaths = Collections.emptyList();

  // .so files inside the APK. (LinkedHashMap to preserve insertion order in values, important for tests)
  @Transient @NotNull public final Map<Abi, VirtualFile> sharedObjectFilesByAbi = new LinkedHashMap<>();

  // ABIs supported by the APK.
  @Transient @NotNull public final List<Abi> abis = new ArrayList<>();

  // Cached list of all the source folders in this library. This list is calculated from the debug symbols in every .so file in this
  // library.
  @VisibleForTesting
  @Transient @Nullable List<String> sourceFolderPaths;

  public boolean hasDebugSymbols;

  // Needed for deserialization from disk.
  public NativeLibrary() {}

  public NativeLibrary(@NotNull String name) {
    this.name = name;
  }

  @NotNull
  public List<String> getSharedObjectFilePaths() {
    return mySharedObjectFilePaths;
  }

  @NotNull
  public List<VirtualFile> getSharedObjectFiles() { return new ArrayList<>(sharedObjectFilesByAbi.values()); }

  // This is being invoked when NativeLibrary is deserialized from XML.
  public void setSharedObjectFilePaths(@NotNull List<String> sharedObjectFilePaths) {
    mySharedObjectFilePaths = sharedObjectFilePaths;

    abis.clear();
    this.sharedObjectFilesByAbi.clear();
    List<String> nonExistingPaths = new ArrayList<>();
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    for (String path : sharedObjectFilePaths) {
      VirtualFile file = fileSystem.findFileByPath(path);
      if (file != null) {
        Abi abi = extractAbiFrom(file);
        this.sharedObjectFilesByAbi.put(abi, file);
        abis.add(abi);
        continue;
      }
      nonExistingPaths.add(path);
    }
    sortAbis();

    if (!nonExistingPaths.isEmpty()) {
      // Remove paths not found in the file system.
      mySharedObjectFilePaths.removeAll(nonExistingPaths);
    }
  }

  public void clearDebugSymbols() {
    debuggableSharedObjectFilesByAbi.clear();
    pathMappings.clear();
    hasDebugSymbols = false;
    clearSourceFolderCache();
  }

  public boolean containsMappingForRemotePath(@NotNull String remotePath) {
    return pathMappings.containsKey(remotePath);
  }

  public void addPathMapping(@NotNull String remotePath, @NotNull String localPath) {
    pathMappings.put(remotePath, localPath);
    clearSourceFolderCache();
  }

  public void removePathMapping(@NotNull String remotePath) {
    pathMappings.remove(remotePath);
    clearSourceFolderCache();
  }

  public void replacePathMappingsWith(@NotNull Map<String, String> newPathMappings) {
    pathMappings.clear();
    pathMappings.putAll(newPathMappings);
    clearSourceFolderCache();
  }

  public boolean needsPathMappings() {
    return !pathMappings.isEmpty();
  }

  public void addSharedObjectFiles(@NotNull Collection<VirtualFile> files) {
    for (VirtualFile file : files) {
      doAddSharedObjectFile(file);
    }
    sortAbis();
  }

  private void sortAbis() {
    if (abis.size() > 1) {
      abis.sort(Comparator.comparing(Abi::toString));
    }
  }

  private void doAddSharedObjectFile(@NotNull VirtualFile file) {
    Abi abi = extractAbiFrom(file);
    sharedObjectFilesByAbi.put(abi, file);
    abis.add(abi);
    if (!ClassUtil.isMutableCollection(mySharedObjectFilePaths)) {
      // EMPTY_LIST is immutable. We must reinitialize to a mutable list.
      mySharedObjectFilePaths = new ArrayList<>();
    }
    mySharedObjectFilePaths.add(file.getPath());
  }

  @NotNull
  private static Abi extractAbiFrom(@NotNull VirtualFile file) {
    VirtualFile parent = file.getParent();
    assert parent != null;
    String folderName = parent.getName();
    Abi abi = Abi.getEnum(folderName);
    if (abi == null) {
      throw new IllegalArgumentException("Failed to find ABI for file: '" + toSystemDependentName(file.getPath()) + "'");
    }
    return abi;
  }

  public boolean isMissingPathMappings() {
    for (String mappedPath : pathMappings.values()) {
      if (isEmpty(mappedPath)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public DebuggableSharedObjectFile addDebuggableSharedObjectFile(@NotNull Abi abi, @NotNull VirtualFile file) {
    hasDebugSymbols = true;
    DebuggableSharedObjectFile sharedObjectFile = new DebuggableSharedObjectFile(file);

    debuggableSharedObjectFilesByAbi.put(abi, sharedObjectFile);
    clearSourceFolderCache();
    return sharedObjectFile;
  }

  private void clearSourceFolderCache() {
    sourceFolderPaths = null; // Need to recalculate all the source folders in this library.
  }

  @NotNull
  public List<String> getUserSelectedPathsInMappings() {
    Collection<String> values = pathMappings.values();
    return values.isEmpty() ? Collections.emptyList() : values.stream().filter(StringUtil::isNotEmpty).collect(Collectors.toList());
  }

  @Override
  public String toString() {
    return name;
  }

  @NotNull
  public List<String> getSourceFolderPaths() {
    if (sourceFolderPaths != null) {
      return sourceFolderPaths;
    }
    recalculateSourceFolderPaths();
    return sourceFolderPaths;
  }

  public void recalculateSourceFolderPaths() {
    if (debuggableSharedObjectFilesByAbi.isEmpty()) {
      sourceFolderPaths = Collections.emptyList();
      return;
    }
    sourceFolderPaths = new ArrayList<>();
    for (DebuggableSharedObjectFile sharedObjectFile : debuggableSharedObjectFilesByAbi.values()) {
      for (String debugSymbolPath : sharedObjectFile.debugSymbolPaths) {
        File path = new File(debugSymbolPath);
        if (path.toPath().isAbsolute() && path.exists()) {
          sourceFolderPaths.add(debugSymbolPath);
          continue;
        }
        // path is not absolute or not local. Check for path mapping.
        String mappedPath = pathMappings.get(debugSymbolPath);
        if (isNotEmpty(mappedPath)) {
          path = new File(mappedPath);
          if (path.toPath().isAbsolute() && path.exists()) {
            sourceFolderPaths.add(mappedPath);
          }
        }
      }
    }
    if (sourceFolderPaths.size() > 1) {
      sourceFolderPaths.sort(Comparator.naturalOrder());
    }
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
           Objects.equals(pathMappings, library.pathMappings) &&
           Objects.equals(mySharedObjectFilePaths, library.mySharedObjectFilePaths) &&
           Objects.equals(debuggableSharedObjectFilesByAbi, library.debuggableSharedObjectFilesByAbi);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, pathMappings, mySharedObjectFilePaths, debuggableSharedObjectFilesByAbi, hasDebugSymbols);
  }
}
