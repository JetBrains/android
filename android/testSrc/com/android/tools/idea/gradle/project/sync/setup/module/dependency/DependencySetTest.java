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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.util.containers.ContainerUtil;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;
import java.util.Collection;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link DependencySet}.
 */
public class DependencySetTest {
  @Mock private Module myModule;

  private DependencySet myDependencies;

  @Before
  public void setUp() throws Exception {
    initMocks(this);
    myDependencies = new DependencySet();
  }

  @Test
  public void addModuleWithExistingDependencyWithNarrowerScope() {
    ModuleDependency compileDependency = new ModuleDependency(":lib", DependencyScope.COMPILE, myModule);
    myDependencies.add(compileDependency);

    ModuleDependency testDependency = new ModuleDependency(":lib", DependencyScope.TEST, myModule);
    myDependencies.add(testDependency);

    Collection<ModuleDependency> all = myDependencies.onModules();
    assertEquals(1, all.size());
    assertSame(compileDependency, ContainerUtil.getFirstItem(all));
  }

  @Test
  public void addModuleWithExistingDependencyWithWiderScope() {
    ModuleDependency testDependency = new ModuleDependency(":lib", DependencyScope.TEST, myModule);
    myDependencies.add(testDependency);

    ModuleDependency compileDependency = new ModuleDependency(":lib", DependencyScope.COMPILE, myModule);
    myDependencies.add(compileDependency);

    Collection<ModuleDependency> moduleDependencies = myDependencies.onModules();
    assertThat(moduleDependencies).hasSize(1);
    assertThat(moduleDependencies).containsExactly(compileDependency);
  }

  @Test
  public void addLibrary() {
    File dependency1Path = new File("file1.jar");
    LibraryDependency dependency1 = new LibraryDependency(dependency1Path, "library-1.0.1.jar", DependencyScope.COMPILE);
    dependency1.addBinaryPath(dependency1Path);
    myDependencies.add(dependency1);

    File dependency2Path = new File("file2.jar");
    LibraryDependency dependency2 = new LibraryDependency(dependency2Path, "library-1.0.1.jar", DependencyScope.TEST);
    dependency2.addBinaryPath(dependency2Path);
    myDependencies.add(dependency2);

    LibraryDependency dependency3 = new LibraryDependency(dependency2Path, "library-1.0.1.jar", DependencyScope.COMPILE);
    dependency3.addBinaryPath(dependency2Path);
    myDependencies.add(dependency3);

    Collection<LibraryDependency> libraryDependencies = myDependencies.onLibraries();
    assertThat(libraryDependencies).hasSize(2);
    assertThat(libraryDependencies).containsAllOf(dependency1, dependency3);

    assertFalse(dependency1.getName().equals(dependency3.getName()));
  }

  @Test
  public void addLibraryWithExistingDependencyWithNarrowerScope() {
    File dependencyPath = new File("asm-4.0.jar");
    LibraryDependency compileDependency = new LibraryDependency(dependencyPath, "asm-4.0.jar", DependencyScope.COMPILE);
    compileDependency.addBinaryPath(dependencyPath);
    myDependencies.add(compileDependency);

    LibraryDependency testDependency = new LibraryDependency(dependencyPath, "asm-4.0.jar", DependencyScope.TEST);
    testDependency.addBinaryPath(dependencyPath);
    myDependencies.add(testDependency);

    Collection<LibraryDependency> libraryDependencies = myDependencies.onLibraries();
    assertThat(libraryDependencies).hasSize(1);
    assertThat(libraryDependencies).containsExactly(compileDependency);
  }

  @Test
  public void addLibraryWithExistingDependencyWithWiderScope() {
    File dependencyPath = new File("asm-4.0.jar");
    LibraryDependency testDependency = new LibraryDependency(dependencyPath, "asm-4.0.jar", DependencyScope.TEST);
    testDependency.addBinaryPath(dependencyPath);
    myDependencies.add(testDependency);

    LibraryDependency compileDependency = new LibraryDependency(dependencyPath, "asm-4.0.jar", DependencyScope.COMPILE);
    compileDependency.addBinaryPath(dependencyPath);
    myDependencies.add(compileDependency);

    Collection<LibraryDependency> libraryDependencies = myDependencies.onLibraries();
    assertThat(libraryDependencies).hasSize(1);
    assertThat(libraryDependencies).containsExactly(compileDependency);
  }

  @Test
  public void addLibraryWithExistingDependency() {
    File dependencyPath = new File("asm-4.0.jar");
    LibraryDependency dependency1 = new LibraryDependency(dependencyPath, "asm-4.0.jar", DependencyScope.COMPILE);
    dependency1.addBinaryPath(dependencyPath);
    myDependencies.add(dependency1);

    LibraryDependency dependency2 = new LibraryDependency(dependencyPath, "asm-4.0.jar", DependencyScope.COMPILE);
    dependency2.addBinaryPath(dependencyPath);
    myDependencies.add(dependency2);

    Collection<LibraryDependency> libraryDependencies = myDependencies.onLibraries();
    assertThat(libraryDependencies).hasSize(1);
    assertThat(libraryDependencies).containsExactly(dependency1);
  }

  @Test
  public void addLibrariesWithSameNameButDifferentArtifacts() {
    File dependency1Path = new File("file1.jar");
    LibraryDependency dependency1 = new LibraryDependency(dependency1Path, "library-1.0.1.jar", DependencyScope.COMPILE);
    dependency1.addBinaryPath(dependency1Path);
    myDependencies.add(dependency1);

    File dependency2Path = new File("file2.jar");
    LibraryDependency dependency2 = new LibraryDependency(dependency2Path, "library-1.0.1.jar", DependencyScope.COMPILE);
    dependency2.addBinaryPath(dependency2Path);
    myDependencies.add(dependency2);

    Collection<LibraryDependency> all = myDependencies.onLibraries();
    assertEquals(2, all.size());
    assertTrue(all.contains(dependency1));
    assertTrue(all.contains(dependency2));
  }
}
