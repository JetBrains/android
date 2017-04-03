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

import com.android.annotations.VisibleForTesting;
import com.android.java.model.JavaLibrary;
import com.android.java.model.JavaProject;
import com.android.java.model.SourceSet;
import com.android.tools.idea.gradle.model.java.JarLibraryDependency;
import com.android.tools.idea.gradle.model.java.JavaModuleContentRoot;
import com.android.tools.idea.gradle.model.java.JavaModuleDependency;
import com.android.tools.idea.gradle.model.java.NewJarLibraryDependencyFactory;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.util.Pair;
import org.gradle.tooling.model.GradleProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ExtIdeaCompilerOutput;
import org.jetbrains.plugins.gradle.tooling.internal.IdeaCompilerOutputImpl;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import static com.android.tools.idea.gradle.project.model.JavaModuleModel.isBuildable;

/**
 * Factory class to create JavaModuleModel instance from JavaProject returned by Java Library plugin.
 */
public class NewJavaModuleModelFactory {
  @NotNull private static final String MAIN_SOURCE_SET_NAME = "main";
  @NotNull private static final String TEST_SOURCE_SET_NAME = "test";
  @NotNull private static final String COMPILE_SCOPE = "COMPILE";
  @NotNull private static final String TEST_SCOPE = "TEST";
  @NotNull private final NewJarLibraryDependencyFactory myNewJarLibraryDependencyFactory;

  public NewJavaModuleModelFactory() {
    this(new NewJarLibraryDependencyFactory());
  }

  @VisibleForTesting
  NewJavaModuleModelFactory(@NotNull NewJarLibraryDependencyFactory newJarLibraryDependencyFactory) {
    myNewJarLibraryDependencyFactory = newJarLibraryDependencyFactory;
  }

  @NotNull
  public JavaModuleModel create(@NotNull GradleProject gradleProject,
                                @NotNull JavaProject javaProject,
                                boolean androidModuleWithoutVariants) {
    Pair<Collection<JavaModuleDependency>, Collection<JarLibraryDependency>> dependencies = getDependencies(javaProject);
    return new JavaModuleModel(javaProject.getName(), getContentRoots(javaProject, gradleProject), dependencies.first, dependencies.second,
                               Collections.emptyMap(), getCompilerOutput(javaProject),
                               gradleProject.getBuildDirectory(), javaProject.getJavaLanguageLevel(),
                               !androidModuleWithoutVariants && isBuildable(gradleProject), androidModuleWithoutVariants);
  }

  @NotNull
  private Pair<Collection<JavaModuleDependency>, Collection<JarLibraryDependency>> getDependencies(@NotNull JavaProject javaProject) {
    Collection<JavaModuleDependency> javaModuleDependencies = new ArrayList<>();
    Collection<JarLibraryDependency> jarLibraryDependencies = new ArrayList<>();
    Map<String, SourceSet> sourceSetByName =
      javaProject.getSourceSets().stream().collect(Collectors.toMap(SourceSet::getName, SourceSet -> SourceSet));
    // java plugin creates "main" and "test" sourceSets by default
    if (sourceSetByName.containsKey(MAIN_SOURCE_SET_NAME) && sourceSetByName.containsKey(TEST_SOURCE_SET_NAME)) {
      Collection<JavaLibrary> dependenciesForMain = sourceSetByName.get(MAIN_SOURCE_SET_NAME).getCompileClasspathDependencies();
      Collection<JavaLibrary> dependenciesForTest = sourceSetByName.get(TEST_SOURCE_SET_NAME).getCompileClasspathDependencies();
      ImmutableSet<JavaLibrary> dependenciesForMainInSet = ImmutableSet.copyOf(dependenciesForMain);

      // Set scope based on sourceSet name, dependencies for main is COMPILE_SCOPE,
      // and dependencies for test is TEST_SCOPE.
      // Please note, dependencies for test contains transitive dependencies from main compileClasspath,
      // we only set unique dependencies to TEST_SCOPE.
      for (JavaLibrary library : dependenciesForMain) {
        createDependency(library, COMPILE_SCOPE, javaModuleDependencies, jarLibraryDependencies);
      }
      for (JavaLibrary library : dependenciesForTest) {
        if (!dependenciesForMainInSet.contains(library)) {
          createDependency(library, TEST_SCOPE, javaModuleDependencies, jarLibraryDependencies);
        }
      }
    }
    return Pair.create(javaModuleDependencies, jarLibraryDependencies);
  }

