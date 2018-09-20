/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.testing.AndroidGradleTestCase;

public class AndroidGradleProjectStartupActivityIntegTest extends AndroidGradleTestCase {

  public void testProjectSetupIsRunOnlyOnce() throws Exception {
    loadSimpleApplication();
    GradleSyncListener listener = mock(GradleSyncListener.class);
    GradleSyncState.subscribe(getProject(), listener);
    AndroidGradleProjectStartupActivity startupActivity = new AndroidGradleProjectStartupActivity();
    startupActivity.runActivity(getProject());
    verify(listener, never()).syncStarted(any(), anyBoolean(), anyBoolean());
    verify(listener, never()).setupStarted(any());
  }
}
