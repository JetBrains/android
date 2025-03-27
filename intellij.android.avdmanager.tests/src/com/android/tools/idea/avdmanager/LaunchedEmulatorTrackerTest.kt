/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.avdmanager

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito.mock

/** Tests for [LaunchedAvdTracker]. */
class LaunchedAvdTrackerTest {

  @Test
  fun testTracker() {
    val tracker = LaunchedAvdTracker()
    assertThat(tracker.launchedAvds).isEmpty()
    val handle1 = mock<ProcessHandle>()
    tracker.started("avd1", handle1)
    assertThat(tracker.launchedAvds).isEqualTo(mapOf("avd1" to handle1))
    val handle2 = mock<ProcessHandle>()
    tracker.started("avd2", handle2)
    assertThat(tracker.launchedAvds).isEqualTo(mapOf("avd1" to handle1, "avd2" to handle2))
    tracker.terminated("avd1", handle1)
    assertThat(tracker.launchedAvds).isEqualTo(mapOf("avd2" to handle2))
    tracker.terminated("avd2", handle2)
    assertThat(tracker.launchedAvds).isEmpty()
  }
}
