/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.actions;

import com.android.tools.idea.gradle.project.ProjectStructure;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.testing.IdeComponents;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.TestActionEvent;
import org.mockito.Mock;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link MakeGradleProjectAction}.
 */
public class MakeGradleProjectActionTest extends PlatformTestCase {
  @Mock private GradleBuildInvoker myBuildInvoker;
  @Mock private ProjectStructure myProjectStructure;

  private MakeGradleProjectAction myAction;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    new IdeComponents(project).replaceProjectService(GradleBuildInvoker.class, myBuildInvoker);
    new IdeComponents(project).replaceProjectService(ProjectStructure.class, myProjectStructure);

    myAction = new MakeGradleProjectAction();
  }

  public void testDoPerform() {
    ImmutableList<Module> leafModules = ImmutableList.of(createModule("leaf1"), createModule("leaf2"));
    when(myProjectStructure.getLeafModules()).thenReturn(leafModules);

    // Method to test.
    myAction.doPerform(new TestActionEvent(), getProject());

    // Verify that only "leaf" modules were built.
    verify(myBuildInvoker).assemble(eq(leafModules.toArray(Module.EMPTY_ARRAY)), eq(TestCompileType.ALL));
  }

  public void testDoPerformWithFailedSync() {
    // Simulate failed sync.
    GradleSyncState syncState = mock(GradleSyncState.class);
    new IdeComponents(getProject()).replaceProjectService(GradleSyncState.class, syncState);
    when(syncState.lastSyncFailed()).thenReturn(true);

    // when sync fails, the list of "leaf" modules is empty.
    when(myProjectStructure.getLeafModules()).thenReturn(ImmutableList.of());

    // Method to test.
    myAction.doPerform(new TestActionEvent(), getProject());

    verify(myBuildInvoker).assemble(eq(Module.EMPTY_ARRAY), eq(TestCompileType.ALL));
  }
}
