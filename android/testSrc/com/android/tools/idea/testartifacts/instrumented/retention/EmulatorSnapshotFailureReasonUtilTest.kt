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
package com.android.tools.idea.testartifacts.instrumented.retention

import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.EmulatorSnapshotFailureReason.EMULATOR_SNAPSHOT_FAILURE_REASON_INCOMPATIBLE_VERSION
import com.google.wireless.android.sdk.stats.EmulatorSnapshotFailureReason.EMULATOR_SNAPSHOT_FAILURE_REASON_UNSPECIFIED
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class EmulatorSnapshotFailureReasonUtilTest {
  @Test
  fun incompatibleSnapshotVersion() {
    assertThat(findFailureReasonFromEmulatorOutput("incompatible snapshot version")).isEqualTo(
      EMULATOR_SNAPSHOT_FAILURE_REASON_INCOMPATIBLE_VERSION)
  }
  @Test
  fun unknownFailure() {
    assertThat(findFailureReasonFromEmulatorOutput("")).isEqualTo(
      EMULATOR_SNAPSHOT_FAILURE_REASON_UNSPECIFIED)
  }
}