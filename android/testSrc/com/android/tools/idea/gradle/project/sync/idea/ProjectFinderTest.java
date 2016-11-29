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
package com.android.tools.idea.gradle.project.sync.idea;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext;
import org.mockito.Mock;

import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link ProjectFinder}.
 */
public class ProjectFinderTest extends IdeaTestCase {
  @Mock private ProjectResolverContext myContext;

  private ProjectFinder myProjectFinder;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    myProjectFinder = new ProjectFinder();
  }

  public void testFindProjectWithMatchingProjectPath() {
    Project project = getProject();
    String projectPath = project.getBasePath();
    assertNotNull(projectPath);

    when(myContext.getProjectPath()).thenReturn(projectPath);

    Project found = myProjectFinder.findProject(myContext);
    assertSame(project, found);
  }

  public void testFindProjectWithNonMatchingProjectPath() {
    when(myContext.getProjectPath()).thenReturn("fakePath");

    Project found = myProjectFinder.findProject(myContext);
    assertNull(found);
  }

  public void testFindProjectWithNullProjectPath() {
    when(myContext.getProjectPath()).thenReturn(null);

    Project found = myProjectFinder.findProject(myContext);
    assertNull(found);
  }
}