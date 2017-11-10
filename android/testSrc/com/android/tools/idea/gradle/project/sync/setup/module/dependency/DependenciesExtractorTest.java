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

import com.android.builder.model.level2.Library;
import com.android.ide.common.gradle.model.stubs.level2.AndroidLibraryStub;
import com.android.ide.common.gradle.model.stubs.level2.JavaLibraryStub;
import com.android.ide.common.gradle.model.stubs.level2.ModuleLibraryStub;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.sync.setup.module.ModulesByGradlePath;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.android.VariantStub;
import com.android.tools.idea.testing.Facets;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.builder.model.level2.Library.LIBRARY_ANDROID;
import static com.android.builder.model.level2.Library.LIBRARY_JAVA;
import static com.android.tools.idea.gradle.project.sync.setup.module.dependency.DependenciesExtractor.getDependencyDisplayName;
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
  private ModulesByGradlePath myModulesByGradlePath;
  private DependenciesExtractor myDependenciesExtractor;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myModulesByGradlePath = new ModulesByGradlePath();

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

  public void testExtractFromJavaLibrary() {
    File jarFile = new File("~/repo/guava/guava-11.0.2.jar");
    Library javaLibrary = new JavaLibraryStub(LIBRARY_JAVA, "guava", jarFile);

    myVariant.getMainArtifact().getLevel2Dependencies().addJavaLibrary(javaLibrary);
    myVariant.getInstrumentTestArtifact().getLevel2Dependencies().addJavaLibrary(javaLibrary);

    Collection<LibraryDependency> dependencies = myDependenciesExtractor.extractFrom(myVariant, myModulesByGradlePath).onLibraries();
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
    String rootDirPath = myAndroidProject.getRootDir().getPath();
    File libJar = new File(rootDirPath, join("bundle_aar", "androidLibrary.jar"));
    File resFolder = new File(rootDirPath, join("bundle_aar", "res"));
    File localJar = new File(rootDirPath, "local.jar");

    AndroidLibraryStub library = new AndroidLibraryStub() {
      @Override
      @NotNull
      public String getJarFile() {
        return libJar.getPath();
      }

      @Override
      @NotNull
      public String getArtifactAddress() {
        return "com.android.support:support-core-ui:25.3.1@aar";
      }

      @Override
      @NotNull
      public String getResFolder() {
        return resFolder.getPath();
      }

      @Override
      @NotNull
      public Collection<String> getLocalJars() {
        return Collections.singletonList(localJar.getPath());
      }
    };

    myVariant.getMainArtifact().getLevel2Dependencies().addAndroidLibrary(library);
    myVariant.getInstrumentTestArtifact().getLevel2Dependencies().addAndroidLibrary(library);

    DependencySet dependencySet = myDependenciesExtractor.extractFrom(myVariant, myModulesByGradlePath);
    List<LibraryDependency> dependencies = new ArrayList<>(dependencySet.onLibraries());
    assertThat(dependencies).hasSize(1);

    LibraryDependency dependency = dependencies.get(0);
    assertNotNull(dependency);
    assertEquals("com.android.support:support-core-ui-25.3.1", dependency.getName());

    File[] binaryPaths = dependency.getBinaryPaths();
    assertThat(binaryPaths).hasLength(3);
    assertThat(binaryPaths).asList().containsAllOf(localJar, libJar, resFolder);
  }

  public void testExtractFromModuleDependency() {
    Module libModule = createModule("lib");
    GradleFacet gradleFacet = Facets.createAndAddGradleFacet(libModule);
    String gradlePath = ":lib";
    gradleFacet.getConfiguration().GRADLE_PROJECT_PATH = gradlePath;

    ModuleLibraryStub library = new ModuleLibraryStub() {
      @Override
      @NotNull
      public String getProjectPath() {
        return gradlePath;
      }
    };

    myModulesByGradlePath = new ModulesByGradlePath();
    myModulesByGradlePath.addModule(libModule, ":lib");

    myVariant.getMainArtifact().getLevel2Dependencies().addModuleDependency(library);
    myVariant.getInstrumentTestArtifact().getLevel2Dependencies().addModuleDependency(library);
    Collection<ModuleDependency> dependencies = myDependenciesExtractor.extractFrom(myVariant, myModulesByGradlePath).onModules();
    assertThat(dependencies).hasSize(1);

    ModuleDependency dependency = getFirstItem(dependencies);
    assertNotNull(dependency);
    assertEquals(gradlePath, dependency.getGradlePath());
    // Make sure that is a "compile" dependency, even if specified as "test".
    assertEquals(COMPILE, dependency.getScope());
    assertSame(libModule, dependency.getModule());

    LibraryDependency backup = dependency.getBackupDependency();
    assertNull(backup);
  }

  public void testGetDependencyDisplayName() {
    Library library1 = new JavaLibraryStub(LIBRARY_JAVA, "com.google.guava:guava:11.0.2@jar", new File(""));
    assertThat(getDependencyDisplayName(library1)).isEqualTo("guava:11.0.2");

    Library library2 = new JavaLibraryStub(LIBRARY_ANDROID, "android.arch.lifecycle:extensions:1.0.0-beta1@aar", new File(""));
    assertThat(getDependencyDisplayName(library2)).isEqualTo("lifecycle:extensions:1.0.0-beta1");

    Library library3 = new JavaLibraryStub(LIBRARY_ANDROID, "com.android.support.test.espresso:espresso-core:3.0.1@aar", new File(""));
    assertThat(getDependencyDisplayName(library3)).isEqualTo("espresso-core:3.0.1");

    Library library4 = new JavaLibraryStub(LIBRARY_JAVA, "foo:bar:1.0", new File(""));
    assertThat(getDependencyDisplayName(library4)).isEqualTo("foo:bar:1.0");
  }
}
