/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.post.project;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.project.AndroidKtsSupportNotification;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

/**
 * Tests for {@link GradleKtsBuildFilesWarningStep}
 */
public class GradleKtsBuildFilesWarningStepTest extends PlatformTestCase {

  @Mock private AndroidKtsSupportNotification myNotification;
  @NotNull private GradleKtsBuildFilesWarningStep myWarningStep = new GradleKtsBuildFilesWarningStep();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    new IdeComponents(myProject).replaceProjectService(AndroidKtsSupportNotification.class, myNotification);
  }

  /**
   * Check that a warning is created when the project uses kts build files
   */
  public void testNotificationShownForProjectWithKts() {
    myWarningStep.doSetUpProject(myProject, true);
    verify(myNotification).showWarningIfNotShown();
  }

  /**
   * Check that a warning is *NOT* created when the project does not use kts build files
   */
  public void testNotificationNotShownForProjectWithoutKts() {
    myWarningStep.doSetUpProject(myProject, false);
    verify(myNotification, never()).showWarningIfNotShown();
  }

  public void testStateSavedToProjectUserDataWithKts() {
    myWarningStep.doSetUpProject(myProject, true);
    assertTrue(myProject.getUserData(GradleKtsBuildFilesWarningStep.HAS_KTS_BUILD_FILES));
  }

  public void testStateSavedToProjectUserDataWithoutKts() {
    myWarningStep.doSetUpProject(myProject, false);
    assertFalse(myProject.getUserData(GradleKtsBuildFilesWarningStep.HAS_KTS_BUILD_FILES));
  }
}
