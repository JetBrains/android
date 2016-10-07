/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.model.java;

import org.gradle.tooling.model.GradleModuleVersion;
import org.gradle.tooling.model.idea.IdeaDependencyScope;
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link JarLibraryDependency}.
 */
public class JarLibraryDependencyTest {
  private IdeaSingleEntryLibraryDependency myOriginalDependency;

  @Before
  public void setUp() {
    myOriginalDependency = mock(IdeaSingleEntryLibraryDependency.class);
  }

  @Test
  public void testCopyWithNullBinaryPath() {
    when(myOriginalDependency.getFile()).thenReturn(null);
    assertNull(JarLibraryDependency.copy(myOriginalDependency));
  }

  @Test
  public void testCopyWithResolvedDependency() {
    FileStub binaryPath = new FileStub("fake.jar", true);
    FileStub sourcePath = new FileStub("fake-src.jar", true);
    FileStub javadocPath = new FileStub("fake-javadoc.jar", true);

    IdeaDependencyScope scope = mock(IdeaDependencyScope.class);
    GradleModuleVersion moduleVersion = mock(GradleModuleVersion.class);

    when(myOriginalDependency.getFile()).thenReturn(binaryPath);
    when(myOriginalDependency.getSource()).thenReturn(sourcePath);
    when(myOriginalDependency.getJavadoc()).thenReturn(javadocPath);
    when(myOriginalDependency.getScope()).thenReturn(scope);
    when(myOriginalDependency.getGradleModuleVersion()).thenReturn(moduleVersion);
    when(scope.getScope()).thenReturn("compile");

    JarLibraryDependency dependency = JarLibraryDependency.copy(myOriginalDependency);
    assertNotNull(dependency);
    assertEquals("fake", dependency.getName());
    assertEquals("compile", dependency.getScope());
    assertSame(binaryPath, dependency.getBinaryPath());
    assertSame(sourcePath, dependency.getSourcePath());
    assertSame(javadocPath, dependency.getJavadocPath());
    assertSame(moduleVersion, dependency.getModuleVersion());
    assertTrue(dependency.isResolved());
  }

  @Test
  public void testCopyWithUnesolvedDependency() {
    FileStub binaryPath = new FileStub("unresolved dependency - fake", true);
    GradleModuleVersion moduleVersion = mock(GradleModuleVersion.class);

    when(myOriginalDependency.getFile()).thenReturn(binaryPath);
    when(myOriginalDependency.getSource()).thenReturn(null);
    when(myOriginalDependency.getJavadoc()).thenReturn(null);
    when(myOriginalDependency.getScope()).thenReturn(null);
    when(myOriginalDependency.getGradleModuleVersion()).thenReturn(moduleVersion);

    JarLibraryDependency dependency = JarLibraryDependency.copy(myOriginalDependency);
    assertNotNull(dependency);
    assertEquals("fake", dependency.getName());
    assertNull(dependency.getScope());
    assertSame(binaryPath, dependency.getBinaryPath());
    assertNull(dependency.getSourcePath());
    assertNull(dependency.getJavadocPath());
    assertSame(moduleVersion, dependency.getModuleVersion());
    assertFalse(dependency.isResolved());
  }

  private static class FileStub extends File {
    private final boolean myIsFile;

    FileStub(@NotNull String pathname, boolean isFile) {
      super(pathname);
      myIsFile = isFile;
    }

    @Override
    public boolean isFile() {
      return myIsFile;
    }
  }
}