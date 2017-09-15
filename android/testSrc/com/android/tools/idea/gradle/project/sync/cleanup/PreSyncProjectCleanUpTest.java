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
package com.android.tools.idea.gradle.project.sync.cleanup;

import com.intellij.openapi.project.Project;
import org.junit.Before;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link PreSyncProjectCleanUp}.
 */
public class PreSyncProjectCleanUpTest {
  private Project myProject;

  @Before
  public void setUp() {
    myProject = mock(Project.class);
  }

  @Test
  public void mainConstructor() {
    PreSyncProjectCleanUp projectCleanUp = new PreSyncProjectCleanUp();
    ProjectCleanUpTask[] tasks = projectCleanUp.getCleanUpTasks();
    assertThat(tasks).hasLength(6);
    assertThat(tasks[0]).isInstanceOf(ProjectPreferencesCleanUpTask.class);
    assertThat(tasks[1]).isInstanceOf(GradleRunnerCleanupTask.class);
    assertThat(tasks[2]).isInstanceOf(HttpProxySettingsCleanUpTask.class);
    assertThat(tasks[3]).isInstanceOf(GradleSettingsCleanUpTask.class);
    assertThat(tasks[4]).isInstanceOf(GradleDistributionCleanUpTask.class);
  }

  public void execute() {
    ProjectCleanUpTask task1 = mock(ProjectCleanUpTask.class);
    ProjectCleanUpTask task2 = mock(ProjectCleanUpTask.class);

    PreSyncProjectCleanUp projectCleanUp = new PreSyncProjectCleanUp(task1, task2);
    projectCleanUp.cleanUp(myProject);

    verify(task1).cleanUp(myProject);
    verify(task2).cleanUp(myProject);
  }
}