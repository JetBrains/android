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
package com.android.tools.idea.gradle.project.sync.setup.post;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.HeavyPlatformTestCase;
import org.mockito.Mock;

/**
 * Tests for {@link ProjectSetup}.
 */
public class ProjectSetupTest extends HeavyPlatformTestCase {
  @Mock ProjectSetupStep mySetupStep1;
  @Mock ProjectSetupStep mySetupStep2;
  @Mock ProgressIndicator myProgressIndicator;

  private ProjectSetup mySetup;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    mySetup = new ProjectSetup(getProject(), mySetupStep1, mySetupStep2);
  }

  public void testSetUpProjectWithFailedSync() {
    when(mySetupStep1.invokeOnFailedSync()).thenReturn(true);
    when(mySetupStep2.invokeOnFailedSync()).thenReturn(false);

    mySetup.setUpProject(true /* sync failed */);

    Project project = getProject();
    verify(mySetupStep1, times(1)).setUpProject(project);
    verify(mySetupStep2, never()).setUpProject(project);
  }

  public void testSetUpProjectWithSuccessfulSync() {
    when(mySetupStep1.invokeOnFailedSync()).thenReturn(true);
    when(mySetupStep2.invokeOnFailedSync()).thenReturn(false);

    mySetup.setUpProject(false /* sync successful */);

    Project project = getProject();
    verify(mySetupStep1, times(1)).setUpProject(project);
    verify(mySetupStep2, times(1)).setUpProject(project);
  }

  public void testSetUpProjectWithWriteAccess() {
    when(mySetupStep1.invokeOnFailedSync()).thenReturn(true);
    when(mySetupStep2.invokeOnFailedSync()).thenReturn(false);

    ApplicationManager.getApplication().runWriteAction(() -> mySetup.setUpProject(false /* sync successful */));

    Project project = getProject();
    verify(mySetupStep1, times(1)).setUpProject(project);
    verify(mySetupStep2, times(1)).setUpProject(project);
  }
}