  private void createDependency(@NotNull JavaLibrary javaLibrary,
                                @NotNull String scope,
                                @NotNull Collection<JavaModuleDependency> javaModuleDependencies,
                                @NotNull Collection<JarLibraryDependency> jarLibraryDependencies) {
    if (javaLibrary.getProject() != null) {
      javaModuleDependencies.add(new JavaModuleDependency(javaLibrary.getName(), scope, false));
    }
    else {
      JarLibraryDependency jarLibraryDependency = myNewJarLibraryDependencyFactory.create(javaLibrary, scope);
      if (jarLibraryDependency != null) {
        jarLibraryDependencies.add(jarLibraryDependency);
      }
    }
  }

  @NotNull
  private static ExtIdeaCompilerOutput getCompilerOutput(@NotNull JavaProject javaProject) {
    IdeaCompilerOutputImpl compilerOutput = new IdeaCompilerOutputImpl();
    Map<String, SourceSet> sourceSetByName =
      javaProject.getSourceSets().stream().collect(Collectors.toMap(SourceSet::getName, SourceSet -> SourceSet));

    if (sourceSetByName.containsKey(MAIN_SOURCE_SET_NAME) && sourceSetByName.containsKey(TEST_SOURCE_SET_NAME)) {
      compilerOutput.setMainClassesDir(sourceSetByName.get(MAIN_SOURCE_SET_NAME).getClassesOutputDirectory());
      compilerOutput.setMainResourcesDir(sourceSetByName.get(MAIN_SOURCE_SET_NAME).getResourcesOutputDirectory());
      compilerOutput.setTestClassesDir(sourceSetByName.get(TEST_SOURCE_SET_NAME).getClassesOutputDirectory());
      compilerOutput.setTestResourcesDir(sourceSetByName.get(TEST_SOURCE_SET_NAME).getResourcesOutputDirectory());
    }
    return compilerOutput;
  }

  @NotNull
  private static Collection<JavaModuleContentRoot> getContentRoots(@NotNull JavaProject javaProject, @NotNull GradleProject gradleProject) {
    Collection<File> sourceDirPaths = new ArrayList<>();
    Collection<File> resourceDirPaths = new ArrayList<>();
    Collection<File> testDirPaths = new ArrayList<>();
    Collection<File> testResourceDirPaths = new ArrayList<>();

    for (SourceSet sourceSet : javaProject.getSourceSets()) {
      if (sourceSet.getName().equals(TEST_SOURCE_SET_NAME)) {
        testDirPaths.addAll(sourceSet.getSourceDirectories());
        testResourceDirPaths.addAll(sourceSet.getResourcesDirectories());
      }
      else {
        // All sourceSets excepts for test.
        sourceDirPaths.addAll(sourceSet.getSourceDirectories());
        resourceDirPaths.addAll(sourceSet.getResourcesDirectories());
      }
    }

    // Exclude directory came from idea plugin, Java Library Plugin doesn't return this information.
    // Manually add build directory.
    Collection<File> excludeDirPaths = new ArrayList<>();
    excludeDirPaths.add(gradleProject.getBuildDirectory());

    // Generated sources and generated test sources come from idea plugin, leave them empty for now.
    return Collections.singleton(
      new JavaModuleContentRoot(gradleProject.getProjectDirectory(), sourceDirPaths, Collections.emptyList(), resourceDirPaths,
                                testDirPaths,
                                Collections.emptyList(), testResourceDirPaths, excludeDirPaths));
  }
}
