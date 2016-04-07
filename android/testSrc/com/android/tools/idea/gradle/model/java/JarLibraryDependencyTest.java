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

import org.gradle.tooling.model.idea.IdeaDependencyScope;
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

/**
 * Tests for {@link JarLibraryDependency}.
 */
public class JarLibraryDependencyTest {
  private IdeaSingleEntryLibraryDependency myOriginalDependency;

  @Before
  public void setUp() {
    myOriginalDependency = createMock(IdeaSingleEntryLibraryDependency.class);
  }

  @Test
  public void testCopyWithNullBinaryPath() {
    expect(myOriginalDependency.getFile()).andStubReturn(null);
    replay(myOriginalDependency);
    assertNull(JarLibraryDependency.copy(myOriginalDependency));
    verify(myOriginalDependency);
  }

  @Test
  public void testCopyWithResolvedDependency() {
    FileStub binaryPath = new FileStub("fake.jar", true);
    FileStub sourcePath = new FileStub("fake-src.jar", true);
    FileStub javadocPath = new FileStub("fake-javadoc.jar", true);

    IdeaDependencyScope scope = createMock(IdeaDependencyScope.class);

    expect(myOriginalDependency.getFile()).andStubReturn(binaryPath);
    expect(myOriginalDependency.getSource()).andStubReturn(sourcePath);
    expect(myOriginalDependency.getJavadoc()).andStubReturn(javadocPath);
    expect(myOriginalDependency.getScope()).andStubReturn(scope);
    expect(scope.getScope()).andStubReturn("compile");
    replay(myOriginalDependency, scope);

    JarLibraryDependency dependency = JarLibraryDependency.copy(myOriginalDependency);
    assertNotNull(dependency);
    assertEquals("fake", dependency.getName());
    assertEquals("compile", dependency.getScope());
    assertSame(binaryPath, dependency.getBinaryPath());
    assertSame(sourcePath, dependency.getSourcePath());
    assertSame(javadocPath, dependency.getJavadocPath());
    assertTrue(dependency.isResolved());
  }

  @Test
  public void testCopyWithUnesolvedDependency() {
    FileStub binaryPath = new FileStub("unresolved dependency - fake", true);

    expect(myOriginalDependency.getFile()).andStubReturn(binaryPath);
    expect(myOriginalDependency.getSource()).andStubReturn(null);
    expect(myOriginalDependency.getJavadoc()).andStubReturn(null);
    expect(myOriginalDependency.getScope()).andStubReturn(null);
    replay(myOriginalDependency);

    JarLibraryDependency dependency = JarLibraryDependency.copy(myOriginalDependency);
    assertNotNull(dependency);
    assertEquals("fake", dependency.getName());
    assertNull(dependency.getScope());
    assertSame(binaryPath, dependency.getBinaryPath());
    assertNull(dependency.getSourcePath());
    assertNull(dependency.getJavadocPath());
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