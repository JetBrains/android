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

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.util.containers.ContainerUtil;
import java.util.List;
import org.jetbrains.annotations.NotNull;
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
    ModuleDependency compileDependency = new ModuleDependency(myModule);
    myDependencies.add(compileDependency);

    ModuleDependency testDependency = new ModuleDependency(myModule);
    myDependencies.add(testDependency);

    Collection<ModuleDependency> all = myDependencies.onModules();
    assertEquals(1, all.size());
    assertSame(compileDependency, ContainerUtil.getFirstItem(all));
  }

  @Test
  public void addModuleWithExistingDependencyWithWiderScope() {
    ModuleDependency testDependency = new ModuleDependency(myModule);
    myDependencies.add(testDependency);

    ModuleDependency compileDependency = new ModuleDependency(myModule);
    myDependencies.add(compileDependency);

    Collection<ModuleDependency> moduleDependencies = myDependencies.onModules();
    assertThat(moduleDependencies).hasSize(1);
    assertThat(moduleDependencies).containsExactly(compileDependency);
  }

  @Test
  public void addLibrary() {
    File dependency1Path = new File("file1.jar");
    LibraryDependency dependency1 =
      new LibraryDependency(dependency1Path, ImmutableList.of(dependency1Path));
    myDependencies.add(dependency1);

    File dependency2Path = new File("file2.jar");
    LibraryDependency dependency2 = new LibraryDependency(dependency2Path, ImmutableList.of(dependency2Path));
    myDependencies.add(dependency2);

    LibraryDependency dependency3 = new LibraryDependency(dependency2Path, ImmutableList.of(dependency2Path));
    myDependencies.add(dependency3);

    Collection<LibraryDependency> libraryDependencies = myDependencies.onLibraries();
    assertThat(libraryDependencies).hasSize(2);
    assertThat(libraryDependencies).containsAllOf(dependency1, dependency3);
  }

  @Test
  public void addLibraryWithExistingDependencyWithNarrowerScope() {
    File dependencyPath = new File("asm-4.0.jar");
    LibraryDependency compileDependency = new LibraryDependency(dependencyPath, ImmutableList.of(dependencyPath));
    myDependencies.add(compileDependency);

    LibraryDependency testDependency = new LibraryDependency(dependencyPath, ImmutableList.of(dependencyPath));
    myDependencies.add(testDependency);

    Collection<LibraryDependency> libraryDependencies = myDependencies.onLibraries();
    assertThat(libraryDependencies).hasSize(1);
    assertThat(libraryDependencies).containsExactly(compileDependency);
  }

  @Test
  public void addLibraryWithExistingDependencyWithWiderScope() {
    File dependencyPath = new File("asm-4.0.jar");
    LibraryDependency testDependency = new LibraryDependency(dependencyPath, ImmutableList.of(dependencyPath));
    myDependencies.add(testDependency);

    LibraryDependency compileDependency = new LibraryDependency(dependencyPath, ImmutableList.of(dependencyPath));
    myDependencies.add(compileDependency);

    Collection<LibraryDependency> libraryDependencies = myDependencies.onLibraries();
    assertThat(libraryDependencies).hasSize(1);
    assertThat(libraryDependencies).containsExactly(compileDependency);
  }

  @Test
  public void addLibraryWithExistingDependency() {
    File dependencyPath = new File("asm-4.0.jar");
    LibraryDependency dependency1 = new LibraryDependency(dependencyPath, ImmutableList.of(dependencyPath));
    myDependencies.add(dependency1);

    LibraryDependency dependency2 = new LibraryDependency(dependencyPath, ImmutableList.of(dependencyPath));
    myDependencies.add(dependency2);

    Collection<LibraryDependency> libraryDependencies = myDependencies.onLibraries();
    assertThat(libraryDependencies).hasSize(1);
    assertThat(libraryDependencies).containsExactly(dependency1);
  }

  @Test
  public void addLibrariesWithSameNameButDifferentArtifacts() {
    File dependency1Path = new File("file1.jar");
    LibraryDependency dependency1 = new LibraryDependency(dependency1Path, ImmutableList.of(dependency1Path));
    myDependencies.add(dependency1);

    File dependency2Path = new File("file2.jar");
    LibraryDependency dependency2 = new LibraryDependency(dependency2Path, ImmutableList.of(dependency2Path));
    myDependencies.add(dependency2);

    Collection<LibraryDependency> all = myDependencies.onLibraries();
    assertEquals(2, all.size());
    assertTrue(all.contains(dependency1));
    assertTrue(all.contains(dependency2));
  }

  @Test
  public void onLibrariesMaintainInsertionOrder_1() {
    addDependency("file1.jar");
    addDependency("file4.jar");
    addDependency("file2.jar");
    addDependency("file3.jar");

    List<File> dependencyNames = ContainerUtil.map(myDependencies.onLibraries(), LibraryDependency::getArtifactPath);
    assertThat(dependencyNames).hasSize(4);
    assertThat(dependencyNames)
      .containsExactly(new File("file1.jar"), new File("file4.jar"), new File("file2.jar"), new File("file3.jar")).inOrder();
  }

  @Test
  public void onLibrariesMaintainInsertionOrder_2() {
    addDependency("file_c.jar");
    addDependency("file_d.jar");
    addDependency("file_a.jar");
    addDependency("file_b.jar");

    List<File> dependencyNames = ContainerUtil.map(myDependencies.onLibraries(), LibraryDependency::getArtifactPath);
    assertThat(dependencyNames).hasSize(4);
    assertThat(dependencyNames)
      .containsExactly(new File("file_c.jar"), new File("file_d.jar"), new File("file_a.jar"), new File("file_b.jar")).inOrder();
  }

  private void addDependency(@NotNull String binaryPath) {
    File binaryFile = new File(binaryPath);
    LibraryDependency dependency = new LibraryDependency(binaryFile, ImmutableList.of(binaryFile));
    myDependencies.add(dependency);
  }
}
