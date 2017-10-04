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

import com.android.builder.model.AndroidProject;
import com.android.java.model.ArtifactModel;
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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExtIdeaCompilerOutput;
import org.jetbrains.plugins.gradle.tooling.internal.IdeaCompilerOutputImpl;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static com.android.tools.idea.gradle.project.model.JavaModuleModel.isBuildable;

/**
 * Factory class to create JavaModuleModel instance from JavaProject returned by Java Library plugin.
 */
public class JavaModuleModelFactory {
  @NotNull private static final String MAIN_SOURCE_SET_NAME = "main";
  @NotNull private static final String TEST_SOURCE_SET_NAME = "test";
  @NotNull private static final String COMPILE_SCOPE = "COMPILE";
  @NotNull private static final String TEST_SCOPE = "TEST";
  @NotNull private final NewJarLibraryDependencyFactory myNewJarLibraryDependencyFactory;

  public JavaModuleModelFactory() {
    myNewJarLibraryDependencyFactory = new NewJarLibraryDependencyFactory();
  }

  @NotNull
  public JavaModuleModel create(@NotNull File moduleFolderPath, @NotNull GradleProject gradleProject, @NotNull ArtifactModel jarAarModel) {
    Collection<JavaModuleContentRoot> contentRoots = getContentRoots(moduleFolderPath, gradleProject);
    return new JavaModuleModel(jarAarModel.getName(), contentRoots, Collections.emptyList() /* Java module dependencies */,
                               Collections.emptyList() /* Jar library dependencies */, jarAarModel.getArtifactsByConfiguration(),
                               null /* compiler output */, gradleProject.getBuildDirectory(), null /* Java language level */,
                               isBuildable(gradleProject), false /* regular Java module */);
  }

  @NotNull
  private static Collection<JavaModuleContentRoot> getContentRoots(@NotNull File moduleFolderPath, @NotNull GradleProject gradleProject) {
    // Exclude directory came from idea plugin, Java Library Plugin doesn't return this information.
    // Manually add build directory.
    Collection<File> excludeFolderPaths = Collections.singletonList(gradleProject.getBuildDirectory());
    JavaModuleContentRoot contentRoot = new JavaModuleContentRoot(moduleFolderPath,
                                                                  Collections.emptyList() /* source folders */,
                                                                  Collections.emptyList() /* generated source folders */,
                                                                  Collections.emptyList() /* resource folders */,
                                                                  Collections.emptyList() /* test folders */,
                                                                  Collections.emptyList() /* generated test folders */,
                                                                  Collections.emptyList() /* test resource folders */, excludeFolderPaths);
    return Collections.singleton(contentRoot);
  }

  @NotNull
  public JavaModuleModel create(@NotNull GradleProject gradleProject, @NotNull AndroidProject androidProject) {
    String sourceCompatibility = androidProject.getJavaCompileOptions().getSourceCompatibility();
    return new JavaModuleModel(androidProject.getName(), Collections.emptyList() /* content roots */,
                               Collections.emptyList() /* Java module dependencies */,
                               Collections.emptyList() /* Jar library dependencies */,
                               Collections.emptyMap() /* artifacts by configuration */, new IdeaCompilerOutputImpl(),
                               gradleProject.getBuildDirectory(), sourceCompatibility, false /* not buildable */,
                               true /* Android project without variants */);
  }

  @NotNull
  public JavaModuleModel create(@NotNull File moduleFolderPath, @NotNull GradleProject gradleProject, @NotNull JavaProject javaProject) {
    Pair<Collection<JavaModuleDependency>, Collection<JarLibraryDependency>> dependencies = getDependencies(javaProject);
    String projectName = javaProject.getName();
    Collection<JavaModuleContentRoot> contentRoots = getContentRoots(moduleFolderPath, javaProject, gradleProject);
    return new JavaModuleModel(projectName, contentRoots, dependencies.first, dependencies.second,
                               Collections.emptyMap() /* artifacts by configuration */, getCompilerOutput(javaProject),
                               gradleProject.getBuildDirectory(), javaProject.getJavaLanguageLevel(), isBuildable(gradleProject),
                               false /* regular Java module */);
  }

