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

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.TestActionEvent;
import org.mockito.Mock;

/**
 * Tests for {@link MakeGradleProjectAction}.
 */
public class MakeGradleProjectActionTest extends PlatformTestCase {
  @Mock private GradleBuildInvoker myBuildInvoker;
  private MakeGradleProjectAction myAction;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    ServiceContainerUtil.replaceService(project, GradleBuildInvoker.class, myBuildInvoker, getTestRootDisposable());

    myAction = new MakeGradleProjectAction();
  }

  public void testDoPerform() {
    // Method to test.
    myAction.doPerform(TestActionEvent.createTestEvent(), getProject());

    // Verify.
    verify(myBuildInvoker).assemble(eq(TestCompileType.ALL));
  }
}
