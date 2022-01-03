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

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static java.util.Collections.emptyList;

import com.android.tools.idea.gradle.model.IdeAndroidLibraryDependency;
import com.android.tools.idea.gradle.model.IdeJavaLibraryDependency;
import com.android.tools.idea.gradle.model.IdeModuleSourceSet;
import com.android.tools.idea.gradle.model.impl.IdeAndroidLibraryDependencyImpl;
import com.android.tools.idea.gradle.model.impl.IdeAndroidLibraryImpl;
import com.android.tools.idea.gradle.model.impl.IdeDependenciesImpl;
import com.android.tools.idea.gradle.model.impl.IdeJavaLibraryCore;
import com.android.tools.idea.gradle.model.impl.IdeJavaLibraryDependencyImpl;
import com.android.tools.idea.gradle.model.impl.IdeJavaLibraryImpl;
import com.android.tools.idea.gradle.model.impl.IdeModuleDependencyImpl;
import com.android.tools.idea.gradle.model.impl.IdeModuleLibraryImpl;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.sync.setup.module.ModuleFinder;
import com.android.tools.idea.testing.Facets;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.PlatformTestCase;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Tests for {@link DependenciesExtractor}.
 */
public class DependenciesExtractorTest extends PlatformTestCase {
  private ModuleFinder myModuleFinder;
  private DependenciesExtractor myDependenciesExtractor;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myModuleFinder = new ModuleFinder(myProject);

    myDependenciesExtractor = new DependenciesExtractor();
  }

  public void testExtractFromJavaLibrary() {
    File jarFile = new File("~/repo/guava/guava-11.0.2.jar");
    IdeJavaLibraryDependency javaLibrary = new IdeJavaLibraryDependencyImpl(
      new IdeJavaLibraryImpl(
        new IdeJavaLibraryCore(
          "guava", jarFile
        ), "guava"
      ), false);

    Collection<LibraryDependency> dependencies = myDependenciesExtractor.extractFrom(
      new IdeDependenciesImpl(emptyList(), ImmutableList.of(javaLibrary), emptyList(), emptyList()),
      myModuleFinder).onLibraries();
    assertThat(dependencies).hasSize(1);

    LibraryDependency dependency = getFirstItem(dependencies);
    assertNotNull(dependency);
    assertEquals(jarFile, dependency.getArtifactPath());

    File[] binaryPaths = dependency.getBinaryPaths();
    assertThat(binaryPaths).hasLength(1);
    assertEquals(jarFile, binaryPaths[0]);
  }

  public void testExtractFromAndroidLibraryWithLocalJar() {
    String rootDirPath = myProject.getBasePath();
    File libJar = new File(rootDirPath, join("bundle_aar", "androidLibrary.jar"));
    File libAar = new File(rootDirPath, "bundle.aar");
    File libCompileJar = new File(rootDirPath, join("api.jar"));

    File resFolder = new File(rootDirPath, join("bundle_aar", "res"));
    File localJar = new File(rootDirPath, "local.jar");

    IdeAndroidLibraryDependency androidLibrary = new IdeAndroidLibraryDependencyImpl(
      new IdeAndroidLibraryImpl(
        "com.android.support:support-core-ui:25.3.1@aar",
        "com.android.support:support-core-ui:25.3.1@aar",
        new File("libraryFolder"),
        "manifest.xml",
        ImmutableList.of(libCompileJar.getPath(), localJar.getPath()),
        ImmutableList.of(libJar.getPath(), localJar.getPath()),
        resFolder.getPath(),
        new File("libraryFolder/res.apk"),
        "assets",
        "jni",
        "aidl",
        "renderscriptFolder",
        "proguardRules",
        "lint.jar",
        "externalAnnotations",
        "publicResources",
        libAar,
        "symbolFile"
      ),
      false);

    DependencySet dependencySet = myDependenciesExtractor.extractFrom(
      new IdeDependenciesImpl(
        ImmutableList.of(androidLibrary),
        ImmutableList.of(),
        ImmutableList.of(),
        ImmutableList.of()
      ),
      myModuleFinder
    );
    List<LibraryDependency> dependencies = new ArrayList<>(dependencySet.onLibraries());
    assertThat(dependencies).hasSize(1);

    LibraryDependency dependency = dependencies.get(0);
    assertNotNull(dependency);
    assertEquals(libAar, dependency.getArtifactPath());

    File[] binaryPaths = dependency.getBinaryPaths();
    assertThat(binaryPaths).hasLength(3);
    assertThat(binaryPaths).asList().containsAllOf(localJar, libCompileJar, resFolder);
  }

  public void testExtractFromModuleDependency() {
    Module libModule = createModule("lib");
    GradleFacet gradleFacet = Facets.createAndAddGradleFacet(libModule);
    String gradlePath = ":lib";
    gradleFacet.getConfiguration().GRADLE_PROJECT_PATH = gradlePath;

    IdeModuleDependencyImpl library = new IdeModuleDependencyImpl(new IdeModuleLibraryImpl(gradlePath, "/tmp"));

    myModuleFinder = new ModuleFinder(myProject);
    myModuleFinder.addModule(libModule, ":lib");

    Collection<ModuleDependency> dependencies = myDependenciesExtractor.extractFrom(
      new IdeDependenciesImpl(
        ImmutableList.of(),
        ImmutableList.of(),
        ImmutableList.of(library),
        ImmutableList.of()
      ),
      myModuleFinder).onModules();
    assertThat(dependencies).hasSize(1);

    ModuleDependency dependency = getFirstItem(dependencies);
    assertNotNull(dependency);
    assertSame(libModule, dependency.getModule());
  }
}
