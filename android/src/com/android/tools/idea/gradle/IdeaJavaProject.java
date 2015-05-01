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

import com.android.tools.idea.gradle.model.java.JarLibraryDependency;
import com.android.tools.idea.gradle.model.java.JavaModuleContentRoot;
import com.android.tools.idea.gradle.model.java.JavaModuleDependency;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.model.idea.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExtIdeaCompilerOutput;
import org.jetbrains.plugins.gradle.model.ModuleExtendedModel;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.tools.idea.gradle.facet.JavaGradleFacet.COMPILE_JAVA_TASK_NAME;
import static java.util.Collections.emptyList;

public class IdeaJavaProject implements Serializable {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 4L;

  @NotNull private String myModuleName;
  @NotNull private Collection<JavaModuleContentRoot> myContentRoots = Lists.newArrayList();
  @NotNull private Collection<JavaModuleDependency> myJavaModuleDependencies = Lists.newArrayList();
  @NotNull private Collection<JarLibraryDependency> myJarLibraryDependencies = Lists.newArrayList();

  @Nullable private Map<String, Set<File>> myArtifactsByConfiguration;
  @Nullable private ExtIdeaCompilerOutput myCompilerOutput;
  @Nullable private File myBuildFolderPath;

  private boolean myBuildable;

  @NotNull
  public static IdeaJavaProject newJavaProject(@NotNull final IdeaModule ideaModule, @Nullable ModuleExtendedModel extendedModel) {
    Collection<? extends IdeaContentRoot> contentRoots = getContentRoots(ideaModule, extendedModel);
    Map<String, Set<File>> artifactsByConfiguration = Maps.newHashMap();
    if (extendedModel != null) {
      artifactsByConfiguration = extendedModel.getArtifactsByConfiguration();
    }
    ExtIdeaCompilerOutput compilerOutput = extendedModel != null ? extendedModel.getCompilerOutput() : null;
    File buildFolderPath = ideaModule.getGradleProject().getBuildDirectory();
    boolean buildable = isBuildable(ideaModule);
    return new IdeaJavaProject(ideaModule.getName(), contentRoots, getDependencies(ideaModule), artifactsByConfiguration, compilerOutput,
                               buildFolderPath, buildable);
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
                         @Nullable Map<String, Set<File>> artifactsByConfiguration,
                         @Nullable ExtIdeaCompilerOutput compilerOutput,
                         @Nullable File buildFolderPath,
                         boolean buildable) {
    myModuleName = name;
    for (IdeaContentRoot contentRoot : contentRoots) {
      if (contentRoot != null) {
        myContentRoots.add(JavaModuleContentRoot.copy(contentRoot));
      }
    }

    for (IdeaDependency dependency : dependencies) {
      if (dependency instanceof IdeaSingleEntryLibraryDependency) {
        JarLibraryDependency libraryDependency = JarLibraryDependency.copy((IdeaSingleEntryLibraryDependency)dependency);
        if (libraryDependency != null) {
          myJarLibraryDependencies.add(libraryDependency);
        }
      }
      else if (dependency instanceof IdeaModuleDependency) {
        JavaModuleDependency moduleDependency = JavaModuleDependency.copy((IdeaModuleDependency)dependency);
        if (moduleDependency != null) {
          myJavaModuleDependencies.add(moduleDependency);
        }
      }
    }

    myArtifactsByConfiguration = artifactsByConfiguration;
    myCompilerOutput = compilerOutput;
    myBuildFolderPath = buildFolderPath;
    myBuildable = buildable;
  }

  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  @NotNull
  public Collection<JavaModuleContentRoot> getContentRoots() {
    return myContentRoots;
  }

  @Nullable
  public Map<String, Set<File>> getArtifactsByConfiguration() {
    return myArtifactsByConfiguration;
  }

  @Nullable
  public ExtIdeaCompilerOutput getCompilerOutput() {
    return myCompilerOutput;
  }

  @Nullable
  public File getBuildFolderPath() {
    return myBuildFolderPath;
  }

  @NotNull
  public Collection<JavaModuleDependency> getJavaModuleDependencies() {
    return myJavaModuleDependencies;
  }

  @NotNull
  public Collection<JarLibraryDependency> getJarLibraryDependencies() {
    return myJarLibraryDependencies;
  }

  public boolean isBuildable() {
    return myBuildable;
  }
}