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

import com.android.tools.idea.gradle.stubs.gradle.IdeaModuleStub;
import com.android.tools.idea.gradle.stubs.gradle.IdeaProjectStub;
import junit.framework.TestCase;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.idea.IdeaContentRoot;
import org.gradle.tooling.model.idea.IdeaModuleDependency;
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;
import org.jetbrains.plugins.gradle.model.ExtIdeaCompilerOutput;
import org.jetbrains.plugins.gradle.model.ModuleExtendedModel;

import java.io.File;
import java.util.List;

import static org.easymock.EasyMock.*;

/**
 * Tests for {@link JavaModel}.
 */
public class JavaModelTest extends TestCase {
  private IdeaModuleStub myModule;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    IdeaProjectStub project = new IdeaProjectStub("javaModelTest");
    myModule = project.addModule("lib");
  }

  public void testModelCreation() {
    IdeaModuleDependency moduleDependency = createMock(IdeaModuleDependency.class);

    IdeaSingleEntryLibraryDependency resolved = createMock(IdeaSingleEntryLibraryDependency.class);
    File resolvedFile = new File("guava.jar");
    expect(resolved.getFile()).andStubReturn(resolvedFile);

    IdeaSingleEntryLibraryDependency unresolved = createMock(IdeaSingleEntryLibraryDependency.class);
    File unresolvedFile = new File("unresolved dependency - commons-collections commons-collections 3.2");
    expect(unresolved.getFile()).andStubReturn(unresolvedFile);

    myModule.addDependency(moduleDependency);
    myModule.addDependency(resolved);
    myModule.addDependency(unresolved);

    ExtIdeaCompilerOutput compilerOutput = createMock(ExtIdeaCompilerOutput.class);

    ModuleExtendedModel extendedModel = createMock(ModuleExtendedModel.class);
    expect(extendedModel.getContentRoots()).andStubReturn(null);
    expect(extendedModel.getCompilerOutput()).andStubReturn(compilerOutput);


    replay(moduleDependency, resolved, unresolved, extendedModel, compilerOutput);

    JavaModel model = JavaModel.newJavaModel(myModule, extendedModel);

    DomainObjectSet<? extends IdeaContentRoot> expectedContentRoots = myModule.getContentRoots();
    List<IdeaContentRoot> actualContentRoots = model.getContentRoots();
    assertEquals(expectedContentRoots.size(), actualContentRoots.size());
    assertSame(expectedContentRoots.getAt(0), actualContentRoots.get(0));

    List<IdeaModuleDependency> moduleDependencies = model.getModuleDependencies();
    assertEquals(1, moduleDependencies.size());
    assertSame(moduleDependency, moduleDependencies.get(0));

    List<IdeaSingleEntryLibraryDependency> libraryDependencies = model.getLibraryDependencies();
    assertEquals(1, libraryDependencies.size());
    assertSame(resolved, libraryDependencies.get(0));

    List<String> unresolvedDependencyNames = model.getUnresolvedDependencyNames();
    assertEquals(1, unresolvedDependencyNames.size());
    assertEquals("commons-collections:commons-collections:3.2", unresolvedDependencyNames.get(0));

    verify(moduleDependency, resolved, unresolved, extendedModel, compilerOutput);
  }
}
