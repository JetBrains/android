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
 * Tests for {@link LibraryDependency}.
 */
public class LibraryDependencyTest extends TestCase {
  public void testConstructorWithJar() {
    File jarFile = new File("~/repo/guava/guava-11.0.2.jar");
    LibraryDependency dependency = new LibraryDependency(jarFile, DependencyScope.TEST);
    assertEquals("guava-11.0.2", dependency.getName());
    Collection<String> binaryPaths = dependency.getPaths(LibraryDependency.PathType.BINARY);
    assertEquals(1, binaryPaths.size());
    assertEquals(jarFile.getPath(), ContainerUtil.getFirstItem(binaryPaths));
    assertEquals(DependencyScope.TEST, dependency.getScope());
  }
}
