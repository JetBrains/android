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

import com.intellij.openapi.roots.DependencyScope;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;

import java.io.File;
import java.util.Collection;

/**
 * Tests for {@link DependencySet}.
 */
public class DependencySetTest extends TestCase {
  private DependencySet myDependencies;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myDependencies = new DependencySet();
  }

  public void testAddModuleWithExistingDependencyWithNarrowerScope() {
    ModuleDependency compileDependency = new ModuleDependency(":lib", DependencyScope.COMPILE);
    myDependencies.add(compileDependency);

    ModuleDependency testDependency = new ModuleDependency(":lib", DependencyScope.TEST);
    myDependencies.add(testDependency);

    Collection<ModuleDependency> all = myDependencies.onModules();
    assertEquals(1, all.size());
    assertSame(compileDependency, ContainerUtil.getFirstItem(all));
  }

  public void testAddModuleWithExistingDependencyWithWiderScope() {
    ModuleDependency testDependency = new ModuleDependency(":lib", DependencyScope.TEST);
    myDependencies.add(testDependency);

    ModuleDependency compileDependency = new ModuleDependency(":lib", DependencyScope.COMPILE);
    myDependencies.add(compileDependency);

    Collection<ModuleDependency> all = myDependencies.onModules();
    assertEquals(1, all.size());
    assertSame(compileDependency, ContainerUtil.getFirstItem(all));
  }

  public void testAddLibrary() {
    LibraryDependency dependency1 = new LibraryDependency("library-1.0.1.jar", DependencyScope.COMPILE);
    dependency1.addPath(LibraryDependency.PathType.BINARY, new File("file1.jar"));
    myDependencies.add(dependency1);

    LibraryDependency dependency2 = new LibraryDependency("library-1.0.1.jar", DependencyScope.TEST);
    dependency2.addPath(LibraryDependency.PathType.BINARY, new File("file2.jar"));
    myDependencies.add(dependency2);

    LibraryDependency dependency3 = new LibraryDependency("library-1.0.1.jar", DependencyScope.COMPILE);
    dependency3.addPath(LibraryDependency.PathType.BINARY, new File("file2.jar"));
    myDependencies.add(dependency3);

    Collection<LibraryDependency> all = myDependencies.onLibraries();
    assertEquals(2, all.size());
    assertTrue(all.contains(dependency1));
    assertTrue(all.contains(dependency3));

    assertFalse(dependency1.getName().equals(dependency3.getName()));
  }

  public void testAddLibraryWithExistingDependencyWithNarrowerScope() {
    LibraryDependency compileDependency = new LibraryDependency("asm-4.0.jar", DependencyScope.COMPILE);
    compileDependency.addPath(LibraryDependency.PathType.BINARY, new File("asm-4.0.jar"));
    myDependencies.add(compileDependency);

    LibraryDependency testDependency = new LibraryDependency("asm-4.0.jar", DependencyScope.TEST);
    testDependency.addPath(LibraryDependency.PathType.BINARY, new File("asm-4.0.jar"));
    myDependencies.add(testDependency);

    Collection<LibraryDependency> all = myDependencies.onLibraries();
    assertEquals(1, all.size());
    assertSame(compileDependency, ContainerUtil.getFirstItem(all));
  }

  public void testAddLibraryWithExistingDependencyWithWiderScope() {
    LibraryDependency testDependency = new LibraryDependency("asm-4.0.jar", DependencyScope.TEST);
    testDependency.addPath(LibraryDependency.PathType.BINARY, new File("asm-4.0.jar"));
    myDependencies.add(testDependency);

    LibraryDependency compileDependency = new LibraryDependency("asm-4.0.jar", DependencyScope.COMPILE);
    compileDependency.addPath(LibraryDependency.PathType.BINARY, new File("asm-4.0.jar"));
    myDependencies.add(compileDependency);

    Collection<LibraryDependency> all = myDependencies.onLibraries();
    assertEquals(1, all.size());
    assertSame(compileDependency, ContainerUtil.getFirstItem(all));
  }

  public void testAddLibraryWithExistingDependency() {
    LibraryDependency dependency1 = new LibraryDependency("asm-4.0.jar", DependencyScope.COMPILE);
    dependency1.addPath(LibraryDependency.PathType.BINARY, new File("asm-4.0.jar"));
    myDependencies.add(dependency1);

    LibraryDependency dependency2 = new LibraryDependency("asm-4.0.jar", DependencyScope.COMPILE);
    dependency2.addPath(LibraryDependency.PathType.BINARY, new File("asm-4.0.jar"));
    myDependencies.add(dependency2);

    Collection<LibraryDependency> all = myDependencies.onLibraries();
    assertEquals(1, all.size());
    assertSame(dependency1, ContainerUtil.getFirstItem(all));
  }

  public void testAddLibrariesWithSameNameButDifferentArtifacts() {
    LibraryDependency dependency1 = new LibraryDependency("library-1.0.1.jar", DependencyScope.COMPILE);
    dependency1.addPath(LibraryDependency.PathType.BINARY, new File("file1.jar"));
    myDependencies.add(dependency1);

    LibraryDependency dependency2 = new LibraryDependency("library-1.0.1.jar", DependencyScope.COMPILE);
    dependency2.addPath(LibraryDependency.PathType.BINARY, new File("file2.jar"));
    myDependencies.add(dependency2);

    Collection<LibraryDependency> all = myDependencies.onLibraries();
    assertEquals(2, all.size());
    assertTrue(all.contains(dependency1));
    assertTrue(all.contains(dependency2));
  }
}
