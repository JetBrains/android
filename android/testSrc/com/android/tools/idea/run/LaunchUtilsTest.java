/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.run;

import static com.android.tools.idea.testing.TestProjectPaths.RUN_CONFIG_ACTIVITY;
import static com.android.tools.idea.testing.TestProjectPaths.RUN_CONFIG_ALIAS;
import static com.android.tools.idea.testing.TestProjectPaths.RUN_CONFIG_WATCHFACE;

import com.android.tools.idea.run.util.LaunchUtils;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.idea.Bombed;
import java.util.Calendar;

public class LaunchUtilsTest extends AndroidGradleTestCase {
  public void testActivity() throws Exception {
    loadProject(RUN_CONFIG_ACTIVITY);
    assertFalse(LaunchUtils.isWatchFeatureRequired(myAndroidFacet));
  }

  public void testActivityAlias() throws Exception {
    loadProject(RUN_CONFIG_ALIAS);
    assertFalse(LaunchUtils.isWatchFeatureRequired(myAndroidFacet));
  }

  @Bombed(year = 2023, month = Calendar.MARCH, day = 30, user = "Nebojsa Viksic",
  description = "Timed out due to: 'Calling invokeAndWait from read-action leads to possible deadlock.' exception")
  public void testWatchFaceService() throws Exception {
    loadProject(RUN_CONFIG_WATCHFACE);
    assertTrue(LaunchUtils.isWatchFeatureRequired(myAndroidFacet));
  }
}
