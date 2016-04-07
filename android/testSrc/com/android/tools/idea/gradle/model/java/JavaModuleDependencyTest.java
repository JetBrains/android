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
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaModuleDependency;
import org.junit.Before;
import org.junit.Test;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

/**
 * Tests for {@link JavaModuleDependency}.
 */
public class JavaModuleDependencyTest {
  private IdeaModuleDependency myOriginalDependency;
  private IdeaModule myIdeaModule;

  @Before
  public void setUp() {
    myOriginalDependency = createMock(IdeaModuleDependency.class);
    myIdeaModule = createMock(IdeaModule.class);
  }

  @Test
  public void testCopyWithNullIdeaModule() {
    expect(myOriginalDependency.getDependencyModule()).andStubReturn(null);
    replay(myOriginalDependency, myIdeaModule);
    assertNull(JavaModuleDependency.copy(myOriginalDependency));
    verify(myOriginalDependency, myIdeaModule);
  }

  @Test
  public void testCopyWithNullModuleName() {
    expect(myOriginalDependency.getDependencyModule()).andStubReturn(myIdeaModule);
    expect(myIdeaModule.getName()).andStubReturn(null);
    replay(myOriginalDependency, myIdeaModule);
    assertNull(JavaModuleDependency.copy(myOriginalDependency));
    verify(myOriginalDependency, myIdeaModule);
  }

  @Test
  public void testCopyWithEmptyModuleName() {
    expect(myOriginalDependency.getDependencyModule()).andStubReturn(myIdeaModule);
    expect(myIdeaModule.getName()).andStubReturn("");
    replay(myOriginalDependency, myIdeaModule);
    assertNull(JavaModuleDependency.copy(myOriginalDependency));
    verify(myOriginalDependency, myIdeaModule);
  }

  @Test
  public void testCopy() {
    String moduleName = "lib";
    IdeaDependencyScope scope = createMock(IdeaDependencyScope.class);

    expect(myOriginalDependency.getDependencyModule()).andStubReturn(myIdeaModule);
    expect(myIdeaModule.getName()).andStubReturn(moduleName);
    expect(myOriginalDependency.getScope()).andStubReturn(scope);
    expect(myOriginalDependency.getExported()).andStubReturn(true);
    expect(scope.getScope()).andStubReturn("compile");

    replay(myOriginalDependency, myIdeaModule, scope);

    JavaModuleDependency copy = JavaModuleDependency.copy(myOriginalDependency);
    assertNotNull(copy);
    assertEquals(moduleName, copy.getModuleName());
    assertSame("compile", copy.getScope());
    assertTrue(copy.isExported());

    verify(myOriginalDependency, myIdeaModule, scope);
  }
}