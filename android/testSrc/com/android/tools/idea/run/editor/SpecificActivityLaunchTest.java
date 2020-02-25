/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.run.editor;

import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.run.ValidationError;
import com.android.tools.idea.run.activity.SpecificActivityLocator;
import com.android.tools.idea.run.activity.StartActivityFlagsProvider;
import com.android.tools.idea.run.tasks.LaunchTask;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

public class SpecificActivityLaunchTest extends AndroidGradleTestCase {

  @Mock StartActivityFlagsProvider startActivityFlagsProvider;
  @Mock ProfilerState profilerState;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    loadSimpleApplication();
    initMocks(this);
  }

  public void testGetLaunchTask() {
    SpecificActivityLaunch.State state = new SpecificActivityLaunch.State();
    LaunchTask launchTask = state.getLaunchTask("applicationId", myAndroidFacet, startActivityFlagsProvider, profilerState);
    assertNotNull(launchTask);
  }

  public void testGetActivity() {

    SpecificActivityLaunch.State state = new SpecificActivityLaunch.State();
    SpecificActivityLocator activityLocator = state.getActivityLocator(myAndroidFacet);
    assertNotNull(activityLocator);
  }

  public void testCheckConfigurationSkipValidation() {
    SpecificActivityLaunch.State state = new SpecificActivityLaunch.State();
    state.SKIP_ACTIVITY_VALIDATION = true;
    List<ValidationError> errors = state.checkConfiguration(myAndroidFacet);
    assertEmpty(errors);
  }

  public void testCheckConfiguration() {
    SpecificActivityLocator specificActivityLocator = mock(SpecificActivityLocator.class);
    initMocks(this);

    SpecificActivityLaunch.State state = new SpecificActivityLaunch.State() {
      @NotNull
      @Override
      protected SpecificActivityLocator getActivityLocator(@NotNull AndroidFacet facet) {
        return specificActivityLocator;
      }
    };
    List<ValidationError> errors = state.checkConfiguration(myAndroidFacet);
    assertEmpty(errors);
  }
}