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

import com.intellij.openapi.diagnostic.Logger;
import org.gradle.tooling.model.idea.IdeaContentRoot;
import org.gradle.tooling.model.idea.IdeaDependency;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class JavaModel {
  private static final Logger LOG = Logger.getInstance(JavaModel.class);

  @Nullable private final IdeaContentRoot myContentRoot;
  @NotNull private final List<? extends IdeaDependency> myDependencies;

  public JavaModel(@NotNull Collection<? extends IdeaContentRoot> contentRoots, @NotNull List<? extends IdeaDependency> dependencies) {
    myContentRoot = getFirstNotNull(contentRoots);
    myDependencies = dependencies;
  }

  @Nullable
  private static IdeaContentRoot getFirstNotNull(Collection<? extends IdeaContentRoot> contentRoots) {
    // The IDEA model returns a Collection of IdeaContentRoots, but in practice there should be no more than one element in it.
    if (contentRoots.size() > 1) {
      LOG.info(String.format("Found a JavaModel with %d content roots", contentRoots.size()));
    }
    for (IdeaContentRoot contentRoot : contentRoots) {
      if (contentRoot != null) {
        return contentRoot;
      }
    }
    return null;
  }

  @Nullable
  public IdeaContentRoot getContentRoot() {
    return myContentRoot;
  }

  @NotNull
  public List<? extends IdeaDependency> getDependencies() {
    return myDependencies;
  }
}
