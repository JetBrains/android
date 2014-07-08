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

import com.android.SdkConstants;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.google.common.collect.Lists;
import com.intellij.util.containers.ContainerUtil;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.model.idea.IdeaContentRoot;
import org.gradle.tooling.model.idea.IdeaDependency;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExtIdeaCompilerOutput;
import org.jetbrains.plugins.gradle.model.ModuleExtendedModel;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class IdeaJavaProject {
  @NotNull private final String myModuleName;
  @NotNull private final Collection<? extends IdeaContentRoot> myContentRoots;
  @NotNull private final List<? extends IdeaDependency> myDependencies;

  @Nullable private final ExtIdeaCompilerOutput myCompilerOutput;
  @Nullable private final File myBuildFolderPath;

  public IdeaJavaProject(@NotNull IdeaModule ideaModule, @Nullable ModuleExtendedModel extendedModel) {
    myModuleName = ideaModule.getName();
    myContentRoots = getContentRoots(ideaModule, extendedModel);
    myDependencies = getDependencies(ideaModule);
    myCompilerOutput = extendedModel != null ? extendedModel.getCompilerOutput() : null;

    // find "build" folder.
    File buildFolderPath = null;
    try {
      buildFolderPath = ideaModule.getGradleProject().getBuildDirectory();
    }
    catch (UnsupportedMethodException e) {
      // Method "getBuildDirectory" was introduced in Gradle 2.0. We'll get this exception when the project uses an older Gradle version.
    }

    // TODO remove this workaround for getting the path of build folder once the Android Gradle plug-in supports Gradle 2.0.
    if (buildFolderPath == null) {
      buildFolderPath = extendedModel != null ? extendedModel.getBuildDir() : null;
    }

    if (buildFolderPath == null) {
      // We could not obtain path to "build" file. This has been happening on Windows 8. Now we need to guess the path.
      for (IdeaContentRoot contentRoot : myContentRoots) {
        for (File excluded : contentRoot.getExcludeDirectories()) {
          if (GradleUtil.BUILD_DIR_DEFAULT_NAME.equals(excluded.getName())) {
            buildFolderPath = excluded;
            break;
          }
        }
        if (buildFolderPath != null) {
          break;
        }
      }
      if (buildFolderPath == null) {
        // If we got here is because the user changed the default location of the "build" folder. We try our best to guess it.
        if (myContentRoots.size() == 1) {
          IdeaContentRoot contentRoot = ContainerUtil.getFirstItem(myContentRoots);
          if (contentRoot != null) {
            Set<File> excludedPaths = contentRoot.getExcludeDirectories();
            if (excludedPaths.size() == 2) {
              // If there are 2 excluded folders, one is .gradle and the other one is build folder.
              List<File> paths = Lists.newArrayList(excludedPaths);
              File path = paths.get(0);
              buildFolderPath = !isDotGradleFolder(path) ? path : paths.get(1);
            }
            if (excludedPaths.size() == 1) {
              // If there is one excluded folder, we take it as long as it is not .gradle folder.
              File path = ContainerUtil.getFirstItem(excludedPaths);
              if (path != null && !isDotGradleFolder(path)) {
                buildFolderPath = path;
              }
            }
          }
        }
      }
    }
    myBuildFolderPath = buildFolderPath;
  }

  private static boolean isDotGradleFolder(@NotNull File path) {
    return SdkConstants.DOT_GRADLE.equals(path.getName());
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
