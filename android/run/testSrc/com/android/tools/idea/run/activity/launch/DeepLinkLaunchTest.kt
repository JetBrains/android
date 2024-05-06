/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.activity.launch;

import com.android.ddmlib.IDevice
import com.android.ddmlib.IShellOutputReceiver
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.idea.execution.common.assertTaskPresentedInStats
import com.android.tools.idea.execution.common.stats.RunStats
import com.android.tools.idea.run.configuration.execution.createApp
import com.android.tools.idea.testing.AndroidProjectRule
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.TimeUnit

class DeepLinkLaunchTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val usageTrackerRule = UsageTrackerRule()

  @Test
  fun testLaunch() {
    val state = DeepLinkLaunch.State()
    state.DEEP_LINK = "com.example"
    val device = Mockito.mock(IDevice::class.java)

    val app = createApp(device, "com.example.myapplication", emptyList(), ArrayList(setOf("com.example.myapplication.MainActivity")))
    val stats = RunStats(projectRule.project);
    state.launch(device, app, { emptyList() }, false, "", EmptyTestConsoleView(), stats)

    stats.success()
    assertTaskPresentedInStats(usageTrackerRule.usages, "LAUNCH_DEEP_LINK")

    Mockito.verify(device).executeShellCommand(
      Mockito.eq(
        "am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d 'com.example'"),
      Mockito.any(IShellOutputReceiver::class.java),
      Mockito.eq(15L),
      Mockito.eq(TimeUnit.SECONDS))
  }
}