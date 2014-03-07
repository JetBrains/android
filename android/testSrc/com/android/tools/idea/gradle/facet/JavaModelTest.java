/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.facet;

import com.google.common.collect.Lists;
import junit.framework.TestCase;
import org.gradle.tooling.model.idea.IdeaContentRoot;
import org.gradle.tooling.model.idea.IdeaDependency;
import org.gradle.tooling.model.idea.IdeaModuleDependency;
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;

import java.io.File;
import java.util.List;

import static org.easymock.EasyMock.*;

/**
 * Tests for {@link JavaModel}.
 */
public class JavaModelTest extends TestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  public void testConstructor() {
    IdeaContentRoot contentRoot = createMock(IdeaContentRoot.class);
    List<? extends IdeaContentRoot> allContentRoots = Lists.newArrayList(contentRoot, null);

    IdeaModuleDependency moduleDependency = createMock(IdeaModuleDependency.class);

    IdeaSingleEntryLibraryDependency resolved = createMock(IdeaSingleEntryLibraryDependency.class);
    File resolvedFile = new File("guava.jar");
    expect(resolved.getFile()).andStubReturn(resolvedFile);

    IdeaSingleEntryLibraryDependency unresolved = createMock(IdeaSingleEntryLibraryDependency.class);
    File unresolvedFile = new File("unresolved dependency - commons-collections commons-collections 3.2");
    expect(unresolved.getFile()).andStubReturn(unresolvedFile);

    List<? extends IdeaDependency> allDependencies = Lists.newArrayList(moduleDependency, resolved, unresolved, null);

    replay(contentRoot, moduleDependency, resolved, unresolved);

    JavaModel model = new JavaModel(allContentRoots, allDependencies);
    List<IdeaContentRoot> contentRoots = model.getContentRoots();
    assertEquals(1, contentRoots.size());
    assertSame(contentRoot, contentRoots.get(0));

    List<IdeaModuleDependency> moduleDependencies = model.getModuleDependencies();
    assertEquals(1, moduleDependencies.size());
    assertSame(moduleDependency, moduleDependencies.get(0));

    List<IdeaSingleEntryLibraryDependency> libraryDependencies = model.getLibraryDependencies();
    assertEquals(1, libraryDependencies.size());
    assertSame(resolved, libraryDependencies.get(0));

    List<String> unresolvedDependencyNames = model.getUnresolvedDependencyNames();
    assertEquals(1, unresolvedDependencyNames.size());
    assertEquals("commons-collections:commons-collections:3.2", unresolvedDependencyNames.get(0));

    verify(contentRoot, moduleDependency, resolved, unresolved);
  }
}
