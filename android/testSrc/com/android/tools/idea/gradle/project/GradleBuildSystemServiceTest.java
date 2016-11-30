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
package com.android.tools.idea.gradle.project;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager;
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.project.BuildSystemService;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.testFramework.IdeaTestCase;

import java.util.Collections;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link GradleBuildSystemService}.
 */
public class GradleBuildSystemServiceTest extends IdeaTestCase {
  private BuildSystemService myService;
  private GradleSyncInvoker myOriginalGradleSyncInvoker;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myOriginalGradleSyncInvoker = GradleSyncInvoker.getInstance();
    IdeComponents.replaceServiceWithMock(GradleSyncInvoker.class);
    IdeComponents.replaceServiceWithMock(myProject, GradleProjectInfo.class);
    IdeComponents.replaceServiceWithMock(myProject, GradleProjectBuilder.class);
    IdeComponents.replaceServiceWithMock(myProject, GradleDependencyManager.class);
    when(GradleProjectInfo.getInstance(myProject).isBuildWithGradle()).thenReturn(true);
    myService = BuildSystemService.getInstance(myProject);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myOriginalGradleSyncInvoker != null) {
        IdeComponents.replaceService(GradleSyncInvoker.class, myOriginalGradleSyncInvoker);
      }
    } finally {
      super.tearDown();
    }
  }

  public void testIsGradleBuildSystemService() {
    assertThat(myService).isInstanceOf(GradleBuildSystemService.class);
  }

  public void testIsNotGradleBuildSystemService() {
    when(GradleProjectInfo.getInstance(myProject).isBuildWithGradle()).thenReturn(false);
    assertThat(BuildSystemService.getInstance(myProject)).isNotInstanceOf(GradleBuildSystemService.class);
  }

  public void testSyncProject() {
    myService.syncProject(myProject);
    verify(GradleSyncInvoker.getInstance()).requestProjectSyncAndSourceGeneration(myProject, null);
  }

  public void testBuildProject() {
    myService.buildProject(myProject);
    verify(GradleProjectBuilder.getInstance(myProject)).compileJava();
  }

  public void testAddDependency() {
    String artifact = "com.android.foo:bar";
    myService.addDependency(getModule(), artifact);
    Iterable<GradleCoordinate> dependencies = Collections.singletonList(GradleCoordinate.parseCoordinateString(artifact + ":+"));
    verify(GradleDependencyManager.getInstance(myProject)).ensureLibraryIsIncluded(eq(getModule()), eq(dependencies), any());
  }
}
