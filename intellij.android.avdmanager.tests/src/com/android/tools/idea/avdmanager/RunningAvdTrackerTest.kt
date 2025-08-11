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

import com.android.sdklib.deviceprovisioner.RunningAvd.RunType
import com.android.testutils.FakeProcessHandle
import com.google.common.truth.Truth.assertThat
import java.nio.file.Path
import org.junit.Test

/** Tests for [RunningAvdTracker]. */
class RunningAvdTrackerTest {

  @Test
  fun testTracker() {
    val tracker = RunningAvdTracker()
    assertThat(tracker.runningAvds).isEmpty()
    val handle1 = FakeProcessHandle(1)
    tracker.started(Path.of("avd1"), handle1, RunType.EMBEDDED, isLaunchedByThisProcess = true)
    assertThat(tracker.runningAvds.toSortedMap().toString())
      .isEqualTo(
        "{avd1=RunningAvd(avdDataFolder=avd1, processHandle=1, runType=EMBEDDED, isLaunchedByThisProcess=true, isShuttingDown=false)}"
      )
    val handle2 = FakeProcessHandle(2)
    tracker.started(Path.of("avd2"), handle2, RunType.STANDALONE)
    assertThat(tracker.runningAvds.toSortedMap().toString())
      .isEqualTo(
        "{avd1=RunningAvd(avdDataFolder=avd1, processHandle=1, runType=EMBEDDED, isLaunchedByThisProcess=true, isShuttingDown=false), " +
          "avd2=RunningAvd(avdDataFolder=avd2, processHandle=2, runType=STANDALONE, isLaunchedByThisProcess=false, isShuttingDown=false)}"
      )
    tracker.started(Path.of("avd1"), handle1, RunType.EMBEDDED)
    assertThat(tracker.runningAvds.toSortedMap().toString())
      .isEqualTo(
        "{avd1=RunningAvd(avdDataFolder=avd1, processHandle=1, runType=EMBEDDED, isLaunchedByThisProcess=true, isShuttingDown=false), " +
          "avd2=RunningAvd(avdDataFolder=avd2, processHandle=2, runType=STANDALONE, isLaunchedByThisProcess=false, isShuttingDown=false)}"
      )
    tracker.shuttingDown(Path.of("avd1"))
    assertThat(tracker.runningAvds.toSortedMap().toString())
      .isEqualTo(
        "{avd1=RunningAvd(avdDataFolder=avd1, processHandle=1, runType=EMBEDDED, isLaunchedByThisProcess=true, isShuttingDown=true), " +
          "avd2=RunningAvd(avdDataFolder=avd2, processHandle=2, runType=STANDALONE, isLaunchedByThisProcess=false, isShuttingDown=false)}"
      )
    handle1.destroy()
    assertThat(tracker.runningAvds.toSortedMap().toString())
      .isEqualTo(
        "{avd2=RunningAvd(avdDataFolder=avd2, processHandle=2, runType=STANDALONE, isLaunchedByThisProcess=false, isShuttingDown=false)}"
      )
    handle2.destroy()
    assertThat(tracker.runningAvds).isEmpty()
  }
}
