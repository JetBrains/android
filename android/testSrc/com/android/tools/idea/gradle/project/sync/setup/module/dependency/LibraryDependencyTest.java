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

import com.intellij.openapi.roots.DependencyScope;
import com.intellij.testFramework.IdeaTestCase;

import java.io.File;
import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link LibraryDependency}.
 */
public class LibraryDependencyTest extends IdeaTestCase {
  public void testConstructorWithJar() {
    File jarFile = new File("~/repo/guava/guava-11.0.2.jar");
    LibraryDependency dependency = new LibraryDependency(jarFile, DependencyScope.TEST);
<<<<<<< HEAD
    assertEquals("guava-11.0.2", dependency.getName());
    File[] binaryPaths = dependency.getBinaryPaths();
=======
    assertEquals("Gradle: guava-11.0.2", dependency.getName());
    File[] binaryPaths = dependency.getPaths(LibraryDependency.PathType.BINARY);
>>>>>>> goog/upstream-ij17
    assertThat(binaryPaths).hasLength(1);
    assertEquals(jarFile, binaryPaths[0]);
    assertEquals(DependencyScope.TEST, dependency.getScope());
  }
}
