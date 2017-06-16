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

import com.android.tools.idea.project.FeatureEnableService;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.testFramework.IdeaTestCase;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GradleFeatureEnableServiceTest extends IdeaTestCase {
  FeatureEnableService myService;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    IdeComponents.replaceService(myProject, GradleProjectInfo.class, mock(GradleProjectInfo.class));
    when(GradleProjectInfo.getInstance(myProject).isBuildWithGradle()).thenReturn(true);

    myService = FeatureEnableService.getInstance(myProject);
  }

  public void testIsGradleFeatureEnableService() {
    assertThat(myService).isInstanceOf(GradleFeatureEnableService.class);
  }

  public void testIsNotGradleFeatureEnableService() {
    when(GradleProjectInfo.getInstance(myProject).isBuildWithGradle()).thenReturn(false);
    assertThat(FeatureEnableService.getInstance(myProject)).isNotInstanceOf(GradleFeatureEnableService.class);
  }

  public void testIsLayoutEditorEnabled() {
    assertThat(myService.isLayoutEditorEnabled(myProject)).isTrue();
  }
}
