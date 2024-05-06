/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.devicemanagerv2

import com.android.tools.analytics.UsageTrackerRule
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DeviceManagerEvent
import com.intellij.testFramework.ApplicationRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ColdBootActionTest {
  @get:Rule val applicationRule = ApplicationRule()
  @get:Rule val usageTrackerRule = UsageTrackerRule()

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun coldBoot() = runTest {
    runWithSingleFakeDevice {
      ColdBootAction().actionPerformed(actionEvent)
      advanceUntilIdle()

      assertThat(handle.coldBootAction.invoked).isEqualTo(1)
      assertThat(usageTrackerRule.deviceManagerEventKinds())
        .containsExactly(DeviceManagerEvent.EventKind.VIRTUAL_COLD_BOOT_NOW_ACTION)
    }
  }
}
