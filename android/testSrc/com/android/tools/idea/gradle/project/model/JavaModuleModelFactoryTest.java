/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.java.model.JavaLibrary;
import com.android.java.model.JavaProject;
import com.android.java.model.LibraryVersion;
import com.android.java.model.SourceSet;
import com.android.tools.idea.gradle.model.java.JarLibraryDependency;
import com.android.tools.idea.gradle.model.java.JavaModuleContentRoot;
import com.android.tools.idea.gradle.model.java.JavaModuleDependency;
import com.google.common.collect.Iterables;
import com.intellij.util.lang.JavaVersion;
import org.gradle.tooling.model.GradleModuleVersion;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link JavaModuleModelFactory}.
 */
public class JavaModuleModelFactoryTest {
  @Mock private JavaProject myJavaProject;
  @Mock private GradleProject myGradleProject;

  private String myJavaLanguageLevel;
  private String myModuleName;
  private File myBuildFolderPath;
  private File myProjectFolderPath;

  private JavaModuleModelFactory myJavaModuleModelFactory;

  @Before
  public void setUpMethod() {
    myJavaLanguageLevel = "1.7";
    myBuildFolderPath = new File("/mock/project/build");
    myProjectFolderPath = new File("/mock/project");
    myModuleName = "javaLib";

    initMocks(this);
    when(myJavaProject.getName()).thenReturn(myModuleName);
    when(myJavaProject.getJavaLanguageLevel()).thenReturn(myJavaLanguageLevel);

    when(myGradleProject.getBuildDirectory()).thenReturn(myBuildFolderPath);
    when(myGradleProject.getProjectDirectory()).thenReturn(myProjectFolderPath);
    doReturn(ImmutableDomainObjectSet.of(Collections.emptyList())).when(myGradleProject).getTasks();

    myJavaModuleModelFactory = new JavaModuleModelFactory();
  }

  @Test
  public void verifyBasicProperties() {
    JavaModuleModel javaModuleModel = myJavaModuleModelFactory.create(myProjectFolderPath, myGradleProject, myJavaProject);
    assertEquals(myModuleName, javaModuleModel.getModuleName());
    assertFalse(javaModuleModel.isAndroidModuleWithoutVariants());
    assertEquals(myBuildFolderPath, javaModuleModel.getBuildFolderPath());
    assertNotNull(javaModuleModel.getJavaLanguageLevel());
    assertEquals(JavaVersion.tryParse(myJavaLanguageLevel), javaModuleModel.getJavaLanguageLevel().toJavaVersion());
    assertThat(javaModuleModel.getContentRoots()).hasSize(1);
    // Verify that build directory and .gradle are excluded.
    JavaModuleContentRoot contentRoot = javaModuleModel.getContentRoots().iterator().next();
    assertThat(contentRoot.getExcludeDirPaths()).containsExactly(myBuildFolderPath, new File(myProjectFolderPath, ".gradle"));
  }

  @Test
  public void verifyDependencies() {
    // Simulate the case when java module has one project dependency, and multiple jar dependencies
    // The java module contains two sourceSets, main and test
    // main compile configuration has one module dependency and one jar dependency
    // testCompile configuration has one direct jar dependency and one inherited dependency from compile
    File compileJarFile = new File("/mock/compile.jar");
    File testJarFile = new File("/mock/test.jar");

    JavaLibrary moduleLibrary = mock(JavaLibrary.class);
    JavaLibrary compileJarLibrary = mock(JavaLibrary.class);
    JavaLibrary testJarLibrary = mock(JavaLibrary.class);
    LibraryVersion libraryVersion = mock(LibraryVersion.class);

    when(moduleLibrary.getName()).thenReturn("lib2");
    when(moduleLibrary.getProject()).thenReturn(":lib2");
    when(moduleLibrary.getBuildId()).thenReturn("/mock/project");

    when(compileJarLibrary.getJarFile()).thenReturn(compileJarFile);
    when(compileJarLibrary.getLibraryVersion()).thenReturn(libraryVersion);
    when(libraryVersion.getGroup()).thenReturn("com.google.example");
    when(libraryVersion.getName()).thenReturn("MyApplication");
    when(libraryVersion.getVersion()).thenReturn("1.0");

    when(testJarLibrary.getJarFile()).thenReturn(testJarFile);

    SourceSet mainSourceSet = mock(SourceSet.class);
    when(mainSourceSet.getName()).thenReturn("main");
    when(mainSourceSet.getCompileClasspathDependencies()).thenReturn(Arrays.asList(compileJarLibrary, moduleLibrary));

    SourceSet testSourceSet = mock(SourceSet.class);
    when(testSourceSet.getName()).thenReturn("test");
    when(testSourceSet.getCompileClasspathDependencies()).thenReturn(Arrays.asList(compileJarLibrary, testJarLibrary));

    when(myJavaProject.getSourceSets()).thenReturn(Arrays.asList(mainSourceSet, testSourceSet));

    JavaModuleModel javaModuleModel = myJavaModuleModelFactory.create(myProjectFolderPath, myGradleProject, myJavaProject);

    // Verify project dependency
    Collection<JavaModuleDependency> javaModuleDependencies = javaModuleModel.getJavaModuleDependencies();
    assertThat(javaModuleDependencies).hasSize(1);
    JavaModuleDependency javaModuleDependency = Iterables.get(javaModuleDependencies, 0);
    assertEquals("lib2", javaModuleDependency.getModuleName());
    assertEquals("/mock/project::lib2", javaModuleDependency.getModuleId());
    assertEquals("COMPILE", javaModuleDependency.getScope());

    // Verify jar dependency
    Collection<JarLibraryDependency> jarLibraryDependencies = javaModuleModel.getJarLibraryDependencies();
    assertThat(jarLibraryDependencies).hasSize(2);

    for (JarLibraryDependency jarLibraryDependency : jarLibraryDependencies) {
      assertNotNull(jarLibraryDependency.getScope());
      // Jar dependency from main sourceSet
      if (jarLibraryDependency.getScope().equals("COMPILE")) {
        assertEquals(compileJarFile, jarLibraryDependency.getBinaryPath());
        GradleModuleVersion version = jarLibraryDependency.getModuleVersion();
        assertNotNull(version);
        assertEquals("com.google.example", version.getGroup());
        assertEquals("MyApplication", version.getName());
        assertEquals("1.0", version.getVersion());
      }
      else {
        // Jar dependency from test sourceSet
        assertEquals("TEST", jarLibraryDependency.getScope());
        assertEquals(testJarFile, jarLibraryDependency.getBinaryPath());
      }
    }
  }
}
