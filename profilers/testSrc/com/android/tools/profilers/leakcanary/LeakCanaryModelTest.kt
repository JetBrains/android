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
package com.android.tools.profilers.leakcanary

import com.android.testutils.TestUtils
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.LeakCanary
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.WithFakeTimer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class LeakCanaryModelTest : WithFakeTimer {
  override val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)
  @Rule
  @JvmField
  val grpcChannel = FakeGrpcChannel("LeakCanaryModelTestChannel", transportService)
  private lateinit var profilers: StudioProfilers
  private lateinit var stage: LeakCanaryModel
  private lateinit var ideProfilerServices: FakeIdeProfilerServices

  @Before
  fun setup() {
    ideProfilerServices = FakeIdeProfilerServices()
    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideProfilerServices, timer)
    stage = LeakCanaryModel(profilers)
  }

  @Test
  fun `Leak canary stage enter - success case with multiple events`() {
    transportService.setCommandHandler(Commands.Command.CommandType.START_LOGCAT_TRACKING,
                                       FakeLeakCanaryCommandHandler(timer, profilers, listOf(
                                         "SingleApplicationLeak.txt",
                                         "SingleApplicationLeakAnalyzeCmd.txt",
                                         "MultiApplicationLeak.txt",
                                         "NoLeak.txt"
                                       )))
    transportService.setCommandHandler(Commands.Command.CommandType.STOP_LOGCAT_TRACKING,
                                       FakeLeakCanaryCommandHandler(timer, profilers, listOf()))
    stage.startListening()
    // Wait for listener to receive events
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    // Verify leak events
    assertEquals(4, stage.leakEvents.size) // 4 events are sent
    assertEquals(4, stage.leaksDetectedCount.value) // All 4 events triggered a leak detected event
    stage.stopListening()
    // After stage exit we get all events
    assertEquals(4, stage.leakEvents.size) // 4 events are sent
    assertEquals(4, stage.leaksDetectedCount.value) // All 4 events triggered a leak detected event
  }

  @Test
  fun `Leak canary stage enter - Invalid leaks are skipped`() {
    transportService.setCommandHandler(Commands.Command.CommandType.START_LOGCAT_TRACKING,
                                       FakeLeakCanaryCommandHandler(timer, profilers, listOf(
                                         "SingleApplicationLeak.txt",
                                         "InValidLeak.txt"
                                       )))
    transportService.setCommandHandler(Commands.Command.CommandType.STOP_LOGCAT_TRACKING,
                                       FakeLeakCanaryCommandHandler(timer, profilers, listOf()))
    stage.startListening()
    // Wait for listener to receive events
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    // Verify leakEvents
    assertEquals(1, stage.leakEvents.size) // 1 event is sent
    assertEquals(1, stage.leaksDetectedCount.value) // 1 event triggered a leak detected event
    stage.stopListening()
    // After stage exit we get all events
    assertEquals(1, stage.leakEvents.size) // 1 event are sent
    assertEquals(1, stage.leaksDetectedCount.value) // 1 event triggered a leak detected event
  }

  @Test
  fun `Leak canary stage enter - no leak events`() {
    transportService.setCommandHandler(Commands.Command.CommandType.START_LOGCAT_TRACKING,
                                       FakeLeakCanaryCommandHandler(timer, profilers, listOf()))
    transportService.setCommandHandler(Commands.Command.CommandType.STOP_LOGCAT_TRACKING,
                                       FakeLeakCanaryCommandHandler(timer, profilers, listOf()))
    stage.startListening()
    // Wait for the listener to receive events
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    // Verify leakEvents
    assertEquals(0, stage.leakEvents.size) // No events are sent
    assertEquals(0, stage.leaksDetectedCount.value) // No leak detected event
    stage.stopListening()
    // After stage exit we get all events
    assertEquals(0, stage.leakEvents.size) // No events are sent
    assertEquals(0, stage.leaksDetectedCount.value) // No leak detected event
  }

  @Test
  fun `Leak canary stage enter - all leaks detected are not valid`() {
    transportService.setCommandHandler(Commands.Command.CommandType.START_LOGCAT_TRACKING,
                                       FakeLeakCanaryCommandHandler(timer, profilers, listOf(
                                         "InValidLeak.txt",
                                         "InValidLeak.txt",
                                         "InValidLeak.txt",
                                         "InValidLeak.txt"
                                       )))
    transportService.setCommandHandler(Commands.Command.CommandType.STOP_LOGCAT_TRACKING,
                                       FakeLeakCanaryCommandHandler(timer, profilers, listOf()))
    stage.startListening()
    // Wait for listener to receive events
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    // Verify leakEvents
    assertEquals(0, stage.leakEvents.size) // 0 event are sent
    assertEquals(0, stage.leaksDetectedCount.value) // No event triggered leak detected event
    stage.stopListening()
    // After stage exit we get all events
    assertEquals(0, stage.leakEvents.size) // 0 event are sent
    assertEquals(0, stage.leaksDetectedCount.value) // No event triggered leak detected event
  }
}

class FakeLeakCanaryCommandHandler(timer: FakeTimer,
                                   val profilers: StudioProfilers,
                                   val leaksToSendFiles: List<String>) : CommandHandler(timer) {
  override fun handleCommand(command: Commands.Command,
                             events: MutableList<Common.Event>) {
    leaksToSendFiles.forEach {leakToSendFile ->
      events.add(getLeakCanaryEvent(profilers, leakToSendFile))
    }
  }

  companion object {
    const val TEST_DATA_PATH = "tools/adt/idea/profilers/testData/sampleLeaks/"

    fun getLeakCanaryEvent(profilers: StudioProfilers, leakToSendFile: String): Common.Event {
      val file = TestUtils.resolveWorkspacePath("${TEST_DATA_PATH}/$leakToSendFile").toFile()
      val fileContent = file.readText()
      return Common.Event.newBuilder()
        .setGroupId(profilers.session.pid.toLong())
        .setPid(profilers.session.pid)
        .setKind(Common.Event.Kind.LEAKCANARY_LOGCAT)
        .setLeakcanaryLogcat(LeakCanary.LeakCanaryLogcatData
                               .newBuilder()
                               .setLogcatMessage(fileContent).build())
        .setTimestamp(System.nanoTime())
        .build()
    }
  }
}