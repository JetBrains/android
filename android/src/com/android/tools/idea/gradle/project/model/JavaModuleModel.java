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

import static com.intellij.openapi.util.io.FileUtil.isAncestor;

import com.android.builder.model.SyncIssue;
import com.android.ide.common.gradle.model.IdeSyncIssue;
import com.android.tools.idea.gradle.model.java.JarLibraryDependency;
import com.android.tools.idea.gradle.model.java.JavaModuleContentRoot;
import com.android.tools.idea.gradle.model.java.JavaModuleDependency;
import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.intellij.openapi.module.Module;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.serialization.PropertyMapping;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExtIdeaCompilerOutput;

/**
 * Base model for Java library modules
 */
public class JavaModuleModel implements ModuleModel {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 4L;

  @NotNull private final String myModuleName;
  @NotNull private final Collection<JavaModuleContentRoot> myContentRoots;
  @NotNull private final Collection<JavaModuleDependency> myJavaModuleDependencies;
  @NotNull private final Collection<JarLibraryDependency> myJarLibraryDependencies;
  @NotNull private final Collection<SyncIssue> mySyncIssues;
  @NotNull private final Map<String, Set<File>> myArtifactsByConfiguration;
  @NotNull private final List<String> myConfigurations;

  @Nullable private final ExtIdeaCompilerOutput myCompilerOutput;
  @Nullable private final File myBuildFolderPath;
  @Nullable private final String myLanguageLevel;

  private final boolean myBuildable;

  @Nullable
  public static JavaModuleModel get(@NotNull Module module) {
    JavaFacet facet = JavaFacet.getInstance(module);
    return facet != null ? get(facet) : null;
  }

  @Nullable
  public static JavaModuleModel get(@NotNull JavaFacet javaFacet) {
    return javaFacet.getJavaModuleModel();
  }

  public static JavaModuleModel create(@NotNull String moduleName,
                                       @NotNull Collection<JavaModuleContentRoot> contentRoots,
                                       @NotNull Collection<JavaModuleDependency> javaModuleDependencies,
                                       @NotNull Collection<JarLibraryDependency> jarLibraryDependencies,
                                       @NotNull Map<String, Set<File>> artifactsByConfiguration,
                                       @NotNull Collection<SyncIssue> syncIssues,
                                       @Nullable ExtIdeaCompilerOutput compilerOutput,
                                       @Nullable File buildFolderPath,
                                       @Nullable String languageLevel,
                                       boolean buildable) {
    Collection<SyncIssue> syncIssuesCopy = ContainerUtil.map(syncIssues, issue -> new IdeSyncIssue(issue));
    List<String> configurationsCopy = new ArrayList<>(artifactsByConfiguration.keySet());
    Collections.sort(configurationsCopy);

    return new JavaModuleModel(moduleName, contentRoots, javaModuleDependencies, jarLibraryDependencies, artifactsByConfiguration,
                               syncIssuesCopy, configurationsCopy, compilerOutput, buildFolderPath, languageLevel, buildable);
  }

  @PropertyMapping({
    "myModuleName",
    "myContentRoots",
    "myJavaModuleDependencies",
    "myJarLibraryDependencies",
    "myArtifactsByConfiguration",
    "mySyncIssues",
    "myConfigurations",
    "myCompilerOutput",
    "myBuildFolderPath",
    "myLanguageLevel",
    "myBuildable"})
  public JavaModuleModel(@NotNull String moduleName,
                         @NotNull Collection<JavaModuleContentRoot> contentRoots,
                         @NotNull Collection<JavaModuleDependency> javaModuleDependencies,
                         @NotNull Collection<JarLibraryDependency> jarLibraryDependencies,
                         @NotNull Map<String, Set<File>> artifactsByConfiguration,
                         @NotNull Collection<SyncIssue> syncIssues,
                         @NotNull List<String> configurations,
                         @Nullable ExtIdeaCompilerOutput compilerOutput,
                         @Nullable File buildFolderPath,
                         @Nullable String languageLevel,
                         boolean buildable) {
    myModuleName = moduleName;
    myContentRoots = contentRoots;
    myJavaModuleDependencies = javaModuleDependencies;
    myJarLibraryDependencies = jarLibraryDependencies;
    myArtifactsByConfiguration = artifactsByConfiguration;
    mySyncIssues = syncIssues;
    myConfigurations = configurations;
    myCompilerOutput = compilerOutput;
    myBuildFolderPath = buildFolderPath;
    myLanguageLevel = languageLevel;
    myBuildable = buildable;
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

  @NotNull
  public Collection<SyncIssue> getSyncIssues() {
    return mySyncIssues;
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
}
