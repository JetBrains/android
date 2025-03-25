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
package com.android.build.attribution.data

import com.android.build.attribution.data.AlwaysRunTaskData.Reason.NO_OUTPUTS_WITH_ACTIONS
import com.android.build.attribution.data.AlwaysRunTaskData.Reason.UP_TO_DATE_WHEN_FALSE
import com.google.common.truth.Truth
import org.gradle.api.internal.changedetection.changes.DefaultTaskExecutionMode
import org.junit.Test

class AlwaysRunTaskDataTest {

  @Test
  fun testFindMatchingReason() {
    // If this test fails after an update, need to update strings in AlwaysRunTaskData.Reason.findMatchingReason.
    // We will probably need to match both old and new message versions in case of a change.
    Truth.assertThat(AlwaysRunTaskData.Reason.findMatchingReason(DefaultTaskExecutionMode.noOutputs().rebuildReason.get())).isEqualTo(
      NO_OUTPUTS_WITH_ACTIONS
    )
    Truth.assertThat(AlwaysRunTaskData.Reason.findMatchingReason(DefaultTaskExecutionMode.upToDateWhenFalse().rebuildReason.get())).isEqualTo(
      UP_TO_DATE_WHEN_FALSE
    )
    Truth.assertThat(AlwaysRunTaskData.Reason.findMatchingReason(DefaultTaskExecutionMode.rerunTasksEnabled().rebuildReason.get())).isNull()
  }
}