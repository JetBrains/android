/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import com.android.tools.adtui.instructions.NewRowInstruction
import com.android.tools.adtui.instructions.TextInstruction
import com.android.tools.adtui.model.Range
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Energy
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.TimeUnit

class EnergyEventsTableTooltipInfoComponentTest {
  private val modelRange = Range(0.0, 0.0)
  private val model = EnergyEventsTableTooltipInfoModel(modelRange, TimeUnit.MINUTES.toMillis(5))
  private val component = EnergyEventsTableTooltipInfoComponent(model)

  @Test
  fun wakeLockIsProperlyRendered() {
    val eventList = ImmutableList.of<Common.Event>(
      Common.Event.newBuilder()
        .setTimestamp(TimeUnit.SECONDS.toNanos(1))
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setWakeLockAcquired(Energy.WakeLockAcquired.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setTimestamp(TimeUnit.SECONDS.toNanos(3))
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setWakeLockReleased(Energy.WakeLockReleased.getDefaultInstance()))
        .setIsEnded(true)
        .build()
    )
    val duration = EnergyDuration(eventList)

    var timestampUs = 0.0
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions).isEmpty()

    timestampUs = TimeUnit.SECONDS.toMicros(1).toDouble()
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions.size).isEqualTo(6)
    assertThat((component.instructions[0] as TextInstruction).text).isEqualTo("Acquired")
    assertThat(component.instructions[1]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[2] as TextInstruction).text).isEqualTo("01.000 - 03.000")
    assertThat(component.instructions[3]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[4] as TextInstruction).text).isEqualTo("Duration")
    assertThat((component.instructions[5] as TextInstruction).text).isEqualTo(": 2 s")

    timestampUs = TimeUnit.SECONDS.toMicros(2).toDouble()
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions.size).isEqualTo(6)
    assertThat((component.instructions[0] as TextInstruction).text).isEqualTo("Active")
    assertThat(component.instructions[1]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[2] as TextInstruction).text).isEqualTo("01.000 - 03.000")
    assertThat(component.instructions[3]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[4] as TextInstruction).text).isEqualTo("Duration")
    assertThat((component.instructions[5] as TextInstruction).text).isEqualTo(": 2 s")

    timestampUs = TimeUnit.SECONDS.toMicros(3).toDouble()
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions.size).isEqualTo(6)
    assertThat((component.instructions[0] as TextInstruction).text).isEqualTo("Released")
    assertThat(component.instructions[1]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[2] as TextInstruction).text).isEqualTo("01.000 - 03.000")
    assertThat(component.instructions[3]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[4] as TextInstruction).text).isEqualTo("Duration")
    assertThat((component.instructions[5] as TextInstruction).text).isEqualTo(": 2 s")

    timestampUs = TimeUnit.SECONDS.toMicros(4).toDouble()
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions).isEmpty()
  }

  @Test
  fun incompleteWakeLockIsProperlyRendered() {
    val eventList = ImmutableList.of<Common.Event>(
      Common.Event.newBuilder()
        .setTimestamp(TimeUnit.SECONDS.toNanos(1))
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setWakeLockAcquired(Energy.WakeLockAcquired.getDefaultInstance()))
        .build()
    )
    val duration = EnergyDuration(eventList)

    var timestampUs = 0.0
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions).isEmpty()

    timestampUs = TimeUnit.SECONDS.toMicros(1).toDouble()
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions.size).isEqualTo(3)
    assertThat((component.instructions[0] as TextInstruction).text).isEqualTo("Acquired")
    assertThat(component.instructions[1]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[2] as TextInstruction).text).isEqualTo("01.000 - Unreleased")

    timestampUs = TimeUnit.SECONDS.toMicros(2).toDouble()
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions.size).isEqualTo(3)
    assertThat((component.instructions[0] as TextInstruction).text).isEqualTo("Active")
    assertThat(component.instructions[1]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[2] as TextInstruction).text).isEqualTo("01.000 - Unreleased")
  }

  @Test
  fun alarmIsProperlyRendered() {
    val eventList = ImmutableList.of<Common.Event>(
      Common.Event.newBuilder()
        .setTimestamp(TimeUnit.SECONDS.toNanos(1))
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setAlarmSet(Energy.AlarmSet.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setTimestamp(TimeUnit.SECONDS.toNanos(3))
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setAlarmFired(Energy.AlarmFired.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setTimestamp(TimeUnit.SECONDS.toNanos(4))
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setAlarmCancelled(Energy.AlarmCancelled.getDefaultInstance()))
        .setIsEnded(true)
        .build()
    )
    val duration = EnergyDuration(eventList)

    val dateString = model.getDateFormattedString(0)
    var timestampUs = 0.0
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions).isEmpty()

    timestampUs = TimeUnit.SECONDS.toMicros(1).toDouble()
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions.size).isEqualTo(9)
    assertThat((component.instructions[0] as TextInstruction).text).isEqualTo("Set")
    assertThat(component.instructions[1]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[2] as TextInstruction).text).isEqualTo("Created")
    assertThat((component.instructions[3] as TextInstruction).text).isEqualTo(": $dateString")
    assertThat((component.instructions[4] as TextInstruction).text).isEqualTo(" (01.000)")
    assertThat(component.instructions[5]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[6] as TextInstruction).text).isEqualTo("Next scheduled")
    assertThat((component.instructions[7] as TextInstruction).text).isEqualTo(": $dateString")
    assertThat((component.instructions[8] as TextInstruction).text).isEqualTo(" (03.000)")

    timestampUs = TimeUnit.SECONDS.toMicros(2).toDouble()
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions.size).isEqualTo(5)
    assertThat((component.instructions[0] as TextInstruction).text).isEqualTo("Active")
    assertThat(component.instructions[1]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[2] as TextInstruction).text).isEqualTo("Created")
    assertThat((component.instructions[3] as TextInstruction).text).isEqualTo(": $dateString")
    assertThat((component.instructions[4] as TextInstruction).text).isEqualTo(" (01.000)")

    timestampUs = TimeUnit.SECONDS.toMicros(3).toDouble()
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions.size).isEqualTo(5)
    assertThat((component.instructions[0] as TextInstruction).text).isEqualTo("Triggered")
    assertThat(component.instructions[1]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[2] as TextInstruction).text).isEqualTo("Created")
    assertThat((component.instructions[3] as TextInstruction).text).isEqualTo(": $dateString")
    assertThat((component.instructions[4] as TextInstruction).text).isEqualTo(" (01.000)")

    timestampUs = TimeUnit.SECONDS.toMicros(4).toDouble()
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions.size).isEqualTo(5)
    assertThat((component.instructions[0] as TextInstruction).text).isEqualTo("Cancelled")
    assertThat(component.instructions[1]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[2] as TextInstruction).text).isEqualTo("Created")
    assertThat((component.instructions[3] as TextInstruction).text).isEqualTo(": $dateString")
    assertThat((component.instructions[4] as TextInstruction).text).isEqualTo(" (01.000)")

    timestampUs = TimeUnit.SECONDS.toMicros(5).toDouble()
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions).isEmpty()
  }

  @Test
  fun jobIsProperlyRendered() {
    val eventList = ImmutableList.of<Common.Event>(
      Common.Event.newBuilder()
        .setTimestamp(TimeUnit.SECONDS.toNanos(1))
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobScheduled(Energy.JobScheduled.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setTimestamp(TimeUnit.SECONDS.toNanos(3))
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobStarted(Energy.JobStarted.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setTimestamp(TimeUnit.SECONDS.toNanos(4))
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobStopped(Energy.JobStopped.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setTimestamp(TimeUnit.SECONDS.toNanos(5))
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobFinished(Energy.JobFinished.getDefaultInstance()))
        .setIsEnded(true)
        .build()
    )
    val duration = EnergyDuration(eventList)

    var timestampUs = 0.0
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions).isEmpty()

    timestampUs = TimeUnit.SECONDS.toMicros(1).toDouble()
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions.size).isEqualTo(6)
    assertThat((component.instructions[0] as TextInstruction).text).isEqualTo("Scheduled")
    assertThat(component.instructions[1]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[2] as TextInstruction).text).isEqualTo("01.000 - 05.000")
    assertThat(component.instructions[3]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[4] as TextInstruction).text).isEqualTo("Duration")
    assertThat((component.instructions[5] as TextInstruction).text).isEqualTo(": 4 s")

    timestampUs = TimeUnit.SECONDS.toMicros(2).toDouble()
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions.size).isEqualTo(6)
    assertThat((component.instructions[0] as TextInstruction).text).isEqualTo("Active")
    assertThat(component.instructions[1]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[2] as TextInstruction).text).isEqualTo("01.000 - 05.000")
    assertThat(component.instructions[3]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[4] as TextInstruction).text).isEqualTo("Duration")
    assertThat((component.instructions[5] as TextInstruction).text).isEqualTo(": 4 s")

    timestampUs = TimeUnit.SECONDS.toMicros(3).toDouble()
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions.size).isEqualTo(6)
    assertThat((component.instructions[0] as TextInstruction).text).isEqualTo("Started")
    assertThat(component.instructions[1]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[2] as TextInstruction).text).isEqualTo("01.000 - 05.000")
    assertThat(component.instructions[3]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[4] as TextInstruction).text).isEqualTo("Duration")
    assertThat((component.instructions[5] as TextInstruction).text).isEqualTo(": 4 s")

    timestampUs = TimeUnit.SECONDS.toMicros(4).toDouble()
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions.size).isEqualTo(6)
    assertThat((component.instructions[0] as TextInstruction).text).isEqualTo("Stopped")
    assertThat(component.instructions[1]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[2] as TextInstruction).text).isEqualTo("01.000 - 05.000")
    assertThat(component.instructions[3]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[4] as TextInstruction).text).isEqualTo("Duration")
    assertThat((component.instructions[5] as TextInstruction).text).isEqualTo(": 4 s")

    timestampUs = TimeUnit.SECONDS.toMicros(5).toDouble()
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions.size).isEqualTo(6)
    assertThat((component.instructions[0] as TextInstruction).text).isEqualTo("Finished")
    assertThat(component.instructions[1]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[2] as TextInstruction).text).isEqualTo("01.000 - 05.000")
    assertThat(component.instructions[3]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[4] as TextInstruction).text).isEqualTo("Duration")
    assertThat((component.instructions[5] as TextInstruction).text).isEqualTo(": 4 s")

    timestampUs = TimeUnit.SECONDS.toMicros(6).toDouble()
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions).isEmpty()
  }

  @Test
  fun incompleteJobIsProperlyRendered() {
    val eventList = ImmutableList.of<Common.Event>(
      Common.Event.newBuilder()
        .setTimestamp(TimeUnit.SECONDS.toNanos(1))
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobScheduled(Energy.JobScheduled.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setTimestamp(TimeUnit.SECONDS.toNanos(3))
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobStarted(Energy.JobStarted.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setTimestamp(TimeUnit.SECONDS.toNanos(4))
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setJobStopped(Energy.JobStopped.getDefaultInstance()))
        .build()
    )
    val duration = EnergyDuration(eventList)

    var timestampUs = 0.0
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions).isEmpty()

    timestampUs = TimeUnit.SECONDS.toMicros(1).toDouble()
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions.size).isEqualTo(3)
    assertThat((component.instructions[0] as TextInstruction).text).isEqualTo("Scheduled")
    assertThat(component.instructions[1]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[2] as TextInstruction).text).isEqualTo("01.000 - Unfinished")

    timestampUs = TimeUnit.SECONDS.toMicros(2).toDouble()
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions.size).isEqualTo(3)
    assertThat((component.instructions[0] as TextInstruction).text).isEqualTo("Active")
    assertThat(component.instructions[1]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[2] as TextInstruction).text).isEqualTo("01.000 - Unfinished")

    timestampUs = TimeUnit.SECONDS.toMicros(3).toDouble()
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions.size).isEqualTo(3)
    assertThat((component.instructions[0] as TextInstruction).text).isEqualTo("Started")
    assertThat(component.instructions[1]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[2] as TextInstruction).text).isEqualTo("01.000 - Unfinished")

    timestampUs = TimeUnit.SECONDS.toMicros(4).toDouble()
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions.size).isEqualTo(3)
    assertThat((component.instructions[0] as TextInstruction).text).isEqualTo("Stopped")
    assertThat(component.instructions[1]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[2] as TextInstruction).text).isEqualTo("01.000 - Unfinished")

    timestampUs = TimeUnit.SECONDS.toMicros(5).toDouble()
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions.size).isEqualTo(3)
    assertThat((component.instructions[0] as TextInstruction).text).isEqualTo("Active")
    assertThat(component.instructions[1]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[2] as TextInstruction).text).isEqualTo("01.000 - Unfinished")
  }

  @Test
  fun locationIsProperlyRendered() {
    val eventList = ImmutableList.of<Common.Event>(
      Common.Event.newBuilder()
        .setTimestamp(TimeUnit.SECONDS.toNanos(1))
        .setEnergyEvent(
          Energy.EnergyEventData.newBuilder()
            .setLocationUpdateRequested(
              Energy.LocationUpdateRequested.newBuilder()
                .setRequest(
                  Energy.LocationRequest.newBuilder()
                    .setPriority(Energy.LocationRequest.Priority.LOW_POWER)
                    .setIntervalMs(1000))))
        .build(),
      Common.Event.newBuilder()
        .setTimestamp(TimeUnit.SECONDS.toNanos(3))
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setLocationChanged(Energy.LocationChanged.getDefaultInstance()))
        .build(),
      Common.Event.newBuilder()
        .setTimestamp(TimeUnit.SECONDS.toNanos(4))
        .setEnergyEvent(Energy.EnergyEventData.newBuilder().setLocationUpdateRemoved(Energy.LocationUpdateRemoved.getDefaultInstance()))
        .setIsEnded(true)
        .build()
    )
    val duration = EnergyDuration(eventList)
    var timestampUs = 0.0
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions).isEmpty()

    timestampUs = TimeUnit.SECONDS.toMicros(1).toDouble()
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions.size).isEqualTo(4)
    assertThat((component.instructions[0] as TextInstruction).text).isEqualTo("Requested")
    assertThat(component.instructions[1]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[2] as TextInstruction).text).isEqualTo("Priority")
    assertThat((component.instructions[3] as TextInstruction).text).isEqualTo(": LOW_POWER")

    timestampUs = TimeUnit.SECONDS.toMicros(2).toDouble()
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions.size).isEqualTo(7)
    assertThat((component.instructions[0] as TextInstruction).text).isEqualTo("Active")
    assertThat(component.instructions[1]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[2] as TextInstruction).text).isEqualTo("Priority")
    assertThat((component.instructions[3] as TextInstruction).text).isEqualTo(": LOW_POWER")
    assertThat(component.instructions[4]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[5] as TextInstruction).text).isEqualTo("Frequency")
    assertThat((component.instructions[6] as TextInstruction).text).isEqualTo(": Every 1 s")

    timestampUs = TimeUnit.SECONDS.toMicros(3).toDouble()
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions.size).isEqualTo(4)
    assertThat((component.instructions[0] as TextInstruction).text).isEqualTo("Location Updated")
    assertThat(component.instructions[1]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[2] as TextInstruction).text).isEqualTo("Priority")
    assertThat((component.instructions[3] as TextInstruction).text).isEqualTo(": LOW_POWER")

    timestampUs = TimeUnit.SECONDS.toMicros(4).toDouble()
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions.size).isEqualTo(4)
    assertThat((component.instructions[0] as TextInstruction).text).isEqualTo("Request Removed")
    assertThat(component.instructions[1]).isInstanceOf(NewRowInstruction::class.java)
    assertThat((component.instructions[2] as TextInstruction).text).isEqualTo("Priority")
    assertThat((component.instructions[3] as TextInstruction).text).isEqualTo(": LOW_POWER")

    timestampUs = TimeUnit.SECONDS.toMicros(5).toDouble()
    model.update(duration, Range(timestampUs, timestampUs))
    assertThat(component.instructions).isEmpty()
  }
}