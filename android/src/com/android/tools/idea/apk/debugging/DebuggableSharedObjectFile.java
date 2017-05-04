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

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.android.tools.idea.gradle.util.FilePaths.toSystemDependentPath;

/**
 * User-selected .so file that contains debug symbols.
 */
public class DebuggableSharedObjectFile {
  // These fields are serialized/deserialized to/from XML by ApkFacet.
  // This is not really a class but a container of configuration options.
  @NotNull public String path = "";
  @NotNull public String abi = "";
  @NotNull public List<String> sourceFolderPaths = new ArrayList<>();

  public DebuggableSharedObjectFile() {
  }

  public DebuggableSharedObjectFile(@NotNull String abi, @NotNull VirtualFile file) {
    this.abi = abi;
    File filePath = toSystemDependentPath(file.getPath());
    path = filePath.getPath();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DebuggableSharedObjectFile)) {
      return false;
    }
    DebuggableSharedObjectFile file = (DebuggableSharedObjectFile)o;
    return Objects.equals(path, file.path) &&
           Objects.equals(abi, file.abi) &&
           Objects.equals(sourceFolderPaths, file.sourceFolderPaths);
  }

  @Override
  public int hashCode() {
    return Objects.hash(path, abi, sourceFolderPaths);
  }

  @Override
  public String toString() {
    return "DebuggableSharedObjectFile{" +
           "path='" + path + '\'' +
           ", abi='" + abi + '\'' +
           ", sourceFolderPaths=" + sourceFolderPaths +
           '}';
  }
}
