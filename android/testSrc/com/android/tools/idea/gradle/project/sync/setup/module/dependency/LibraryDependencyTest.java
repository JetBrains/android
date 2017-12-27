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
    assertEquals("Gradle: guava-11.0.2", dependency.getName());
    File[] binaryPaths = dependency.getPaths(LibraryDependency.PathType.BINARY);
    assertThat(binaryPaths).hasLength(1);
    assertEquals(jarFile, binaryPaths[0]);
    assertEquals(DependencyScope.TEST, dependency.getScope());
  }

  public void testConstructorWithAarAndJavadoc() throws IOException {
    // Simulate maven layout for LibraryFilePaths to perform the lookup (both aar and javadoc in the same folder)
    File aarFile = createTempFile("fakeAndroidLibrary-1.2.3.aar", "");
    File javadocFile = createTempFile("fakeAndroidLibrary-1.2.3-javadoc.jar", "");

    LibraryDependency dependency = new LibraryDependency(aarFile, "fakeAndroidLibraryName-1.2.3", DependencyScope.COMPILE);
    assertEquals("Gradle: fakeAndroidLibraryName-1.2.3", dependency.getName());
    File[] binaryPaths = dependency.getPaths(LibraryDependency.PathType.BINARY);
    // Binary paths for aar are populated by DependenciesExtractor.createLibraryDependencyFromAndroidLibrary
    assertEmpty(binaryPaths);
    File[] documentationPaths = dependency.getPaths(LibraryDependency.PathType.DOCUMENTATION);
    assertThat(documentationPaths).hasLength(1);
    assertEquals(javadocFile, documentationPaths[0]);
  }
}
