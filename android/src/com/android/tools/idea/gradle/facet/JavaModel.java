/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.facet;

import com.google.common.collect.Lists;
import org.gradle.tooling.model.idea.IdeaContentRoot;
import org.gradle.tooling.model.idea.IdeaDependency;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class JavaModel {
  @NotNull private final List<IdeaContentRoot> myContentRoots;
  @NotNull private final List<? extends IdeaDependency> myDependencies;

  public JavaModel(@NotNull Collection<? extends IdeaContentRoot> contentRoots, @NotNull List<? extends IdeaDependency> dependencies) {
    myContentRoots = contentRoots.isEmpty() ? Collections.<IdeaContentRoot>emptyList() : Lists.<IdeaContentRoot>newArrayList();
    for (IdeaContentRoot contentRoot : contentRoots) {
      if (contentRoot != null) {
        myContentRoots.add(contentRoot);
      }
    }
    myDependencies = dependencies;
  }

  @NotNull
  public List<IdeaContentRoot> getContentRoots() {
    return myContentRoots;
  }

  @NotNull
  public List<? extends IdeaDependency> getDependencies() {
    return myDependencies;
  }
}
