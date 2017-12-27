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
package com.android.tools.idea.apk.paths;

import com.google.common.base.Splitter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;

import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;

/**
 * Takes full paths and organized them in a hierarchical structure.
 */
public class PathTree extends PathNodeParent {
  @Nullable
  public PathNode addPath(@NotNull String path, char pathSeparator) {
    if (!path.isEmpty()) {
      return addChild(split(path), 0, pathSeparator);
    }
    return null;
  }

  @NotNull
  private static List<String> split(@NotNull String path) {
    String systemIndependentPath = toSystemIndependentName(path);
    return Splitter.on('/').splitToList(systemIndependentPath);
  }

  @TestOnly
  @NotNull
  String print() {
    StringBuilder buffer = new StringBuilder();
    for (PathNode child : getChildren()) {
      child.print(buffer, 0);
    }

    return buffer.toString();
  }
}
