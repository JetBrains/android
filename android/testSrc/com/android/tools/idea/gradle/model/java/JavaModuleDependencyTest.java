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

import com.google.common.collect.ImmutableList;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.ProjectIdentifier;
import org.gradle.tooling.model.idea.IdeaDependencyScope;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaModuleDependency;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link JavaModuleDependency}.
 */
public class JavaModuleDependencyTest {
  private IdeaModuleDependency myOriginalDependency;
  private IdeaModule myIdeaModule;
  private IdeaProject myIdeaProject;

  @Before
  public void setUp() {
    myOriginalDependency = createMock(IdeaModuleDependency.class);
    myIdeaModule = createMock(IdeaModule.class);
    myIdeaProject = createMock(IdeaProject.class);
  }

  @Test
  public void testCopyWithNullIdeaModule() {

    expect(myIdeaProject.getModules()).andReturn(ImmutableDomainObjectSet.of(ImmutableList.of()));
    replay(myOriginalDependency, myIdeaModule, myIdeaProject);
    assertNull(JavaModuleDependency.copy(myIdeaProject, myOriginalDependency));
    verify(myOriginalDependency, myIdeaModule);
  }

  @Test
  public void testCopyWithNullModuleName() {
    expect(myIdeaProject.getModules()).andReturn(ImmutableDomainObjectSet.of(ImmutableList.of()));
    expect(myIdeaModule.getName()).andStubReturn(null);
    replay(myOriginalDependency, myIdeaModule, myIdeaProject);
    assertNull(JavaModuleDependency.copy(myIdeaProject, myOriginalDependency));
    verify(myOriginalDependency, myIdeaModule);
  }

  @Test
  public void testCopyWithEmptyModuleName() {
    expect(myIdeaProject.getModules()).andReturn(ImmutableDomainObjectSet.of(ImmutableList.of()));
    expect(myIdeaModule.getName()).andStubReturn("");
    replay(myOriginalDependency, myIdeaModule, myIdeaProject);
    assertNull(JavaModuleDependency.copy(myIdeaProject, myOriginalDependency));
    verify(myOriginalDependency, myIdeaModule);
  }

  @Test
  public void testCopy() {
    String moduleName = "lib";
    IdeaDependencyScope scope = createMock(IdeaDependencyScope.class);

    // Setup GradleProject to construct module id.
    GradleProject gradleProject = mock(GradleProject.class);
    ProjectIdentifier projectIdentifier = mock(ProjectIdentifier.class);
    BuildIdentifier buildIdentifier = mock(BuildIdentifier.class);
    when(buildIdentifier.getRootDir()).thenReturn(new File("/mock/project"));
    when(projectIdentifier.getBuildIdentifier()).thenReturn(buildIdentifier);
    when(gradleProject.getProjectIdentifier()).thenReturn(projectIdentifier);
    when(gradleProject.getPath()).thenReturn(":lib");

    expect(myIdeaModule.getName()).andStubReturn(moduleName);
    expect(myIdeaModule.getGradleProject()).andStubReturn(gradleProject);
    expect(myOriginalDependency.getScope()).andStubReturn(scope);
    expect(myOriginalDependency.getTargetModuleName()).andReturn(moduleName);
    expect(myOriginalDependency.getExported()).andStubReturn(true);
    expect(scope.getScope()).andStubReturn("compile");

    DomainObjectSet listOfModules = ImmutableDomainObjectSet.of(ImmutableList.of(myIdeaModule));
    expect(myIdeaProject.getModules()).andReturn(listOfModules);
    replay(myOriginalDependency, myIdeaModule, myIdeaProject, scope);

    JavaModuleDependency copy = JavaModuleDependency.copy(myIdeaProject, myOriginalDependency);
    assertNotNull(copy);
    assertEquals(moduleName, copy.getModuleName());
    assertSame("compile", copy.getScope());
    assertEquals("/mock/project::lib", copy.getModuleId());
    assertTrue(copy.isExported());

    verify(myOriginalDependency, myIdeaModule, scope);
  }
}