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
import com.intellij.openapi.application.ReadAction;
import com.intellij.util.concurrency.AppExecutorUtil;

public class LaunchUtilsTest extends AndroidGradleTestCase {
  public void testActivity() throws Exception {
    loadProject(RUN_CONFIG_ACTIVITY);
    assertFalse(ReadAction.nonBlocking(() -> LaunchUtils.isWatchFeatureRequired(myAndroidFacet)).submit(AppExecutorUtil.getAppExecutorService()).get());
  }

  public void testActivityAlias() throws Exception {
    loadProject(RUN_CONFIG_ALIAS);
    assertFalse(ReadAction.nonBlocking(() -> LaunchUtils.isWatchFeatureRequired(myAndroidFacet)).submit(AppExecutorUtil.getAppExecutorService()).get());
  }

  public void testWatchFaceService() throws Exception {
    loadProject(RUN_CONFIG_WATCHFACE);
    assertTrue(ReadAction.nonBlocking(() -> LaunchUtils.isWatchFeatureRequired(myAndroidFacet)).submit(AppExecutorUtil.getAppExecutorService()).get());
  }
}
