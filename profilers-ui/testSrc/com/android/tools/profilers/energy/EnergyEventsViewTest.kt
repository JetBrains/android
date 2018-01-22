/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.energy

import com.android.tools.profiler.proto.EnergyProfiler
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEvent
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.TimeUnit

class EnergyEventsViewTest {
  private val wakeLockAcquired = EnergyProfiler.WakeLockAcquired.newBuilder().setTag("tag").setLevelAndFlags(1).build()
  private val wakeLockAcquire = EnergyEvent.newBuilder()
    .setTimestamp(TimeUnit.MILLISECONDS.toNanos(1000))
    .setWakeLockAcquired(wakeLockAcquired)
    .setEventId(1000)
    .build()
  private val wakeLockRelease = EnergyEvent.newBuilder()
    .setTimestamp(TimeUnit.MILLISECONDS.toNanos(1200))
    .setWakeLockReleased(EnergyProfiler.WakeLockReleased.getDefaultInstance())
    .setEventId(1000)
    .build()

  @Test
  fun expectColumnsValueProperlySet() {
    val duration = EventDuration(ImmutableList.of<EnergyEvent>(wakeLockAcquire, wakeLockRelease))
    assertThat(EnergyEventsView.Column.NAME.getValueFrom(duration)).isEqualTo("tag")
    assertThat(EnergyEventsView.Column.KIND.getValueFrom(duration)).isEqualTo("wakelock")
    assertThat(EnergyEventsView.Column.TIMELINE.getValueFrom(duration)).isEqualTo(TimeUnit.MILLISECONDS.toNanos(1000))
  }
}