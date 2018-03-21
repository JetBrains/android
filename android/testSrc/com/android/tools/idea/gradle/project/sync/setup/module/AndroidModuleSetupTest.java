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
package com.android.tools.idea.gradle.project.sync.setup.module;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link AndroidModuleSetup}.
 */
public class AndroidModuleSetupTest {
  @Mock private AndroidModuleModel myAndroidModel;
  @Mock private AndroidModuleSetupStep mySetupStep1;
  @Mock private AndroidModuleSetupStep mySetupStep2;
  @Mock private ModuleSetupContext myModuleSetupContext;

  private AndroidModuleSetup myModuleSetup;

  @Before
  public void setUp() {
    initMocks(this);
    myModuleSetup = new AndroidModuleSetup(mySetupStep1, mySetupStep2);
    when(mySetupStep1.invokeOnSkippedSync()).thenReturn(true); // Only mySetupStep1 can be invoked when sync is skipped.
  }

  @Test
  public void setUpAndroidModuleWithSyncSkipped() {
    myModuleSetup.setUpModule(myModuleSetupContext, myAndroidModel, true /* sync skipped */);

    // Only 'mySetupStep1' should be invoked when sync is skipped.
    verify(mySetupStep1, times(1)).setUpModule(myModuleSetupContext, myAndroidModel);
    verify(mySetupStep2, times(0)).setUpModule(myModuleSetupContext, myAndroidModel);
  }

  @Test
  public void setUpAndroidModuleWithSyncNotSkipped() {
    myModuleSetup.setUpModule(myModuleSetupContext, myAndroidModel, false /* sync not skipped */);

    // Only 'mySetupStep1' should be invoked when sync is skipped.
    verify(mySetupStep1, times(1)).setUpModule(myModuleSetupContext, myAndroidModel);
    verify(mySetupStep2, times(1)).setUpModule(myModuleSetupContext, myAndroidModel);
  }
}