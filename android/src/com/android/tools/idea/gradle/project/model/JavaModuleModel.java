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
import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.model.idea.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExtIdeaCompilerOutput;
import org.jetbrains.plugins.gradle.model.ModuleExtendedModel;

import java.io.File;
import java.io.Serializable;
import java.util.*;

import static com.android.tools.idea.gradle.project.facet.java.JavaFacet.COMPILE_JAVA_TASK_NAME;
import static com.intellij.openapi.util.io.FileUtil.isAncestor;

public class JavaModuleModel implements Serializable {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  @NotNull private String myModuleName;
  @NotNull private Collection<JavaModuleContentRoot> myContentRoots = new ArrayList<>();
  @NotNull private Collection<JavaModuleDependency> myJavaModuleDependencies = new ArrayList<>();
  @NotNull private Collection<JarLibraryDependency> myJarLibraryDependencies = new ArrayList<>();
  @NotNull private Map<String, Set<File>> myArtifactsByConfiguration;
  @NotNull private List<String> myConfigurations;

  @Nullable private ExtIdeaCompilerOutput myCompilerOutput;
  @Nullable private File myBuildFolderPath;
  @Nullable private String myLanguageLevel;

  private boolean myBuildable;
  private boolean myAndroidModuleWithoutVariants;

  public JavaModuleModel(@NotNull IdeaModule ideaModule, @Nullable ModuleExtendedModel javaModel, boolean androidModuleWithoutVariants) {
    this(ideaModule.getName(), getContentRoots(ideaModule, javaModel), getDependencies(ideaModule),
         getArtifactsByConfiguration(javaModel), getCompilerOutput(javaModel), ideaModule.getGradleProject().getBuildDirectory(),
         getLanguageLevel(javaModel), !androidModuleWithoutVariants && isBuildable(ideaModule), androidModuleWithoutVariants);
  }

  @Nullable
  private static ExtIdeaCompilerOutput getCompilerOutput(@Nullable ModuleExtendedModel javaModel) {
    return javaModel != null ? javaModel.getCompilerOutput() : null;
  }

  @NotNull
  private static Collection<? extends IdeaContentRoot> getContentRoots(@NotNull IdeaModule ideaModule,
                                                                       @Nullable ModuleExtendedModel javaModel) {
    Collection<? extends IdeaContentRoot> contentRoots = javaModel != null ? javaModel.getContentRoots() : null;
    if (contentRoots == null) {
      contentRoots = ideaModule.getContentRoots();
    }
    return contentRoots != null ? contentRoots : Collections.emptyList();
  }

  @NotNull
  private static Map<String, Set<File>> getArtifactsByConfiguration(@Nullable ModuleExtendedModel javaModel) {
    Map<String, Set<File>> artifactsByConfiguration = Collections.emptyMap();
    if (javaModel != null) {
      artifactsByConfiguration = javaModel.getArtifactsByConfiguration();
    }
    return artifactsByConfiguration;
  }

  @NotNull
  private static List<? extends IdeaDependency> getDependencies(@NotNull IdeaModule ideaModule) {
    List<? extends IdeaDependency> dependencies = ideaModule.getDependencies().getAll();
    if (dependencies != null) {
      return dependencies;
    }
    return Collections.emptyList();
  }

  private static boolean isBuildable(@NotNull IdeaModule ideaModule) {
    for (GradleTask task : ideaModule.getGradleProject().getTasks()) {
      if (COMPILE_JAVA_TASK_NAME.equals(task.getName())) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static String getLanguageLevel(@Nullable ModuleExtendedModel javaModel) {
    return javaModel != null ? javaModel.getJavaSourceCompatibility() : null;
  }

  public JavaModuleModel(@NotNull String name,
                         @NotNull Collection<? extends IdeaContentRoot> contentRoots,
                         @NotNull List<? extends IdeaDependency> dependencies,
                         @Nullable Map<String, Set<File>> artifactsByConfiguration,
                         @Nullable ExtIdeaCompilerOutput compilerOutput,
                         @Nullable File buildFolderPath,
                         @Nullable String languageLevel,
                         boolean buildable,
                         boolean androidModuleWithoutVariants) {
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

    myArtifactsByConfiguration = artifactsByConfiguration != null ? artifactsByConfiguration : Collections.emptyMap();

    myConfigurations = new ArrayList<>(myArtifactsByConfiguration.keySet());
    Collections.sort(myConfigurations);

    myCompilerOutput = compilerOutput;
    myBuildFolderPath = buildFolderPath;
    myLanguageLevel = languageLevel;
    myBuildable = buildable;
    myAndroidModuleWithoutVariants = androidModuleWithoutVariants;
  }

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
}
