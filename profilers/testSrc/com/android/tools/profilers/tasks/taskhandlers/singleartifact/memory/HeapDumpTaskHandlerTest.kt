/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.profilers.tasks.taskhandlers.singleartifact.memory

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.HeapDump
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.Process.ExposureLevel
import com.android.tools.profiler.proto.Memory
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.SessionArtifactUtils.createHprofSessionArtifact
import com.android.tools.profilers.SessionArtifactUtils.createSessionItem
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.tasks.taskhandlers.TaskHandlerTestUtils
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.HeapProfdSessionArtifact
import com.android.tools.profilers.memory.MainMemoryProfilerStage
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.args.singleartifact.memory.HeapDumpTaskArgs
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFailsWith

class HeapDumpTaskHandlerTest {
  private val myTimer = FakeTimer()
  private val ideProfilerServices = FakeIdeProfilerServices().apply {
    enableTaskBasedUx(true)
  }
  private val myTransportService = FakeTransportService(myTimer, false,  ideProfilerServices.featureConfig.isTaskBasedUxEnabled)

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("HeapDumpTaskHandlerTestChannel", myTransportService, FakeEventService())

  private lateinit var myProfilers: StudioProfilers
  private lateinit var myManager: SessionsManager
  private lateinit var myHeapDumpTaskHandler: HeapDumpTaskHandler

  @Before
  fun setup() {
    myProfilers = StudioProfilers(
      ProfilerClient(myGrpcChannel.channel),
      ideProfilerServices,
      myTimer
    )
    myManager = myProfilers.sessionsManager
    myHeapDumpTaskHandler = HeapDumpTaskHandler(myManager)
    myProfilers.addTaskHandler(ProfilerTaskType.HEAP_DUMP, myHeapDumpTaskHandler)
    assertThat(myManager.sessionArtifacts).isEmpty()
    assertThat(myManager.selectedSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myManager.profilingSession).isEqualTo(Common.Session.getDefaultInstance())
  }

  @Test
  fun testSetupStageCalledOnEnterAndSetsStageCorrectly() {
    val hprofSessionArtifact = createHprofSessionArtifact(myProfilers, Common.Session.getDefaultInstance(), 1L, 100L)
    val heapDumpArgs = HeapDumpTaskArgs(artifact = hprofSessionArtifact)
    // Verify that the stage is not set in the StudioProfilers stage management before the call to setupStage.
    assertThat(myProfilers.stage).isNotInstanceOf(MainMemoryProfilerStage::class.java)
    // Verify that the current stage stored in the MemoryTaskHandler is not set before the call to setupStage.
    myHeapDumpTaskHandler.enter(heapDumpArgs)
    // Verify that the stage set in the StudioProfilers stage management is correct.
    assertThat(myProfilers.stage).isInstanceOf(MainMemoryProfilerStage::class.java)
  }

  @Test
  fun testSupportsArtifactWithHprofArtifact() {
    val hprofSessionArtifact = createHprofSessionArtifact(myProfilers, Common.Session.getDefaultInstance(), 1L, 100L)
    assertThat(myHeapDumpTaskHandler.supportsArtifact(hprofSessionArtifact)).isTrue()
  }

  @Test
  fun testSupportsArtifactWithNonHprofArtifact() {
    val heapProfdSessionArtifact = HeapProfdSessionArtifact(myProfilers, Common.Session.getDefaultInstance(),
                                                            Common.SessionMetaData.getDefaultInstance(),
                                                            Trace.TraceInfo.getDefaultInstance())
    assertThat(myHeapDumpTaskHandler.supportsArtifact(heapProfdSessionArtifact)).isFalse()
  }

