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
package com.android.tools.idea.gradle;

import org.gradle.tooling.model.idea.IdeaContentRoot;
import org.gradle.tooling.model.idea.IdeaDependency;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExtIdeaCompilerOutput;
import org.jetbrains.plugins.gradle.model.ModuleExtendedModel;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class IdeaJavaProject implements Serializable {
  @NotNull private final String myModuleName;
  @NotNull private final Collection<? extends IdeaContentRoot> myContentRoots;
  @NotNull private final List<? extends IdeaDependency> myDependencies;

  @Nullable private final ExtIdeaCompilerOutput myCompilerOutput;
  @Nullable private final File myBuildFolderPath;

  public IdeaJavaProject(@NotNull final IdeaModule ideaModule, @Nullable ModuleExtendedModel extendedModel) {
    myModuleName = ideaModule.getName();
    myContentRoots = getContentRoots(ideaModule, extendedModel);
    myDependencies = getDependencies(ideaModule);
    myCompilerOutput = extendedModel != null ? extendedModel.getCompilerOutput() : null;
    myBuildFolderPath = ideaModule.getGradleProject().getBuildDirectory();
  }

  @NotNull
  private static Collection<? extends IdeaContentRoot> getContentRoots(@NotNull IdeaModule ideaModule,
                                                                       @Nullable ModuleExtendedModel extendedModel) {
    Collection<? extends IdeaContentRoot> contentRoots = null;
    if (extendedModel != null) {
      contentRoots = extendedModel.getContentRoots();
    }
    if (contentRoots != null) {
      return contentRoots;
    }
    contentRoots = ideaModule.getContentRoots();
    return contentRoots != null ? contentRoots : Collections.<IdeaContentRoot>emptyList();
  }

  @NotNull
  private static List<? extends IdeaDependency> getDependencies(IdeaModule ideaModule) {
    List<? extends IdeaDependency> dependencies = ideaModule.getDependencies().getAll();
    return dependencies != null ? dependencies : Collections.<IdeaDependency>emptyList();
  }

  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  @NotNull
  public Collection<? extends IdeaContentRoot> getContentRoots() {
    return myContentRoots;
  }

  @NotNull
  public List<? extends IdeaDependency> getDependencies() {
    return myDependencies;
  }

  @Nullable
  public ExtIdeaCompilerOutput getCompilerOutput() {
    return myCompilerOutput;
  }

  @Nullable
  public File getBuildFolderPath() {
    return myBuildFolderPath;
  }
}
