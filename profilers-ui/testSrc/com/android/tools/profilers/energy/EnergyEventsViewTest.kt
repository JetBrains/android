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

import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Energy
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Arrays

class EnergyEventsViewTest {
  @Test
  fun testWakeLockColumnValues() {
    val wakeLockAcquired = Energy.WakeLockAcquired.newBuilder()
      .setLevel(Energy.WakeLockAcquired.Level.PARTIAL_WAKE_LOCK)
      .setTag("wakeLockTag")
      .build()
    val duration = EnergyDuration(Arrays.asList(
      Common.Event.newBuilder()
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setWakeLockAcquired(wakeLockAcquired))
        .build()))
    assertThat(EnergyEventsView.Column.EVENT.getValueFrom(duration)).isEqualTo("Wake Lock: Partial")
    assertThat(EnergyEventsView.Column.DESCRIPTION.getValueFrom(duration)).isEqualTo("wakeLockTag")
  }

  @Test
  fun testAlarmColumnValuesWithPendingIntent() {
    val alarmSet = Energy.AlarmSet.newBuilder()
      .setType(Energy.AlarmSet.Type.RTC)
      .setOperation(Energy.PendingIntent.newBuilder().setCreatorPackage("package").setCreatorUid(123).build())
      .build()
    val duration = EnergyDuration(Arrays.asList(
      Common.Event.newBuilder()
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setAlarmSet(alarmSet))
        .build()))
    assertThat(EnergyEventsView.Column.EVENT.getValueFrom(duration)).isEqualTo("Alarm: RTC")
    assertThat(EnergyEventsView.Column.DESCRIPTION.getValueFrom(duration)).isEqualTo("package")
  }

  @Test
  fun testAlarmColumnValuesWithListener() {
    val alarmSetWithListener = Energy.AlarmSet.newBuilder()
      .setType(Energy.AlarmSet.Type.ELAPSED_REALTIME_WAKEUP)
      .setListener(Energy.AlarmListener.newBuilder().setTag("listener").build()).build()
    val duration = EnergyDuration(Arrays.asList(
      Common.Event.newBuilder()
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setAlarmSet(alarmSetWithListener))
        .build()))
    assertThat(EnergyEventsView.Column.EVENT.getValueFrom(duration)).isEqualTo("Alarm: Elapsed Realtime Wakeup")
    assertThat(EnergyEventsView.Column.DESCRIPTION.getValueFrom(duration)).isEqualTo("listener")
  }

  @Test
  fun testJobColumnValues() {
    val jobScheduled = Energy.JobScheduled.newBuilder()
      .setJob(Energy.JobInfo.newBuilder().setJobId(111).setServiceName("service").build())
      .build()
    val duration = EnergyDuration(Arrays.asList(
      Common.Event.newBuilder()
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobScheduled(jobScheduled))
        .build()))
    assertThat(EnergyEventsView.Column.EVENT.getValueFrom(duration)).isEqualTo("Job")
    assertThat(EnergyEventsView.Column.DESCRIPTION.getValueFrom(duration)).isEqualTo("111:service")
  }

  @Test
  fun testUnknownColumnValues() {
    val duration = EnergyDuration(Arrays.asList(Common.Event.newBuilder().build()))
    assertThat(EnergyEventsView.Column.EVENT.getValueFrom(duration)).isEqualTo("N/A")
    assertThat(EnergyEventsView.Column.DESCRIPTION.getValueFrom(duration)).isEqualTo("N/A")
  }
}