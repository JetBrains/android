/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.metrics.statistics

import com.android.tools.idea.layoutinspector.model
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorMemory
import org.junit.Test

class MemoryStatisticsTest {

  @Test
  fun testStart() {
    val memory = MemoryStatistics(model {})
    memory.recordModelSize(true, 1000L * ONE_MB, 1000L)
    memory.start()
    val data = DynamicLayoutInspectorMemory.newBuilder()
    memory.save { data }
    assertThat(data.initialSnapshot.skiaImage).isFalse()
    assertThat(data.initialSnapshot.captureSizeMb).isEqualTo(0L)
    assertThat(data.initialSnapshot.measurementDurationMs).isEqualTo(0L)
    assertThat(data.initialSnapshot.skiaImage).isFalse()
    assertThat(data.largestSnapshot.captureSizeMb).isEqualTo(0L)
    assertThat(data.largestSnapshot.measurementDurationMs).isEqualTo(0L)
  }

  @Test
  fun testKeepLargestMemorySize() {
    val memory = MemoryStatistics(model {})
    memory.recordModelSize(true, 1000L * ONE_MB, 1000L)
    memory.recordModelSize(false, 10000L * ONE_MB, 500L)
    memory.recordModelSize(true, 100L * ONE_MB, 10000L)
    memory.recordModelSize(false, 1000L * ONE_MB, 1000L)
    val data = DynamicLayoutInspectorMemory.newBuilder()
    memory.save { data }
    assertThat(data.initialSnapshot.skiaImage).isTrue()
    assertThat(data.initialSnapshot.captureSizeMb).isEqualTo(1000L)
    assertThat(data.initialSnapshot.measurementDurationMs).isEqualTo(1000L)
    assertThat(data.largestSnapshot.skiaImage).isFalse()
    assertThat(data.largestSnapshot.captureSizeMb).isEqualTo(10000L)
    assertThat(data.largestSnapshot.measurementDurationMs).isEqualTo(500L)
  }
}
