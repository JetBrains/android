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

import com.google.common.base.Joiner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class PathNodeParent {
  @NotNull private final Map<String, PathNode> myChildrenByPathSegment = new LinkedHashMap<>();

  @Nullable
  final PathNode addChild(@NotNull List<String> segments, int index, char pathSeparator) {
    if (index < segments.size()) {
      String pathSegment = segments.get(index);
      String path = Joiner.on(pathSeparator).join(segments.subList(0, index + 1));
      PathNode child = myChildrenByPathSegment.computeIfAbsent(pathSegment, s -> new PathNode(s, path, this));
      child.addChild(segments, index + 1, pathSeparator);
      return child;
    }
    return null;
  }

  @NotNull
  public final Collection<PathNode> getChildren() {
    return myChildrenByPathSegment.values();
  }
}
