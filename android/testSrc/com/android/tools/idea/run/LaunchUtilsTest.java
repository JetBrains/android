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

import com.android.tools.idea.run.util.LaunchUtils;
import com.android.tools.idea.templates.AndroidGradleTestCase;

public class LaunchUtilsTest extends AndroidGradleTestCase {
  public void testActivity() throws Exception {
    if (!CAN_SYNC_PROJECTS) {
      System.err.println("LaunchUtilsTest.testActivity temporarily disabled");
      return;
    }

    loadProject("projects/runConfig/activity");
    assertFalse(LaunchUtils.isWatchFaceApp(myAndroidFacet));
    assertFalse(LaunchUtils.isWatchFeatureRequired(myAndroidFacet));
  }

  public void testActivityAlias() throws Exception {
    if (!CAN_SYNC_PROJECTS) {
      System.err.println("LaunchUtilsTest.testActivityAlias temporarily disabled");
      return;
    }

    loadProject("projects/runConfig/alias");
    assertFalse(LaunchUtils.isWatchFaceApp(myAndroidFacet));
  }

  public void testWatchFaceService() throws Exception {
    if (!CAN_SYNC_PROJECTS) {
      System.err.println("LaunchUtilsTest.testWatchFaceService temporarily disabled");
      return;
    }

    loadProject("projects/runConfig/watchface");
    assertTrue(LaunchUtils.isWatchFaceApp(myAndroidFacet));
    assertTrue(LaunchUtils.isWatchFeatureRequired(myAndroidFacet));
  }
}
