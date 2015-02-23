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

import org.gradle.tooling.model.GradleTask;
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
import java.util.List;

import static com.android.tools.idea.gradle.facet.JavaGradleFacet.COMPILE_JAVA_TASK_NAME;
import static java.util.Collections.emptyList;

public class IdeaJavaProject implements Serializable {
  private static final long serialVersionUID = 1L;

  @NotNull private final String myModuleName;
  @NotNull private final Collection<? extends IdeaContentRoot> myContentRoots;
  @NotNull private final List<? extends IdeaDependency> myDependencies;

  @Nullable private final ExtIdeaCompilerOutput myCompilerOutput;
  @Nullable private final File myBuildFolderPath;

  private final boolean myBuildable;

  @NotNull
  public static IdeaJavaProject newJavaProject(@NotNull final IdeaModule ideaModule, @Nullable ModuleExtendedModel extendedModel) {
    Collection<? extends IdeaContentRoot> contentRoots = getContentRoots(ideaModule, extendedModel);
    ExtIdeaCompilerOutput compilerOutput = extendedModel != null ? extendedModel.getCompilerOutput() : null;
    File buildFolderPath = ideaModule.getGradleProject().getBuildDirectory();
    boolean buildable = isBuildable(ideaModule);
    return new IdeaJavaProject(ideaModule.getName(), contentRoots, getDependencies(ideaModule), compilerOutput, buildFolderPath, buildable);
  }

  @NotNull
  private static Collection<? extends IdeaContentRoot> getContentRoots(@NotNull IdeaModule ideaModule,
                                                                       @Nullable ModuleExtendedModel extendedModel) {
    Collection<? extends IdeaContentRoot> contentRoots = extendedModel != null ? extendedModel.getContentRoots() : null;
    if (contentRoots != null) {
      return contentRoots;
    }
    contentRoots = ideaModule.getContentRoots();
    if (contentRoots != null) {
      return contentRoots;
    }
    return emptyList();
  }

  @NotNull
  private static List<? extends IdeaDependency> getDependencies(@NotNull IdeaModule ideaModule) {
    List<? extends IdeaDependency> dependencies = ideaModule.getDependencies().getAll();
    if (dependencies != null) {
      return dependencies;
    }
    return emptyList();
  }

  private static boolean isBuildable(@NotNull IdeaModule ideaModule) {
    for (GradleTask task : ideaModule.getGradleProject().getTasks()) {
      if (COMPILE_JAVA_TASK_NAME.equals(task.getName())) {
        return true;
      }
    }
    return false;
  }

  public IdeaJavaProject(@NotNull String name,
                         @NotNull Collection<? extends IdeaContentRoot> contentRoots,
                         @NotNull List<? extends IdeaDependency> dependencies,
                         @Nullable ExtIdeaCompilerOutput compilerOutput,
                         @Nullable File buildFolderPath,
                         boolean buildable) {
    myModuleName = name;
    myContentRoots = contentRoots;
    myDependencies = dependencies;
    myCompilerOutput = compilerOutput;
    myBuildFolderPath = buildFolderPath;
    myBuildable = buildable;
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

  public boolean isBuildable() {
    return myBuildable;
  }
}
