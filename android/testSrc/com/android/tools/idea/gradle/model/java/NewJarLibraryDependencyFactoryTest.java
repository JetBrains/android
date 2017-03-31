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

import com.android.java.model.JavaLibrary;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link NewJarLibraryDependencyFactory}.
 */
public class NewJarLibraryDependencyFactoryTest {
  private JavaLibrary myOriginalDependency;
  private NewJarLibraryDependencyFactory myNewJarLibraryDependencyFactory;

  @Before
  public void setUp() {
    myOriginalDependency = mock(JavaLibrary.class);
    myNewJarLibraryDependencyFactory = new NewJarLibraryDependencyFactory();
  }

  @Test
  public void testCopyWithNullBinaryPath() {
    when(myOriginalDependency.getJarFile()).thenReturn(null);
    assertNull(myNewJarLibraryDependencyFactory.create(myOriginalDependency, "compile"));
  }

  @Test
  public void testCopyWithResolvedDependency() {
    FileStub binaryPath = new FileStub("fake.jar", true);
    FileStub sourcePath = new FileStub("fake-src.jar", true);
    FileStub javadocPath = new FileStub("fake-javadoc.jar", true);

    String scope = "compile";

    when(myOriginalDependency.getJarFile()).thenReturn(binaryPath);
    when(myOriginalDependency.getSource()).thenReturn(sourcePath);
    when(myOriginalDependency.getJavadoc()).thenReturn(javadocPath);

    JarLibraryDependency dependency = myNewJarLibraryDependencyFactory.create(myOriginalDependency, scope);
    assertNotNull(dependency);
    assertEquals("fake", dependency.getName());
    assertEquals("compile", dependency.getScope());
    assertSame(binaryPath, dependency.getBinaryPath());
    assertSame(sourcePath, dependency.getSourcePath());
    assertSame(javadocPath, dependency.getJavadocPath());
    assertNull(dependency.getModuleVersion());
    assertTrue(dependency.isResolved());
  }

  @Test
  public void testCopyWithUnresolvedDependency() {
    FileStub binaryPath = new FileStub("unresolved dependency - fake", true);

    when(myOriginalDependency.getJarFile()).thenReturn(binaryPath);
    when(myOriginalDependency.getSource()).thenReturn(null);
    when(myOriginalDependency.getJavadoc()).thenReturn(null);

    JarLibraryDependency dependency = myNewJarLibraryDependencyFactory.create(myOriginalDependency, null);
    assertNotNull(dependency);
    assertEquals("fake", dependency.getName());
    assertNull(dependency.getScope());
    assertSame(binaryPath, dependency.getBinaryPath());
    assertNull(dependency.getSourcePath());
    assertNull(dependency.getJavadocPath());
    assertNull(dependency.getModuleVersion());
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