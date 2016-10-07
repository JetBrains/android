/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.customizer.dependency;

import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.stubs.android.AndroidLibraryStub;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.android.VariantStub;
import com.google.common.collect.Lists;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;

/**
 * Tests for {@link Dependency#extractFrom(AndroidGradleModel)}.
 */
public class ExtractAndroidDependenciesTest extends IdeaTestCase {
  private AndroidGradleModel myAndroidModel;
  private AndroidProjectStub myAndroidProject;
  private VariantStub myVariant;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myAndroidProject = TestProjects.createBasicProject();
    myVariant = myAndroidProject.getFirstVariant();
    assertNotNull(myVariant);

    File rootDir = myAndroidProject.getRootDir();
    myAndroidModel = new AndroidGradleModel(GradleConstants.SYSTEM_ID, myAndroidProject.getName(), rootDir, myAndroidProject,
                                            myVariant.getName(), ARTIFACT_ANDROID_TEST);
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

    Collection<LibraryDependency> dependencies = Dependency.extractFrom(myAndroidModel).onLibraries();
    assertEquals(1, dependencies.size());

    LibraryDependency dependency = ContainerUtil.getFirstItem(dependencies);
    assertNotNull(dependency);
    assertEquals("guava-11.0.2", dependency.getName());
    // Make sure that is a "compile" dependency, even if specified as "test".
    assertEquals(DependencyScope.COMPILE, dependency.getScope());

    Collection<String> binaryPaths = dependency.getPaths(LibraryDependency.PathType.BINARY);
    assertEquals(1, binaryPaths.size());
    assertEquals(jarFile.getPath(), ContainerUtil.getFirstItem(binaryPaths));
  }

  public void testExtractFromWithLibraryProject() {
    String rootDirPath = myAndroidProject.getRootDir().getPath();
    File bundle = new File(rootDirPath, "bundle.aar");
    File libJar = new File(rootDirPath, FileUtil.join("bundle_aar", "library.jar"));
    File resFolder = new File(rootDirPath, FileUtil.join("bundle_aar", "res"));
    String gradlePath = "abc:xyz:library";
    AndroidLibraryStub library = new AndroidLibraryStub(bundle, libJar, gradlePath);

    myVariant.getMainArtifact().getDependencies().addLibrary(library);
    myVariant.getInstrumentTestArtifact().getDependencies().addLibrary(library);

    Collection<ModuleDependency> dependencies = Dependency.extractFrom(myAndroidModel).onModules();
    assertEquals(1, dependencies.size());

    ModuleDependency dependency = ContainerUtil.getFirstItem(dependencies);
    assertNotNull(dependency);
    assertEquals(gradlePath, dependency.getGradlePath());
    // Make sure that is a "compile" dependency, even if specified as "test".
    assertEquals(DependencyScope.COMPILE, dependency.getScope());

    LibraryDependency backup = dependency.getBackupDependency();
    assertNotNull(backup);
    assertEquals("bundle", backup.getName());
    assertEquals(DependencyScope.COMPILE, backup.getScope());

    Collection<String> backupBinaryPaths = backup.getPaths(LibraryDependency.PathType.BINARY);
    assertEquals(2, backupBinaryPaths.size());
    assertTrue(backupBinaryPaths.contains(libJar.getPath()));
    assertTrue(backupBinaryPaths.contains(resFolder.getPath()));
  }

  public void testExtractFromWithLibraryAar() {
    String rootDirPath = myAndroidProject.getRootDir().getPath();
    File bundle = new File(rootDirPath, "bundle.aar");
    File libJar = new File(rootDirPath, FileUtil.join("bundle_aar", "library.jar"));
    AndroidLibraryStub library = new AndroidLibraryStub(bundle, libJar);

    myVariant.getMainArtifact().getDependencies().addLibrary(library);
    myVariant.getInstrumentTestArtifact().getDependencies().addLibrary(library);

    Collection<LibraryDependency> dependencies = Dependency.extractFrom(myAndroidModel).onLibraries();
    assertEquals(1, dependencies.size());

    LibraryDependency dependency = ContainerUtil.getFirstItem(dependencies);
    assertNotNull(dependency);
    assertEquals("bundle", dependency.getName());
    // Make sure that is a "compile" dependency, even if specified as "test".
    assertEquals(DependencyScope.COMPILE, dependency.getScope());

    Collection<String> binaryPaths = dependency.getPaths(LibraryDependency.PathType.BINARY);
    assertEquals(2, binaryPaths.size());
    assertTrue(binaryPaths.contains(libJar.getPath()));
  }

  public void testExtractFromWithLibraryLocalJar() {
    String rootDirPath = myAndroidProject.getRootDir().getPath();
    File bundle = new File(rootDirPath, "bundle.aar");
    File libJar = new File(rootDirPath, FileUtil.join("bundle_aar", "library.jar"));
    File resFolder = new File(rootDirPath, FileUtil.join("bundle_aar", "res"));
    AndroidLibraryStub library = new AndroidLibraryStub(bundle, libJar);

    File localJar = new File(rootDirPath, "local.jar");
    library.addLocalJar(localJar);

    myVariant.getMainArtifact().getDependencies().addLibrary(library);
    myVariant.getInstrumentTestArtifact().getDependencies().addLibrary(library);

    List<LibraryDependency> dependencies = Lists.newArrayList(Dependency.extractFrom(myAndroidModel).onLibraries());
    assertEquals(1, dependencies.size());

    LibraryDependency dependency = dependencies.get(0);
    assertNotNull(dependency);
    assertEquals("bundle", dependency.getName());

    Collection<String> binaryPaths = dependency.getPaths(LibraryDependency.PathType.BINARY);
    assertEquals(3, binaryPaths.size());
    assertTrue(binaryPaths.contains(localJar.getPath()));
    assertTrue(binaryPaths.contains(libJar.getPath()));
    assertTrue(binaryPaths.contains(resFolder.getPath()));
  }

  public void testExtractFromWithProject() {
    String gradlePath = "abc:xyz:library";
    myVariant.getMainArtifact().getDependencies().addProject(gradlePath);
    myVariant.getInstrumentTestArtifact().getDependencies().addProject(gradlePath);
    Collection<ModuleDependency> dependencies = Dependency.extractFrom(myAndroidModel).onModules();
    assertEquals(1, dependencies.size());

    ModuleDependency dependency = ContainerUtil.getFirstItem(dependencies);
    assertNotNull(dependency);
    assertEquals(gradlePath, dependency.getGradlePath());
    // Make sure that is a "compile" dependency, even if specified as "test".
    assertEquals(DependencyScope.COMPILE, dependency.getScope());

    LibraryDependency backup = dependency.getBackupDependency();
    assertNull(backup);
  }

}