  @Test
  fun testStartTaskInvokedOnEnterWithAliveSession() {
    TaskHandlerTestUtils.startSession(ExposureLevel.DEBUGGABLE, myProfilers, myTransportService, myTimer, Common.ProfilerTaskType.HEAP_DUMP)
    val hprofSessionArtifact = createHprofSessionArtifact(myProfilers, Common.Session.getDefaultInstance(), 1L, 100L)
    val heapDumpArgs = HeapDumpTaskArgs(artifact = hprofSessionArtifact)
    myHeapDumpTaskHandler.enter(heapDumpArgs)
    // The session is alive, so startTask and thus startCapture should be called.
    assertThat(myHeapDumpTaskHandler.stage!!.recordingOptionsModel.isRecording).isTrue()
  }

  @Test
  fun testStartTaskWithSetStage() {
    TaskHandlerTestUtils.startSession(ExposureLevel.DEBUGGABLE, myProfilers, myTransportService, myTimer, Common.ProfilerTaskType.HEAP_DUMP)
    // To start the task and thus the capture, the stage must be set up before. This will be taken care of via the setupStage() method call,
    // on enter of the task handler, but this test is testing the explicit invocation of startTask.
    myHeapDumpTaskHandler.setupStage()
    myHeapDumpTaskHandler.startTask(HeapDumpTaskArgs(false, null))
    assertThat(myHeapDumpTaskHandler.stage!!.recordingOptionsModel.isRecording).isTrue()
  }

  @Test
  fun testStartTaskWithUnsetStage() {
    // To start the task and thus the capture, the stage must be set up before. Here we will test the case where startTask is invoked
    // without the stage being set precondition being met.
    val exception = assertFailsWith<Throwable> {
      myHeapDumpTaskHandler.startTask(HeapDumpTaskArgs(false, null))
    }
    assertThat(myHeapDumpTaskHandler.stage).isNull()
    assertThat(exception.message).isEqualTo("There was an error with the Heap Dump task. Error message: Cannot start the task as the " +
                                            "InterimStage was null.")
  }

  @Test
  fun testStopTaskSuccessfullyTerminatesTasksSession() {
    TaskHandlerTestUtils.startSession(ExposureLevel.DEBUGGABLE, myProfilers, myTransportService, myTimer,
                                      Common.ProfilerTaskType.HEAP_DUMP)

    (myTransportService.getRegisteredCommand(Commands.Command.CommandType.HEAP_DUMP) as HeapDump).apply {
      dumpStatus = Memory.HeapDumpStatus.Status.SUCCESS
    }
    myHeapDumpTaskHandler.setupStage()
    myHeapDumpTaskHandler.startTask(HeapDumpTaskArgs(false, null))
    // Wait for successful end event to be consumed.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    // Issuing a heap dump command should result in the session terminating as well.
    assertThat(myManager.isSessionAlive).isFalse()
  }

  @Test
  fun testLoadTaskInvokedOnEnterWithDeadSession() {
    // Before enter + loadTask, the stage should not be set yet.
    assertThat(myProfilers.stage).isNotInstanceOf(MainMemoryProfilerStage::class.java)

    // Create a fake HprofSessionArtifact.
    val hprofSessionArtifact = createHprofSessionArtifact(myProfilers, Common.Session.getDefaultInstance(), 1L, 100L)
    val heapDumpArgs = HeapDumpTaskArgs(artifact = hprofSessionArtifact)
    // The session is not alive (dead) so loadTask and thus loadCapture should be called.
    val argsSuccessfullyUsed = myHeapDumpTaskHandler.enter(heapDumpArgs)
    assertThat(argsSuccessfullyUsed).isTrue()

    // Verify that the artifact doSelect behavior is called by checking if the stage was set to MainMemoryProfilerStage.
    assertThat(myProfilers.stage).isInstanceOf(MainMemoryProfilerStage::class.java)
  }

