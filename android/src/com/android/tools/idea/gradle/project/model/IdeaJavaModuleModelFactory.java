/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model;

import static com.intellij.openapi.util.text.StringUtil.equalsIgnoreCase;
import static org.jetbrains.plugins.gradle.service.project.CommonGradleProjectResolverExtension.getGradleOutputDir;

import com.android.tools.idea.gradle.model.java.IdeaJarLibraryDependencyFactory;
import com.android.tools.idea.gradle.model.java.JarLibraryDependency;
import com.android.tools.idea.gradle.model.java.JavaModuleContentRoot;
import com.android.tools.idea.gradle.model.java.JavaModuleDependency;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.util.Pair;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gradle.tooling.model.idea.IdeaContentRoot;
import org.gradle.tooling.model.idea.IdeaDependency;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaModuleDependency;
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExtIdeaCompilerOutput;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.model.ExternalSourceSet;
import org.jetbrains.plugins.gradle.tooling.internal.IdeaCompilerOutputImpl;

/**
 * Factory class to create JavaModuleModel instance from IdeaModule.
 */
public class IdeaJavaModuleModelFactory {
  @NotNull private final IdeaJarLibraryDependencyFactory myIdeaJarLibraryDependencyFactory;

  public IdeaJavaModuleModelFactory() {
    this(new IdeaJarLibraryDependencyFactory());
  }

  @VisibleForTesting
  IdeaJavaModuleModelFactory(@NotNull IdeaJarLibraryDependencyFactory ideaJarLibraryDependencyFactory) {
    myIdeaJarLibraryDependencyFactory = ideaJarLibraryDependencyFactory;
  }

  @NotNull
  public JavaModuleModel create(@NotNull IdeaModule ideaModule,
                                @Nullable ExternalProject externalProject,
                                boolean isBuildable) {
    Pair<Collection<JavaModuleDependency>, Collection<JarLibraryDependency>> dependencies = getDependencies(ideaModule);
    return JavaModuleModel.create(
      ideaModule.getName(),
                                  getContentRoots(ideaModule),
                                  dependencies.first,
                                  dependencies.second,
                                  getArtifactsByConfiguration(externalProject),
                                  getCompilerOutput(externalProject),
                                  ideaModule.getGradleProject().getBuildDirectory(),
                                  getLanguageLevel(externalProject),
                                  isBuildable);
  }

  @NotNull
  private static ExtIdeaCompilerOutput getCompilerOutputCopy(@NotNull ExternalProject externalProject) {
    IdeaCompilerOutputImpl clone = new IdeaCompilerOutputImpl();
    clone.setMainClassesDir(getGradleOutputDir(externalProject, "main", ExternalSystemSourceType.SOURCE));
    clone.setMainResourcesDir(getGradleOutputDir(externalProject, "main", ExternalSystemSourceType.RESOURCE));
    clone.setTestClassesDir(getGradleOutputDir(externalProject, "test", ExternalSystemSourceType.TEST));
    clone.setTestResourcesDir(getGradleOutputDir(externalProject, "test", ExternalSystemSourceType.TEST_RESOURCE));
    return clone;
  }

  @Nullable
  private static ExtIdeaCompilerOutput getCompilerOutput(@Nullable ExternalProject externalProject) {
    return externalProject != null ? getCompilerOutputCopy(externalProject) : null;
  }

  @NotNull
  private static Collection<JavaModuleContentRoot> getContentRoots(@NotNull IdeaModule ideaModule) {
    Collection<? extends IdeaContentRoot> contentRoots = ideaModule.getContentRoots();
    Collection<JavaModuleContentRoot> javaModuleContentRoots = new ArrayList<>();

    if (contentRoots != null) {
      for (IdeaContentRoot contentRoot : contentRoots) {
        if (contentRoot != null) {
          javaModuleContentRoots.add(JavaModuleContentRoot.copy(contentRoot));
        }
      }
    }
    return javaModuleContentRoots;
  }

  @NotNull
  private static Map<String, Set<File>> getArtifactsByConfiguration(@Nullable ExternalProject externalProject) {
    return externalProject != null ? externalProject.getArtifactsByConfiguration() : Collections.emptyMap();
  }

  @NotNull
  private Pair<Collection<JavaModuleDependency>, Collection<JarLibraryDependency>> getDependencies(@NotNull IdeaModule ideaModule) {
    List<? extends IdeaDependency> dependencies = ideaModule.getDependencies().getAll();
    Collection<JavaModuleDependency> javaModuleDependencies = new ArrayList<>();
    Collection<JarLibraryDependency> jarLibraryDependencies = new ArrayList<>();

    if (dependencies != null) {
      for (IdeaDependency dependency : dependencies) {
        if (dependency instanceof IdeaSingleEntryLibraryDependency) {
          JarLibraryDependency libraryDependency = myIdeaJarLibraryDependencyFactory.create((IdeaSingleEntryLibraryDependency)dependency);
          if (libraryDependency != null) {
            jarLibraryDependencies.add(libraryDependency);
          }
        }
        else if (dependency instanceof IdeaModuleDependency) {
          // Don't include runtime module dependencies. b/63819274.
          // Consider example,
          // libA implementation depends on libB, libB api depends on libAPI, libB implementation depends on LibImpl.
          // libA should have implementation dependency on libB and libAPI, but not on LibImpl, libA however still have runtime dependency on LibImpl.
          // So we need to exclude runtime module dependencies.
          if (equalsIgnoreCase(dependency.getScope().getScope(), "RUNTIME")) {
            continue;
          }
          JavaModuleDependency moduleDependency = JavaModuleDependency.copy(
            ideaModule.getProject(),
            (IdeaModuleDependency)dependency);
          if (moduleDependency != null) {
            javaModuleDependencies.add(moduleDependency);
          }
        }
      }
    }
    return Pair.create(javaModuleDependencies, jarLibraryDependencies);
  }

  @Nullable
  private static String getLanguageLevel(@Nullable ExternalProject externalProject) {
    if (externalProject == null) {
      return null;
    }
    ExternalSourceSet mainSourceSet = externalProject.getSourceSets().get("main");
    return mainSourceSet == null ? null : mainSourceSet.getSourceCompatibility();
  }
}
