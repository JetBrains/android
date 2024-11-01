/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.wearwhs.view

import com.android.tools.idea.wearwhs.WhsDataType
import com.android.tools.idea.wearwhs.communication.CapabilityState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CapabilityUIStateTest {
  @Test
  fun `hasUserChanges only considers override values when an exercise is ongoing`() {
    val heartRateCapabilityState = CapabilityState(true, WhsDataType.HEART_RATE_BPM.value(30))
    val upToDateState = UpToDateCapabilityUIState(heartRateCapabilityState)
    assertThat(upToDateState.hasUserChanges(ongoingExercise = true)).isFalse()

    val onlyAvailabilityChange =
      PendingUserChangesCapabilityUIState(
        upToDateState = heartRateCapabilityState,
        userState = heartRateCapabilityState.disable(),
      )
    assertThat(onlyAvailabilityChange.hasUserChanges(ongoingExercise = true)).isFalse()

    val onlyOverrideValueChange =
      PendingUserChangesCapabilityUIState(
        upToDateState = heartRateCapabilityState,
        userState = heartRateCapabilityState.override(WhsDataType.HEART_RATE_BPM.value(60)),
      )
    assertThat(onlyOverrideValueChange.hasUserChanges(ongoingExercise = true)).isTrue()

    val allChanges =
      PendingUserChangesCapabilityUIState(
        upToDateState = heartRateCapabilityState,
        userState =
          heartRateCapabilityState.override(WhsDataType.HEART_RATE_BPM.value(60)).disable(),
      )
    assertThat(allChanges.hasUserChanges(ongoingExercise = true)).isTrue()
  }

  @Test
  fun `hasUserChanges only considers availability changes when outside an exercise`() {
    val heartRateCapabilityState = CapabilityState(true, WhsDataType.HEART_RATE_BPM.value(30))
    val upToDateState = UpToDateCapabilityUIState(heartRateCapabilityState)
    assertThat(upToDateState.hasUserChanges(ongoingExercise = false)).isFalse()

    val onlyAvailabilityChange =
      PendingUserChangesCapabilityUIState(
        upToDateState = heartRateCapabilityState,
        userState = heartRateCapabilityState.disable(),
      )
    assertThat(onlyAvailabilityChange.hasUserChanges(ongoingExercise = false)).isTrue()

    val onlyOverrideValueChange =
      PendingUserChangesCapabilityUIState(
        upToDateState = heartRateCapabilityState,
        userState = heartRateCapabilityState.override(WhsDataType.HEART_RATE_BPM.value(60)),
      )
    assertThat(onlyOverrideValueChange.hasUserChanges(ongoingExercise = false)).isFalse()

    val allChanges =
      PendingUserChangesCapabilityUIState(
        upToDateState = heartRateCapabilityState,
        userState =
          heartRateCapabilityState.override(WhsDataType.HEART_RATE_BPM.value(60)).disable(),
      )
    assertThat(allChanges.hasUserChanges(ongoingExercise = false)).isTrue()
  }
}
