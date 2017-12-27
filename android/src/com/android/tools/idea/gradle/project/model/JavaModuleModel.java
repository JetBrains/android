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

import com.android.tools.idea.gradle.model.java.JarLibraryDependency;
import com.android.tools.idea.gradle.model.java.JavaModuleContentRoot;
import com.android.tools.idea.gradle.model.java.JavaModuleDependency;
import com.intellij.pom.java.LanguageLevel;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExtIdeaCompilerOutput;

import java.io.File;
import java.util.*;

import static com.android.tools.idea.gradle.project.facet.java.JavaFacet.COMPILE_JAVA_TASK_NAME;
import static com.intellij.openapi.util.io.FileUtil.isAncestor;

/**
 * Base model for Java library modules
 */
public class JavaModuleModel implements ModuleModel {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 2L;

  @NotNull private final String myModuleName;
  @NotNull private final Collection<JavaModuleContentRoot> myContentRoots;
  @NotNull private final Collection<JavaModuleDependency> myJavaModuleDependencies;
  @NotNull private final Collection<JarLibraryDependency> myJarLibraryDependencies;
  @NotNull private final Map<String, Set<File>> myArtifactsByConfiguration;
  @NotNull private final List<String> myConfigurations;

  @Nullable private final ExtIdeaCompilerOutput myCompilerOutput;
  @Nullable private final File myBuildFolderPath;
  @Nullable private final String myLanguageLevel;

  private final boolean myBuildable;
  private final boolean myAndroidModuleWithoutVariants;

  public JavaModuleModel(@NotNull String moduleName,
                         @NotNull Collection<JavaModuleContentRoot> contentRoots,
                         @NotNull Collection<JavaModuleDependency> javaModuleDependencies,
                         @NotNull Collection<JarLibraryDependency> jarLibraryDependencies,
                         @NotNull Map<String, Set<File>> artifactsByConfiguration,
                         @Nullable ExtIdeaCompilerOutput compilerOutput,
                         @Nullable File buildFolderPath,
                         @Nullable String languageLevel,
                         boolean buildable,
                         boolean androidModuleWithoutVariants) {
    myModuleName = moduleName;
    myContentRoots = contentRoots;
    myJavaModuleDependencies = javaModuleDependencies;
    myJarLibraryDependencies = jarLibraryDependencies;
    myArtifactsByConfiguration = artifactsByConfiguration;
    myCompilerOutput = compilerOutput;
    myBuildFolderPath = buildFolderPath;
    myLanguageLevel = languageLevel;
    myBuildable = buildable;
    myAndroidModuleWithoutVariants = androidModuleWithoutVariants;
    myConfigurations = new ArrayList<>(myArtifactsByConfiguration.keySet());
    Collections.sort(myConfigurations);
  }

  @Override
  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  @NotNull
  public Collection<JavaModuleContentRoot> getContentRoots() {
    return myContentRoots;
  }

  public boolean containsSourceFile(@NotNull File file) {
    for (JavaModuleContentRoot contentRoot : getContentRoots()) {
      if (contentRoot != null) {
        if (containsFile(contentRoot, file)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean containsFile(@NotNull JavaModuleContentRoot contentRoot, @NotNull File file) {
    if (containsFile(contentRoot.getSourceDirPaths(), file) ||
        containsFile(contentRoot.getTestDirPaths(), file) ||
        containsFile(contentRoot.getResourceDirPaths(), file) ||
        containsFile(contentRoot.getGenSourceDirPaths(), file) ||
        containsFile(contentRoot.getGenTestDirPaths(), file) ||
        containsFile(contentRoot.getTestResourceDirPaths(), file)) {
      return true;
    }
    return false;
  }

  private static boolean containsFile(@NotNull Collection<File> folderPaths, @NotNull File file) {
    for (File path : folderPaths) {
      if (isAncestor(path, file, false)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
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

  public boolean isAndroidModuleWithoutVariants() {
    return myAndroidModuleWithoutVariants;
  }

  @Nullable
  public LanguageLevel getJavaLanguageLevel() {
    if (myLanguageLevel != null) {
      return LanguageLevel.parse(myLanguageLevel);
    }
    return null;
  }

  @NotNull
  public List<String> getConfigurations() {
    return myConfigurations;
  }

  public static boolean isBuildable(@NotNull GradleProject gradleProject) {
    for (GradleTask task : gradleProject.getTasks()) {
      if (COMPILE_JAVA_TASK_NAME.equals(task.getName())) {
        return true;
      }
    }
    return false;
  }
}
