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

import com.android.test.testutils.TestUtils
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.LeakCanary
import com.android.tools.profiler.proto.LeakCanary.LeakCanaryLogcatInfo
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.WithFakeTimer
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

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
    val startTime = System.currentTimeMillis()
    transportService.setCommandHandler(Commands.Command.CommandType.START_LOGCAT_TRACKING,
                                       FakeLeakCanaryCommandHandler(timer, profilers, listOf(
                                         "SingleApplicationLeak.txt", // 1 application leak
                                         "SingleApplicationLeakAnalyzeCmd.txt", // 1 application leak
                                         "MultiApplicationLeak.txt", // 2 application leak with different signature
                                         "NoLeak.txt"
                                       ), startTime))
    transportService.setCommandHandler(Commands.Command.CommandType.STOP_LOGCAT_TRACKING,
                                       FakeLeakCanaryCommandHandler(timer, profilers, listOf(), startTime))
    stage.startListening()
    // Wait for listener to receive events
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    // Verify leak events
    assertEquals(4, stage.leaks.value.size) // 4 events are sent
    stage.stopListening()
    // First leak is selected by default
    assertEquals(stage.leaks.value[0], stage.selectedLeak.value)
    // After stage exit we get all events
    assertEquals(4, stage.leaks.value.size) // 4 events are sent
    assertEquals(4, stage.leaks.value.size)
    // First leak is selected by default
    assertEquals(stage.leaks.value[0], stage.selectedLeak.value)
  }

  @Test
  fun `Leak canary stage enter - load from past`() {
    val startTime = System.currentTimeMillis()
    transportService.setCommandHandler(Commands.Command.CommandType.START_LOGCAT_TRACKING,
                                       FakeLeakCanaryCommandHandler(timer, profilers, listOf(
                                         "SingleApplicationLeak.txt", // 1 application leak
                                         "SingleApplicationLeakAnalyzeCmd.txt", // 1 application leak
                                         "MultiApplicationLeak.txt", // 2 application leak with different signature
                                         "NoLeak.txt"
                                       ), startTime))
    transportService.setCommandHandler(Commands.Command.CommandType.STOP_LOGCAT_TRACKING,
                                       FakeLeakCanaryCommandHandler(timer, profilers, listOf(), startTime))
    stage.startListening()
    // Wait for listener to receive events
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    stage.stopListening()
    val endTime = System.currentTimeMillis()
    assertEquals(4, stage.leaks.value.size)

    stage.clearLeaks()
    assertEmpty(stage.leaks.value)

    stage.loadFromPastSession(startTime, endTime, profilers.session)
    assertEquals(4, stage.leaks.value.size)

    // First leak is selected by default
    assertEquals(stage.leaks.value[0], stage.selectedLeak.value)
  }

  @Test
  fun `Leak canary stage enter - Invalid leaks are skipped`() {
    val startTime = System.currentTimeMillis()
    transportService.setCommandHandler(Commands.Command.CommandType.START_LOGCAT_TRACKING,
                                       FakeLeakCanaryCommandHandler(timer, profilers, listOf(
                                         "SingleApplicationLeak.txt",
                                         "InValidLeak.txt"
                                       ), startTime))
    transportService.setCommandHandler(Commands.Command.CommandType.STOP_LOGCAT_TRACKING,
                                       FakeLeakCanaryCommandHandler(timer, profilers, listOf(), startTime))
    stage.startListening()
    // Wait for listener to receive events
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    // Verify leakEvents
    assertEquals(1, stage.leaks.value.size) // 1 event is sent
    assertTrue(stage.isRecording.value)
    stage.stopListening()
    // After stage exit we get all events
    assertEquals(1, stage.leaks.value.size) // 1 event are sent
    assertEquals(1, stage.leaks.value.size)
    assertFalse(stage.isRecording.value)

  }

  @Test
  fun `Leak canary stage enter - no leak events`() {
    val startTime = System.currentTimeMillis()
    transportService.setCommandHandler(Commands.Command.CommandType.START_LOGCAT_TRACKING,
                                       FakeLeakCanaryCommandHandler(timer, profilers, listOf(), startTime))
    transportService.setCommandHandler(Commands.Command.CommandType.STOP_LOGCAT_TRACKING,
                                       FakeLeakCanaryCommandHandler(timer, profilers, listOf(), startTime))
    stage.startListening()
    // Wait for the listener to receive events
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    // Verify leakEvents
    assertEmpty(stage.leaks.value) // No events are sent
    stage.stopListening()
    // After stage exit we get all events
    assertEmpty(stage.leaks.value) // No events are sent

  }

  @Test
  fun `Leak canary stage enter - all leaks detected are not valid and test start and stop leakInfo events`() {
    val startTime = System.currentTimeMillis()
    transportService.setCommandHandler(Commands.Command.CommandType.START_LOGCAT_TRACKING,
                                       FakeLeakCanaryCommandHandler(timer, profilers, listOf(
                                         "InValidLeak.txt",
                                         "InValidLeak.txt",
                                         "InValidLeak.txt",
                                         "InValidLeak.txt"
                                       ), startTime))
    transportService.setCommandHandler(Commands.Command.CommandType.STOP_LOGCAT_TRACKING,
                                       FakeLeakCanaryCommandHandler(timer, profilers, listOf(), startTime))
    stage.startListening()
    // Wait for listener to receive events
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    // Verify leakEvents
    assertEmpty(stage.leaks.value) // 0 event are sent
    assertEquals(true, stage.isRecording.value)
    stage.stopListening()
    val endTime = System.currentTimeMillis()
    // After stage exit we get all events
    assertEmpty(stage.leaks.value) // 0 event are sent

    val leakInfoEvents = LeakCanaryModel.getLeakCanaryLogcatInfo(profilers.client, profilers.session,
                                                         Range(startTime.toDouble(), endTime.toDouble()))
    assertEquals(1, leakInfoEvents.size) // Fetching only ended events
    assertEquals(Common.Event.Kind.LEAKCANARY_LOGCAT_INFO, leakInfoEvents[0].kind)
    assertTrue(leakInfoEvents[0].isEnded)
    assertEquals(LeakCanary.LeakCanaryLogcatEnded.Status.SUCCESS, leakInfoEvents[0].leakCanaryLogcatInfo.logcatEnded.status)
    assertFalse(stage.isRecording.value)
  }
}

