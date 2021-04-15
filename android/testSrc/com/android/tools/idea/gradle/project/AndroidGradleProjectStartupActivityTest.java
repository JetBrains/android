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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.google.wireless.android.sdk.stats.GradleSyncStats;
import com.intellij.mock.MockModule;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.ServiceContainerUtil;
import java.util.Collections;

/**
 * Tests for {@link AndroidGradleProjectStartupActivity}.
 */
public class AndroidGradleProjectStartupActivityTest extends HeavyPlatformTestCase {
  private GradleProjectInfo myGradleProjectInfo;
  private AndroidGradleProjectStartupActivity myStartupActivity;
  private GradleSyncInvoker mySyncInvoker;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Project project = getProject();
    mySyncInvoker = mock(GradleSyncInvoker.class);
    ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), GradleSyncInvoker.class, mySyncInvoker, project);
    myGradleProjectInfo = mock(GradleProjectInfo.class);
    ServiceContainerUtil.replaceService(myProject, GradleProjectInfo.class, myGradleProjectInfo, project);

    myStartupActivity = new AndroidGradleProjectStartupActivity();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myGradleProjectInfo = null;
    }
    finally {
      super.tearDown();
    }
  }

  public void testRunActivityWithImportedProject() {
    when(myGradleProjectInfo.isBuildWithGradle()).thenReturn(true);
    when(myGradleProjectInfo.isImportedProject()).thenReturn(true);

    Project project = getProject();
    myStartupActivity.runActivity(project);

    verify(mySyncInvoker, times(1)).requestProjectSync(same(project), any(GradleSyncInvoker.Request.class));
  }

  public void testRunActivityWithSkipStartupProject() {
    when(myGradleProjectInfo.isBuildWithGradle()).thenReturn(true);
    when(myGradleProjectInfo.isSkipStartupActivity()).thenReturn(true);

    Project project = getProject();
    myStartupActivity.runActivity(project);

    verify(mySyncInvoker, never()).requestProjectSync(same(project), any(GradleSyncInvoker.Request.class));
  }

  public void testRunActivityWithExistingGradleProject() {
    when(myGradleProjectInfo.isBuildWithGradle()).thenReturn(true);
    when(myGradleProjectInfo.getAndroidModules()).thenReturn(Collections.singletonList(new MockModule(getTestRootDisposable())));

    Project project = getProject();
    myStartupActivity.runActivity(project);

    GradleSyncInvoker.Request request = new GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_PROJECT_REOPEN);
    verify(mySyncInvoker, times(1)).requestProjectSync(project, request);
  }

  public void testRunActivityWithNonGradleProject() {
    when(myGradleProjectInfo.isBuildWithGradle()).thenReturn(false);

    Project project = getProject();
    myStartupActivity.runActivity(project);

    verify(mySyncInvoker, never()).requestProjectSync(same(project), any(GradleSyncInvoker.Request.class));
  }
}
