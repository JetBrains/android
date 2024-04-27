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
package com.android.tools.idea.gradle.dsl.api.dependencies;

import static com.android.tools.idea.gradle.dsl.utils.SdkConstants.GRADLE_PATH_SEPARATOR;

import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public interface ModuleDependencyModel extends DependencyModel {
  @NotNull
  String name();

  default void setName(@NotNull String name) {
    String newPath;
    ResolvedPropertyModel path = path();

    // Keep empty spaces, needed when putting the path back together
    List<String> segments = Splitter.on(GRADLE_PATH_SEPARATOR).splitToList(path.forceString());
    List<String> modifiableSegments = Lists.newArrayList(segments);
    int segmentCount = modifiableSegments.size();
    if (segmentCount == 0) {
      newPath = GRADLE_PATH_SEPARATOR + name.trim();
    }
    else {
      modifiableSegments.set(segmentCount - 1, name);
      newPath = Joiner.on(GRADLE_PATH_SEPARATOR).join(modifiableSegments);
    }
    path.setValue(newPath);
  }

  @NotNull
  ResolvedPropertyModel path();

  @NotNull
  ResolvedPropertyModel configuration();
}
