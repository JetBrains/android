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
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.idea.Bombed;
import com.intellij.openapi.application.ReadAction;
import com.intellij.util.concurrency.AppExecutorUtil;

import java.util.Calendar;

import static com.android.tools.idea.testing.TestProjectPaths.*;

public class LaunchUtilsTest extends AndroidGradleTestCase {
  public void testActivity() throws Exception {
    loadProject(RUN_CONFIG_ACTIVITY);
    boolean result = ReadAction.nonBlocking(() -> LaunchUtils.isWatchFeatureRequired(myAndroidFacet)).submit(AppExecutorUtil.getAppExecutorService()).get();
    assertFalse(result);
  }

  public void testActivityAlias() throws Exception {
    loadProject(RUN_CONFIG_ALIAS);
    boolean result = ReadAction.nonBlocking(() -> LaunchUtils.isWatchFeatureRequired(myAndroidFacet)).submit(AppExecutorUtil.getAppExecutorService()).get();
    assertFalse(result);
  }

  @Bombed(year = 2022, month = Calendar.NOVEMBER, day = 30, user = "Andrei Kuznetsov",
  description = "Timed out due to: 'Calling invokeAndWait from read-action leads to possible deadlock.' exception")
  public void testWatchFaceService() throws Exception {
    loadProject(RUN_CONFIG_WATCHFACE);
    boolean result = ReadAction.nonBlocking(() -> LaunchUtils.isWatchFeatureRequired(myAndroidFacet)).submit(AppExecutorUtil.getAppExecutorService()).get();
    assertTrue(result);
  }
}