  @NotNull
  private Pair<Collection<JavaModuleDependency>, Collection<JarLibraryDependency>> getDependencies(@NotNull JavaProject javaProject) {
    Collection<JavaModuleDependency> javaModuleDependencies = new ArrayList<>();
    Collection<JarLibraryDependency> jarLibraryDependencies = new ArrayList<>();

    SourceSets sourceSets = new SourceSets(javaProject);

    // Java plugin creates "main" and "test" SourceSets by default.
    // Android projects without variants do not contain any SourceSets.
    SourceSet mainSourceSet = sourceSets.getMainSourceSet();
    SourceSet testSourceSet = sourceSets.getTestSourceSet();
    if (mainSourceSet != null && testSourceSet != null) {
      Collection<JavaLibrary> dependenciesForMain = mainSourceSet.getCompileClasspathDependencies();
      Collection<JavaLibrary> dependenciesForTest = testSourceSet.getCompileClasspathDependencies();
      Set<JavaLibrary> dependenciesForMainInSet = ImmutableSet.copyOf(dependenciesForMain);

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
    SourceSets sourceSets = new SourceSets(javaProject);

    SourceSet mainSourceSet = sourceSets.getMainSourceSet();
    SourceSet testSourceSet = sourceSets.getTestSourceSet();
    if (mainSourceSet != null && testSourceSet != null) {
      compilerOutput.setMainClassesDir(mainSourceSet.getClassesOutputDirectory());
      compilerOutput.setMainResourcesDir(mainSourceSet.getResourcesOutputDirectory());

      compilerOutput.setTestClassesDir(testSourceSet.getClassesOutputDirectory());
      compilerOutput.setTestResourcesDir(testSourceSet.getResourcesOutputDirectory());
    }
    return compilerOutput;
  }

  @NotNull
  private static Collection<JavaModuleContentRoot> getContentRoots(@NotNull File moduleFolderPath,
                                                                   @NotNull JavaProject javaProject,
                                                                   @NotNull GradleProject gradleProject) {
    Collection<File> sourceFolderPaths = new ArrayList<>();
    Collection<File> resourceFolderPaths = new ArrayList<>();
    Collection<File> testFolderPaths = new ArrayList<>();
    Collection<File> testResourceFolderPaths = new ArrayList<>();

    for (SourceSet sourceSet : javaProject.getSourceSets()) {
      if (sourceSet.getName().equals(TEST_SOURCE_SET_NAME)) {
        testFolderPaths.addAll(sourceSet.getSourceDirectories());
        testResourceFolderPaths.addAll(sourceSet.getResourcesDirectories());
      }
      else {
        // All sourceSets excepts for test.
        sourceFolderPaths.addAll(sourceSet.getSourceDirectories());
        resourceFolderPaths.addAll(sourceSet.getResourcesDirectories());
      }
    }

    // Exclude directory came from idea plugin, Java Library Plugin doesn't return this information.
    // Manually add build directory.
    Collection<File> excludeFolderPaths = new ArrayList<>();
    excludeFolderPaths.add(gradleProject.getBuildDirectory());

    // Generated sources and generated test sources come from idea plugin, leave them empty for now.
    JavaModuleContentRoot contentRoot = new JavaModuleContentRoot(moduleFolderPath, sourceFolderPaths,
                                                                  Collections.emptyList() /* generated source folders */,
                                                                  resourceFolderPaths, testFolderPaths,
                                                                  Collections.emptyList() /* test generated source folders */,
                                                                  testResourceFolderPaths, excludeFolderPaths);
    return Collections.singleton(contentRoot);
  }

  private static class SourceSets {
    @NotNull private final Map<String, SourceSet> mySourceSetByName;

    SourceSets(@NotNull JavaProject javaProject) {
      mySourceSetByName = javaProject.getSourceSets().stream().collect(Collectors.toMap(SourceSet::getName, SourceSet -> SourceSet));
    }

    @Nullable
    SourceSet getMainSourceSet() {
      return mySourceSetByName.get(MAIN_SOURCE_SET_NAME);
    }

    @Nullable
    SourceSet getTestSourceSet() {
      return mySourceSetByName.get(TEST_SOURCE_SET_NAME);
    }
  }
}
