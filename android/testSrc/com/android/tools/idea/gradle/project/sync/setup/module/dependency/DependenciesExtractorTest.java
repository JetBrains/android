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
package com.android.tools.idea.gradle.project.sync.setup.module.dependency;

import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.stubs.android.AndroidLibraryStub;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.android.VariantStub;
import com.google.common.collect.Lists;
import com.intellij.testFramework.IdeaTestCase;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static com.android.tools.idea.gradle.project.sync.setup.module.dependency.LibraryDependency.PathType.BINARY;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

/**
 * Tests for {@link DependenciesExtractor}.
 */
public class DependenciesExtractorTest extends IdeaTestCase {
  private AndroidProjectStub myAndroidProject;
  private VariantStub myVariant;

  private DependenciesExtractor myDependenciesExtractor;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myAndroidProject = TestProjects.createBasicProject();
    myVariant = myAndroidProject.getFirstVariant();
    assertNotNull(myVariant);

    myDependenciesExtractor = new DependenciesExtractor();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myAndroidProject != null) {
        myAndroidProject.dispose();
      }
    }
    finally {
      super.tearDown();
    }
  }

  public void testExtractFromWithJar() {
    File jarFile = new File("~/repo/guava/guava-11.0.2.jar");

    myVariant.getMainArtifact().getDependencies().addJar(jarFile);
    myVariant.getInstrumentTestArtifact().getDependencies().addJar(jarFile);

    Collection<LibraryDependency> dependencies = myDependenciesExtractor.extractFrom(myVariant).onLibraries();
    assertThat(dependencies).hasSize(1);

    LibraryDependency dependency = getFirstItem(dependencies);
    assertNotNull(dependency);
    assertEquals("guava-11.0.2", dependency.getName());
    // Make sure that is a "compile" dependency, even if specified as "test".
    assertEquals(COMPILE, dependency.getScope());

    File[] binaryPaths = dependency.getPaths(BINARY);
    assertThat(binaryPaths).hasLength(1);
    assertEquals(jarFile, binaryPaths[0]);
  }

  public void testExtractFromWithLibraryProject() {
    String rootDirPath = myAndroidProject.getRootDir().getPath();
    File bundle = new File(rootDirPath, "bundle.aar");
    File libJar = new File(rootDirPath, join("bundle_aar", "library.jar"));
    File resFolder = new File(rootDirPath, join("bundle_aar", "res"));
    String gradlePath = "abc:xyz:library";
    AndroidLibraryStub library = new AndroidLibraryStub(bundle, libJar, gradlePath);

    myVariant.getMainArtifact().getDependencies().addLibrary(library);
    myVariant.getInstrumentTestArtifact().getDependencies().addLibrary(library);

    Collection<ModuleDependency> dependencies = myDependenciesExtractor.extractFrom(myVariant).onModules();
    assertThat(dependencies).hasSize(1);

    ModuleDependency dependency = getFirstItem(dependencies);
    assertNotNull(dependency);
    assertEquals(gradlePath, dependency.getGradlePath());
    // Make sure that is a "compile" dependency, even if specified as "test".
    assertEquals(COMPILE, dependency.getScope());

    LibraryDependency backup = dependency.getBackupDependency();
    assertNotNull(backup);
    assertEquals("bundle.aar-1.0", backup.getName());
    assertEquals(COMPILE, backup.getScope());

    File[] backupBinaryPaths = backup.getPaths(BINARY);
    assertThat(backupBinaryPaths).hasLength(2);
    assertThat(backupBinaryPaths).asList().containsAllOf(libJar, resFolder);
  }

  public void testExtractFromWithLibraryAar() {
    String rootDirPath = myAndroidProject.getRootDir().getPath();
    File bundle = new File(rootDirPath, "bundle.aar");
    File libJar = new File(rootDirPath, join("bundle_aar", "library.jar"));
    AndroidLibraryStub library = new AndroidLibraryStub(bundle, libJar);

    myVariant.getMainArtifact().getDependencies().addLibrary(library);
    myVariant.getInstrumentTestArtifact().getDependencies().addLibrary(library);

    Collection<LibraryDependency> dependencies = myDependenciesExtractor.extractFrom(myVariant).onLibraries();
    assertThat(dependencies).hasSize(1);

    LibraryDependency dependency = getFirstItem(dependencies);
    assertNotNull(dependency);
    assertEquals("bundle.aar-1.0", dependency.getName());
    // Make sure that is a "compile" dependency, even if specified as "test".
    assertEquals(COMPILE, dependency.getScope());

    File[] binaryPaths = dependency.getPaths(BINARY);
    assertThat(binaryPaths).hasLength(2);
    assertThat(binaryPaths).asList().contains(libJar);
  }

  public void testExtractFromWithLibraryLocalJar() {
    String rootDirPath = myAndroidProject.getRootDir().getPath();
    File bundle = new File(rootDirPath, "bundle.aar");
    File libJar = new File(rootDirPath, join("bundle_aar", "library.jar"));
    File resFolder = new File(rootDirPath, join("bundle_aar", "res"));
    AndroidLibraryStub library = new AndroidLibraryStub(bundle, libJar);

    File localJar = new File(rootDirPath, "local.jar");
    library.addLocalJar(localJar);

    myVariant.getMainArtifact().getDependencies().addLibrary(library);
    myVariant.getInstrumentTestArtifact().getDependencies().addLibrary(library);

    List<LibraryDependency> dependencies = Lists.newArrayList(myDependenciesExtractor.extractFrom(myVariant).onLibraries());
    assertThat(dependencies).hasSize(1);

    LibraryDependency dependency = dependencies.get(0);
    assertNotNull(dependency);
    assertEquals("bundle.aar-1.0", dependency.getName());

    File[] binaryPaths = dependency.getPaths(BINARY);
    assertThat(binaryPaths).hasLength(3);
    assertThat(binaryPaths).asList().containsAllOf(localJar, libJar, resFolder);
  }

  public void testExtractFromWithProject() {
    String gradlePath = "abc:xyz:library";
    myVariant.getMainArtifact().getDependencies().addProject(gradlePath);
    myVariant.getInstrumentTestArtifact().getDependencies().addProject(gradlePath);
    Collection<ModuleDependency> dependencies = myDependenciesExtractor.extractFrom(myVariant).onModules();
    assertThat(dependencies).hasSize(1);

    ModuleDependency dependency = getFirstItem(dependencies);
    assertNotNull(dependency);
    assertEquals(gradlePath, dependency.getGradlePath());
    // Make sure that is a "compile" dependency, even if specified as "test".
    assertEquals(COMPILE, dependency.getScope());

    LibraryDependency backup = dependency.getBackupDependency();
    assertNull(backup);
  }

}
