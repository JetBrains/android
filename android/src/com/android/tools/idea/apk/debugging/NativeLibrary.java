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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

public class NativeLibrary {
  @Transient @NotNull private String myId = "";
  @Transient @NotNull private String myFileName = "";
  @NotNull private String myFilePath = "";

  // This class is serialized to XML in APKFacet. For this serialization, classes must have either getters/setters or public fields.
  @NotNull public List<String> sourceFolderPaths = new ArrayList<>();
  @NotNull public Map<String, String> pathMappings = new HashMap<>();
  @Nullable public String debuggableFilePath;
  public boolean hasDebugSymbols;

  public NativeLibrary() {
  }

  public NativeLibrary(@NotNull String filePath) {
    setFilePath(filePath);
  }

  @NotNull
  public String getFilePath() {
    return myFilePath;
  }

  public void setFilePath(@NotNull String filePath) {
    myFilePath = filePath;
    File path = new File(toSystemDependentName(myFilePath));
    File parentPath = path.getParentFile();
    myFileName = path.getName();
    myId = myFileName;
    if (parentPath != null) {
      myId = parentPath.getName() + '/' + myId;
    }
  }

  @NotNull
  public String getId() {
    return myId;
  }

  @NotNull
  public String getFileName() {
    return myFileName;
  }

  @Nullable
  public VirtualFile getFile() {
    return LocalFileSystem.getInstance().findFileByPath(myFilePath);
  }
}