  @Test
  fun testLoadTaskWithNonNullTaskArgs() {
    // Before enter + loadTask, the stage should not be set yet.
    assertThat(myProfilers.stage).isNotInstanceOf(MainMemoryProfilerStage::class.java)

    val hprofSessionArtifact = createHprofSessionArtifact(myProfilers, Common.Session.getDefaultInstance(), 1L, 100L)
    val heapDumpArgs = HeapDumpTaskArgs(artifact = hprofSessionArtifact)
    val argsSuccessfullyUsed = myHeapDumpTaskHandler.loadTask(heapDumpArgs)
    assertThat(argsSuccessfullyUsed).isTrue()

    // Verify that the artifact doSelect behavior was called by checking if the stage was set to MainMemoryProfilerStage.
    assertThat(myProfilers.stage).isInstanceOf(MainMemoryProfilerStage::class.java)
  }

  @Test
  fun testLoadTaskWithNullTaskArgsArtifact() {
    // Before enter + loadTask, the stage should not be set yet.
    assertThat(myProfilers.stage).isNotInstanceOf(MainMemoryProfilerStage::class.java)

    val exception = assertFailsWith<Throwable> {
      myHeapDumpTaskHandler.loadTask(HeapDumpTaskArgs(false, null))
    }

    assertThat(exception.message).isEqualTo(
      "There was an error with the Heap Dump task. Error message: The task arguments (HeapDumpTaskArgs) supplied do not contains a valid " +
      "artifact to load.")

    // Verify that the artifact doSelect behavior was not called by checking if the stage was not set to MainMemoryProfilerStage.
    assertThat(myProfilers.stage).isNotInstanceOf(MainMemoryProfilerStage::class.java)
  }

  @Test
  fun testCreateArgsSuccessfully() {
    val selectedSession = Common.Session.newBuilder().setSessionId(1).setEndTimestamp(100).build()
    val sessionIdToSessionItems = mapOf(
      1L to createSessionItem(myProfilers, selectedSession, 1, listOf(createHprofSessionArtifact(myProfilers, selectedSession, 1, 100))),
    )

    val heapDumpTaskArgs = myHeapDumpTaskHandler.createArgs(false, sessionIdToSessionItems, selectedSession)
    assertThat(heapDumpTaskArgs).isNotNull()
    assertThat(heapDumpTaskArgs).isInstanceOf(HeapDumpTaskArgs::class.java)
    heapDumpTaskArgs as HeapDumpTaskArgs
    assertThat(heapDumpTaskArgs.getMemoryCaptureArtifact()).isNotNull()
    assertThat(heapDumpTaskArgs.getMemoryCaptureArtifact()!!.artifactProto.startTime).isEqualTo(1L)
    assertThat(heapDumpTaskArgs.getMemoryCaptureArtifact()!!.artifactProto.endTime).isEqualTo(100L)
  }

  @Test
  fun testCreateArgsFailsToFindArtifact() {
    // By setting a session id that does not match any of the session items, the task artifact will not be found in the call to createArgs
    // will fail to be constructed.
    val selectedSession = Common.Session.newBuilder().setSessionId(0).setEndTimestamp(100).build()
    val sessionIdToSessionItems = mapOf(
      1L to createSessionItem(myProfilers, selectedSession, 1, listOf(createHprofSessionArtifact(myProfilers, selectedSession, 1, 100))),
    )

    assertThrows(IllegalStateException::class.java) {
      myHeapDumpTaskHandler.createArgs(false, sessionIdToSessionItems, selectedSession)
    }
  }

  @Test
  fun testSupportsDeviceAndProcess() {
    // Heap Dump task only checks the process support, so device used does not matter in call to supportsDeviceAndProcess.
    val device = TaskHandlerTestUtils.createDevice(1)

    val profileableProcess = TaskHandlerTestUtils.createProcess(isProfileable = true)
    assertThat(myHeapDumpTaskHandler.supportsDeviceAndProcess(device, profileableProcess)).isFalse()

    val debuggableProcess = TaskHandlerTestUtils.createProcess(isProfileable = false)
    assertThat(myHeapDumpTaskHandler.supportsDeviceAndProcess(device, debuggableProcess)).isTrue()
  }

  @Test
  fun testGetTaskName() {
    assertThat(myHeapDumpTaskHandler.getTaskName()).isEqualTo("Heap Dump")
  }
}