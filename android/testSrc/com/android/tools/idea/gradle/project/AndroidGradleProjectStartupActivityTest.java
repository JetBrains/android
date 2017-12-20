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
package com.android.tools.idea.gradle.project;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;

import static org.mockito.Mockito.*;

/**
 * Tests for {@link AndroidGradleProjectStartupActivity}.
 */
public class AndroidGradleProjectStartupActivityTest extends IdeaTestCase {
  private IdeComponents myIdeComponents;
  private GradleProjectInfo myGradleProjectInfo;
  private AndroidGradleProjectStartupActivity myStartupActivity;
  private GradleSyncInvoker mySyncInvoker;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myIdeComponents = new IdeComponents(myProject);
    mySyncInvoker = myIdeComponents.mockService(GradleSyncInvoker.class);
    myGradleProjectInfo = myIdeComponents.mockProjectService(GradleProjectInfo.class);
    myStartupActivity = new AndroidGradleProjectStartupActivity();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myIdeComponents.restore();
    }
    finally {
      super.tearDown();
    }
  }

  // http://b/62543184
  public void testRunActivityWithNewCreatedProject() {
    when(myGradleProjectInfo.isBuildWithGradle()).thenReturn(true);
    when(myGradleProjectInfo.isImportedProject()).thenReturn(true);

    Project project = getProject();
    myStartupActivity.runActivity(project);

    verify(mySyncInvoker, never()).requestProjectSync(same(project), any());
  }

  public void testRunActivityWithExistingGradleProject() {
    when(myGradleProjectInfo.isBuildWithGradle()).thenReturn(true);

    Project project = getProject();
    myStartupActivity.runActivity(project);

    GradleSyncInvoker.Request request = GradleSyncInvoker.Request.projectLoaded();
    request.useCachedGradleModels = true;

    verify(mySyncInvoker, times(1)).requestProjectSync(project, request);
  }


  public void testRunActivityWithNonGradleProject() {
    when(myGradleProjectInfo.isBuildWithGradle()).thenReturn(false);

    Project project = getProject();
    myStartupActivity.runActivity(project);

    verify(mySyncInvoker, never()).requestProjectSync(same(project), any());
  }
}