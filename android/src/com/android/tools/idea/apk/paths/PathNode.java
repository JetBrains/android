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

import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class PathNode extends PathNodeParent {
  @NotNull private final String myPathSegment;
  @NotNull private final String myPath;
  @Nullable private final PathNodeParent myParent;

  PathNode(@NotNull String pathSegment, @NotNull String path, @Nullable PathNodeParent parent) {
    myPathSegment = pathSegment;
    myPath = path;
    myParent = parent;
  }

  @NotNull
  public String getPathSegment() {
    return myPathSegment;
  }

  @NotNull
  public String getPath() {
    return myPath;
  }

  @Nullable
  public PathNodeParent getParent() {
    return myParent;
  }

  @TestOnly
  void print(@NotNull StringBuilder buffer, int level) {
    int spaceCount = level * 2;
    for (int i = 0; i < spaceCount; i++) {
      buffer.append(' ');
    }
    buffer.append(getPathSegment()).append(SystemProperties.getLineSeparator());
    for (PathNode child : getChildren()) {
      child.print(buffer, level + 1);
    }
  }

  @Override
  public String toString() {
    return myPath;
  }
}
