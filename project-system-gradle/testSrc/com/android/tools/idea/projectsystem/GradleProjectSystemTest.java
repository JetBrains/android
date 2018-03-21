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
package com.android.tools.idea.projectsystem;

import com.android.tools.idea.gradle.dependencies.GradleDependencyManager;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder;
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.testFramework.IdeaTestCase;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

public class GradleProjectSystemTest extends IdeaTestCase {
  private IdeComponents myIdeComponents;
  private GradleProjectInfo myGradleProjectInfo;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myIdeComponents = new IdeComponents(myProject);

    myIdeComponents.mockProjectService(GradleDependencyManager.class);
    myIdeComponents.mockProjectService(GradleProjectBuilder.class);
    myGradleProjectInfo = myIdeComponents.mockProjectService(GradleProjectInfo.class);
    when(myGradleProjectInfo.isBuildWithGradle()).thenReturn(true);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myIdeComponents.restore();
    } finally {
      super.tearDown();
    }
  }

  public void testIsGradleProjectSystem() {
    assertThat(ProjectSystemUtil.getProjectSystem(getProject())).isInstanceOf(GradleProjectSystem.class);
  }

  public void testBuildProject() {
    ProjectSystemUtil.getProjectSystem(getProject()).buildProject();
    verify(GradleProjectBuilder.getInstance(myProject)).compileJava();
  }
}