class FakeLeakCanaryCommandHandler(timer: FakeTimer,
                                   val profilers: StudioProfilers,
                                   val leaksToSendFiles: List<String>,
                                   val startTimestamp: Long) : CommandHandler(timer) {
  override fun handleCommand(command: Commands.Command,
                             events: MutableList<Common.Event>) {

    if (command.type == Commands.Command.CommandType.START_LOGCAT_TRACKING) {
      // Start tracking info event
      events.add(Common.Event.newBuilder()
                   .setGroupId(profilers.session.pid.toLong())
                   .setPid(profilers.session.pid)
                   .setIsEnded(false)
                   .setKind(Common.Event.Kind.LEAKCANARY_LOGCAT_INFO)
                   .setLeakCanaryLogcatInfo(LeakCanaryLogcatInfo.newBuilder()
                                              .setLogcatStarted(
                                                LeakCanary.LeakCanaryLogcatStarted
                                                  .newBuilder()
                                                  .setTimestamp(startTimestamp)
                                                  .build())
                                              .build())
                   .setTimestamp(startTimestamp)
                   .build())
      leaksToSendFiles.forEach {leakToSendFile ->
        events.add(getLeakCanaryEvent(profilers, leakToSendFile))
      }
    } else {
      // Stop tracking info event
      events.add(Common.Event.newBuilder()
                   .setGroupId(profilers.session.pid.toLong())
                   .setPid(profilers.session.pid)
                   .setIsEnded(true)
                   .setKind(Common.Event.Kind.LEAKCANARY_LOGCAT_INFO)
                   .setLeakCanaryLogcatInfo(LeakCanaryLogcatInfo.newBuilder()
                                              .setLogcatEnded(LeakCanary.LeakCanaryLogcatEnded
                                                               .newBuilder()
                                                               .setStatus(LeakCanary.LeakCanaryLogcatEnded.Status.SUCCESS)
                                                               .setStartTimestamp(startTimestamp)
                                                               .setEndTimestamp(System.currentTimeMillis())
                                                               .build())
                                              .build())
                   .setTimestamp(System.currentTimeMillis())
                   .build())

      // Stop session event
      Common.Event.newBuilder().setTimestamp(System.currentTimeMillis())
        .setPid(profilers.session.pid)
        .setGroupId(profilers.session.sessionId)
        .setKind(Common.Event.Kind.SESSION)
        .setIsEnded(true).build();
    }
  }

  companion object {
    const val TEST_DATA_PATH = "tools/adt/idea/profilers/testData/sampleLeaks/"

    fun getLeakCanaryEvent(profilers: StudioProfilers, leakToSendFile: String): Common.Event {
      val file = TestUtils.resolveWorkspacePath("${TEST_DATA_PATH}/$leakToSendFile").toFile()
      val fileContent = file.readText()
      val currentTime = System.currentTimeMillis()
      return Common.Event.newBuilder()
        .setGroupId(profilers.session.pid.toLong())
        .setPid(profilers.session.pid)
        .setKind(Common.Event.Kind.LEAKCANARY_LOGCAT)
        .setLeakcanaryLogcat(LeakCanary.LeakCanaryLogcatData
                               .newBuilder()
                               .setLogcatMessage(fileContent).build())
        .setTimestamp(currentTime)
        .build()
    }
  }
}