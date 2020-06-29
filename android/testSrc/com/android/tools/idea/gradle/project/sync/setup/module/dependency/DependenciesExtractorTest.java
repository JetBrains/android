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

import static com.android.tools.idea.gradle.project.sync.setup.module.dependency.DependenciesExtractor.getDependencyDisplayName;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

import com.android.ide.common.gradle.model.level2.IdeAndroidLibrary;
import com.android.ide.common.gradle.model.level2.IdeJavaLibrary;
import com.android.ide.common.gradle.model.level2.IdeJavaLibraryCore;
import com.android.ide.common.gradle.model.level2.IdeLibrary;
import com.android.ide.common.gradle.model.level2.IdeModuleLibrary;
import com.android.ide.common.gradle.model.stubs.AndroidLibraryStubBuilder;
import com.android.ide.common.gradle.model.stubs.ModuleLibraryStubBuilder;
import com.android.ide.common.gradle.model.stubs.level2.IdeDependenciesStubBuilder;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.sync.setup.module.ModuleFinder;
import com.android.tools.idea.testing.Facets;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.PlatformTestCase;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

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
    IdeLibrary javaLibrary = new IdeJavaLibrary(
      new IdeJavaLibraryCore(
        "guava", jarFile
      ), false
    );

    IdeDependenciesStubBuilder builder = new IdeDependenciesStubBuilder();
    builder.setJavaLibraries(ImmutableList.of(javaLibrary));

    Collection<LibraryDependency> dependencies =
      myDependenciesExtractor.extractFrom(getProjectBasePath(), builder.build(), COMPILE, myModuleFinder).onLibraries();
    assertThat(dependencies).hasSize(1);

    LibraryDependency dependency = getFirstItem(dependencies);
    assertNotNull(dependency);
    assertEquals("Gradle: guava", dependency.getName());
    // Make sure that is a "compile" dependency, even if specified as "test".
    assertEquals(COMPILE, dependency.getScope());

    File[] binaryPaths = dependency.getBinaryPaths();
    assertThat(binaryPaths).hasLength(1);
    assertEquals(jarFile, binaryPaths[0]);
  }

  public void testExtractFromAndroidLibraryWithLocalJar() {
    String rootDirPath = myProject.getBasePath();
    File libJar = new File(rootDirPath, join("bundle_aar", "androidLibrary.jar"));
    File libCompileJar = new File(rootDirPath, join("api.jar"));

    File resFolder = new File(rootDirPath, join("bundle_aar", "res"));
    File localJar = new File(rootDirPath, "local.jar");

    AndroidLibraryStubBuilder builder = new AndroidLibraryStubBuilder();
    builder.setArtifactAddress("com.android.support:support-core-ui:25.3.1@aar");
    builder.setJarFile(libJar.getPath());
    builder.setCompileJarFile(libCompileJar.getPath());
    builder.setResFolder(resFolder.getPath());
    builder.setLocalJars(Collections.singletonList(localJar.getPath()));
    IdeAndroidLibrary library = builder.build();

    IdeDependenciesStubBuilder dependenciesStubBuilder = new IdeDependenciesStubBuilder();
    dependenciesStubBuilder.setAndroidLibraries(ImmutableList.of(library));

    DependencySet dependencySet = myDependenciesExtractor.extractFrom(getProjectBasePath(),
                                                                      dependenciesStubBuilder.build(),
                                                                      COMPILE,
                                                                      myModuleFinder
    );
    List<LibraryDependency> dependencies = new ArrayList<>(dependencySet.onLibraries());
    assertThat(dependencies).hasSize(1);

    LibraryDependency dependency = dependencies.get(0);
    assertNotNull(dependency);
    assertEquals("Gradle: com.android.support:support-core-ui:25.3.1@aar", dependency.getName());

    File[] binaryPaths = dependency.getBinaryPaths();
    assertThat(binaryPaths).hasLength(3);
    assertThat(binaryPaths).asList().containsAllOf(localJar, libCompileJar, resFolder);
  }

  public void testExtractFromModuleDependency() {
    Module libModule = createModule("lib");
    GradleFacet gradleFacet = Facets.createAndAddGradleFacet(libModule);
    String gradlePath = ":lib";
    gradleFacet.getConfiguration().GRADLE_PROJECT_PATH = gradlePath;

    ModuleLibraryStubBuilder builder = new ModuleLibraryStubBuilder(gradlePath);
    IdeModuleLibrary library = builder.build();

    myModuleFinder = new ModuleFinder(myProject);
    myModuleFinder.addModule(libModule, ":lib");

    IdeDependenciesStubBuilder dependenciesStubBuilder = new IdeDependenciesStubBuilder();
    dependenciesStubBuilder.setModuleDependencies(ImmutableList.of(library));
    Collection<ModuleDependency> dependencies =
      myDependenciesExtractor.extractFrom(getProjectBasePath(), dependenciesStubBuilder.build(), COMPILE, myModuleFinder).onModules();
    assertThat(dependencies).hasSize(1);

    ModuleDependency dependency = getFirstItem(dependencies);
    assertNotNull(dependency);
    assertEquals(libModule, dependency.getModule());
    // Make sure that is a "compile" dependency, even if specified as "test".
    assertEquals(COMPILE, dependency.getScope());
    assertSame(libModule, dependency.getModule());
  }

  public void testGetDependencyDisplayName() {
    IdeJavaLibrary library1 = new IdeJavaLibrary(
      new IdeJavaLibraryCore(
        "com.google.guava:guava:11.0.2@jar", new File("")
      ),
      false);
    assertThat(getDependencyDisplayName(library1)).isEqualTo("guava:11.0.2");

    IdeJavaLibrary library2 = new IdeJavaLibrary(
      new IdeJavaLibraryCore(
        "android.arch.lifecycle:extensions:1.0.0-beta1@aar", new File("")
      ), false);
    assertThat(getDependencyDisplayName(library2)).isEqualTo("lifecycle:extensions:1.0.0-beta1");

    IdeJavaLibrary library3 = new IdeJavaLibrary(
      new IdeJavaLibraryCore(
        "com.android.support.test.espresso:espresso-core:3.0.1@aar", new File("")
      ), false);
    assertThat(getDependencyDisplayName(library3)).isEqualTo("espresso-core:3.0.1");

    IdeJavaLibrary library4 = new IdeJavaLibrary(
      new IdeJavaLibraryCore(
        "foo:bar:1.0", new File("")
      ), false);
    assertThat(getDependencyDisplayName(library4)).isEqualTo("foo:bar:1.0");
  }

  @NotNull
  private File getProjectBasePath() {
    return new File(getProject().getBasePath());
  }
}
