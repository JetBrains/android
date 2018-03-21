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
package com.android.tools.idea.gradle.project.sync.ng;

import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link GradleProjectRefresh}.
 */
public class GradleProjectRefreshTest extends IdeaTestCase {
  @Mock private SyncExecutor mySyncExecutor;
  @Mock private SyncExecutionCallback.Factory myCallbackFactory;
  @Mock private ProjectSetup.Factory mySetupFactory;

  private SyncExecutionCallback myCallback;
  private GradleProjectRefresh myProjectRefresh;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myCallback = new SyncExecutionCallback();
    myProjectRefresh = new GradleProjectRefresh(getProject(), mySyncExecutor, myCallbackFactory, mySetupFactory);
  }

  public void testRefresh() {
    ProjectSetup projectSetup = mock(ProjectSetup.class);
    SyncProjectModels projectModels = mock(SyncProjectModels.class);
    myCallback.setDone(projectModels);
    when(myCallbackFactory.create()).thenReturn(myCallback);
    when(mySetupFactory.create(any())).thenReturn(projectSetup);
    doNothing().when(mySyncExecutor).syncProject(any(), eq(myCallback));
    doNothing().when(projectSetup).setUpProject(eq(projectModels), any());
    doNothing().when(projectSetup).commit();

    myProjectRefresh.refresh();

    verify(mySyncExecutor).syncProject(any(), eq(myCallback));
    verify(projectSetup).setUpProject(eq(projectModels), any());
    verify(projectSetup).commit();
  }
}
