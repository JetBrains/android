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
package com.android.tools.idea.gradle.project.sync.precheck;

import com.android.tools.idea.gradle.project.sync.SdkSync;
import com.intellij.openapi.project.Project;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link AndroidSdkPreSyncCheck}.
 */
public class AndroidSdkPreSyncCheckTest {
  private Project myProject;
  private SdkSync mySdkSync;
  private AndroidSdkPreSyncCheck myCondition;

  @Before
  public void setUp() {
    myProject = mock(Project.class);
    mySdkSync = mock(SdkSync.class);
    myCondition = new AndroidSdkPreSyncCheck(mySdkSync);
  }

  @Test
  public void doCheckCanSyncWithSuccessfulSdkSync() throws IOException {
    PreSyncCheckResult result = myCondition.doCheckCanSync(myProject);
    assertTrue(result.isSuccess());
    verify(mySdkSync).syncIdeAndProjectAndroidSdks(myProject);
  }

  @Test
  public void doCheckCanSyncWithFailedSdkSync() throws IOException {
    //noinspection ThrowableInstanceNeverThrown
    IOException error = new IOException();
    doThrow(error).when(mySdkSync).syncIdeAndProjectAndroidSdks(myProject);
    PreSyncCheckResult result = myCondition.doCheckCanSync(myProject);
    assertFalse(result.isSuccess());
    verify(mySdkSync).syncIdeAndProjectAndroidSdks(myProject);
  }
}