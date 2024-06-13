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

import com.android.sdklib.AndroidVersion
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.MemoryAllocTracking
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.Process.ExposureLevel
import com.android.tools.profiler.proto.Memory
import com.android.tools.profiler.proto.Memory.TrackStatus
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.SessionArtifactUtils.createAllocationSessionArtifact
import com.android.tools.profilers.SessionArtifactUtils.createLegacyAllocationsSessionArtifact
import com.android.tools.profilers.SessionArtifactUtils.createSessionItem
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.AllocationStage
import com.android.tools.profilers.memory.HeapProfdSessionArtifact
import com.android.tools.profilers.memory.MainMemoryProfilerStage
import com.android.tools.profilers.memory.MemoryProfilerTestUtils
import com.android.tools.profilers.sessions.SessionsManager
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.args.singleartifact.memory.JavaKotlinAllocationsTaskArgs
import com.android.tools.profilers.tasks.args.singleartifact.memory.LegacyJavaKotlinAllocationsTaskArgs
import com.android.tools.profilers.tasks.taskhandlers.TaskHandlerTestUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFailsWith

class JavaKotlinAllocationsTaskHandlerTest {
  private val myTimer = FakeTimer()
  private val ideProfilerServices = FakeIdeProfilerServices().apply {
    enableTaskBasedUx(true)
  }
  private val myTransportService = FakeTransportService(myTimer, false,  ideProfilerServices.featureConfig.isTaskBasedUxEnabled)

  @get:Rule
  var myGrpcChannel = FakeGrpcChannel("JavaKotlinAllocationsTaskHandlerTestChannel", myTransportService, FakeEventService())

  private lateinit var myProfilers: StudioProfilers
  private lateinit var myManager: SessionsManager
  private lateinit var myJavaKotlinAllocationsTaskHandler: JavaKotlinAllocationsTaskHandler

  @Before
  fun setup() {
    myProfilers = StudioProfilers(
      ProfilerClient(myGrpcChannel.channel),
      ideProfilerServices,
      myTimer
    )
    myManager = myProfilers.sessionsManager
    myJavaKotlinAllocationsTaskHandler = JavaKotlinAllocationsTaskHandler(myManager)
    myProfilers.addTaskHandler(ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS, myJavaKotlinAllocationsTaskHandler)
    assertThat(myManager.sessionArtifacts).isEmpty()
    assertThat(myManager.selectedSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myManager.profilingSession).isEqualTo(Common.Session.getDefaultInstance())
  }

  @Test
  fun testSetupStageCalledOnEnterAndSetsStageCorrectly() {
    val allocationsSessionArtifact = createAllocationSessionArtifact(myProfilers, Common.Session.getDefaultInstance(), 1L, 100L)
    val allocationsTaskArgs = JavaKotlinAllocationsTaskArgs(artifact = allocationsSessionArtifact)
    // Verify that the stage is not set in the StudioProfilers stage management before the call to setupStage.
    assertThat(myProfilers.stage).isNotInstanceOf(MainMemoryProfilerStage::class.java)
    // Verify that the current stage stored in the MemoryTaskHandler is not set before the call to setupStage.
    myJavaKotlinAllocationsTaskHandler.enter(allocationsTaskArgs)
    // Verify that the stage set in the StudioProfilers stage management is correct.
    assertThat(myProfilers.stage).isInstanceOf(AllocationStage::class.java)
  }

  @Test
  fun testSetupStageCalledOnEnterAndSetsStageCorrectlyWithLegacyAllocationsSessionArtifact() {
    val legacyAllocationsSessionArtifact = createLegacyAllocationsSessionArtifact(myProfilers, Common.Session.getDefaultInstance(), 1L,
                                                                                  100L)
    val legacyAllocationsTaskArgs = LegacyJavaKotlinAllocationsTaskArgs(artifact = legacyAllocationsSessionArtifact)
    // Verify that the stage is not set in the StudioProfilers stage management before the call to setupStage.
    assertThat(myProfilers.stage).isNotInstanceOf(MainMemoryProfilerStage::class.java)
    // Verify that the current stage stored in the MemoryTaskHandler is not set before the call to setupStage.
    myJavaKotlinAllocationsTaskHandler.enter(legacyAllocationsTaskArgs)
    // Verify that the stage set in the StudioProfilers stage management is correct.
    assertThat(myProfilers.stage).isInstanceOf(MainMemoryProfilerStage::class.java)
  }

  @Test
  fun testSupportsArtifactWithAllocationsArtifact() {
    val allocationsSessionArtifact = createAllocationSessionArtifact(myProfilers, Common.Session.getDefaultInstance(), 1L, 100L)
    assertThat(myJavaKotlinAllocationsTaskHandler.supportsArtifact(allocationsSessionArtifact)).isTrue()
  }

  @Test
  fun testSupportsArtifactWithLegacyAllocationsSessionArtifact() {
    val legacyAllocationsSessionArtifact = createLegacyAllocationsSessionArtifact(myProfilers, Common.Session.getDefaultInstance(), 1L,
                                                                                  100L)
    assertThat(myJavaKotlinAllocationsTaskHandler.supportsArtifact(legacyAllocationsSessionArtifact)).isTrue()
  }

  @Test
  fun testSupportsArtifactWithNonAllocationsArtifact() {
    val heapProfdSessionArtifact = HeapProfdSessionArtifact(myProfilers, Common.Session.getDefaultInstance(),
                                                            Common.SessionMetaData.getDefaultInstance(),
                                                            Trace.TraceInfo.getDefaultInstance())
    assertThat(myJavaKotlinAllocationsTaskHandler.supportsArtifact(heapProfdSessionArtifact)).isFalse()
  }

  @Test
  fun testStartTaskInvokedOnEnterWithAliveSession() {
    TaskHandlerTestUtils.startSession(ExposureLevel.DEBUGGABLE, myProfilers, myTransportService, myTimer,
                                      Common.ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS)
    val allocationsSessionArtifact = createAllocationSessionArtifact(myProfilers, Common.Session.getDefaultInstance(), 1L, 100L)
    val allocationsTaskArgs = JavaKotlinAllocationsTaskArgs(artifact = allocationsSessionArtifact)
    myJavaKotlinAllocationsTaskHandler.enter(allocationsTaskArgs)
    // The session is alive, so startTask and thus startCapture should be called.
    assertThat(myJavaKotlinAllocationsTaskHandler.stage!!.recordingOptionsModel.isRecording)
  }

  @Test
  fun testStartTaskWithSetStage() {
    TaskHandlerTestUtils.startSession(ExposureLevel.DEBUGGABLE, myProfilers, myTransportService, myTimer,
                                      Common.ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS)
    // To start the task and thus the capture, the stage must be set up before. This will be taken care of via the setupStage() method call,
    // on enter of the task handler, but this test is testing the explicit invocation of startTask.
    myJavaKotlinAllocationsTaskHandler.setupStage()
    myJavaKotlinAllocationsTaskHandler.startTask(JavaKotlinAllocationsTaskArgs(false, null))
    assertThat(myJavaKotlinAllocationsTaskHandler.stage!!.recordingOptionsModel.isRecording).isTrue()
  }

  @Test
  fun testStartTaskWithUnsetStage() {
    // To start the task and thus the capture, the stage must be set up before. Here we will test the case where startTask is invoked
    // without the stage being set precondition being met.
    val exception = assertFailsWith<Throwable> {
      myJavaKotlinAllocationsTaskHandler.startTask(JavaKotlinAllocationsTaskArgs(false, null))
    }
    assertThat(myJavaKotlinAllocationsTaskHandler.stage).isNull()
    assertThat(exception.message).isEqualTo(
      "There was an error with the Java/Kotlin Allocations task. Error message: Cannot start the task as the InterimStage was null.")
  }

  @Test
  fun testStopTaskSuccessfullyTerminatesTasksSession() {
    TaskHandlerTestUtils.startSession(ExposureLevel.DEBUGGABLE, myProfilers, myTransportService, myTimer,
                                      Common.ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS)
    // Set the start allocation tracking status to be successful.
    (myTransportService.getRegisteredCommand(Commands.Command.CommandType.START_ALLOC_TRACKING) as MemoryAllocTracking).apply {
      trackStatus = TrackStatus.newBuilder().setStatus(TrackStatus.Status.SUCCESS).build()
    }
    // In order to proceed with the allocation tracking, a MEMORY_ALLOC_TRACKING event is expected with underlying data populated.
    // This data is faked (as well as the hard coded range) to simulate the data that would normally be fetched in a production scenario.
    myTransportService.addEventToStream(1234, Common.Event.newBuilder().setPid(1).setKind(
      Common.Event.Kind.MEMORY_ALLOC_TRACKING).setMemoryAllocTracking(
      Memory.MemoryAllocTrackingData.newBuilder().setInfo(
        Memory.AllocationsInfo.newBuilder().setStartTime(0).setEndTime(1).setLegacy(false).build()).build()).build())
    myProfilers.timeline.dataRange.set(0.0, 1.0)

    myJavaKotlinAllocationsTaskHandler.setupStage()
    myJavaKotlinAllocationsTaskHandler.startTask(JavaKotlinAllocationsTaskArgs(false, null))
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)

    // Because the Java/Kotlin task's stop is self-contained within the AllocationStage, we must force stop tracking allocations.
    MemoryProfilerTestUtils.stopTrackingHelper(myJavaKotlinAllocationsTaskHandler.stage!!, myTransportService, myTimer, 0,
                                               TrackStatus.Status.SUCCESS, false)
    // Wait for successful end event to be consumed.
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    // Issuing a stop allocation tracking command should result in the session terminating as well.
    assertThat(myManager.isSessionAlive).isFalse()
  }

  @Test
  fun testLoadTaskInvokedOnEnterWithDeadSession() {
    // Before enter + loadTask, the stage should not be set yet.
    assertThat(myProfilers.stage).isNotInstanceOf(MainMemoryProfilerStage::class.java)

    // Create a fake AllocationSessionArtifact.
    val allocationsSessionArtifact = createAllocationSessionArtifact(myProfilers, Common.Session.getDefaultInstance(), 1L, 100L)
    val allocationsTaskArgs = JavaKotlinAllocationsTaskArgs(artifact = allocationsSessionArtifact)
    // The session is not alive (dead) so loadTask and thus loadCapture should be called.
    val argsSuccessfullyUsed = myJavaKotlinAllocationsTaskHandler.enter(allocationsTaskArgs)
    assertThat(argsSuccessfullyUsed).isTrue()

    // Verify that the artifact doSelect behavior is called by checking if the stage was set to MainMemoryProfilerStage.
    assertThat(myProfilers.stage).isInstanceOf(AllocationStage::class.java)
  }

  @Test
  fun testLoadTaskInvokedOnEnterWithDeadSessionAndLegacyArgs() {
    // Before enter + loadTask, the stage should not be set yet.
    assertThat(myProfilers.stage).isNotInstanceOf(MainMemoryProfilerStage::class.java)

    // Create a fake LegacyAllocationsSessionArtifact.
    val legacyAllocationsSessionArtifact = createLegacyAllocationsSessionArtifact(myProfilers, Common.Session.getDefaultInstance(), 1L,
                                                                                  100L)
    val legacyAllocationsTaskArgs = LegacyJavaKotlinAllocationsTaskArgs(artifact = legacyAllocationsSessionArtifact)
    // The session is not alive (dead) so loadTask and thus loadCapture should be called.
    val argsSuccessfullyUsed = myJavaKotlinAllocationsTaskHandler.enter(legacyAllocationsTaskArgs)
    assertThat(argsSuccessfullyUsed).isTrue()

    // Verify that the artifact doSelect behavior is called by checking if the stage was set to MainMemoryProfilerStage.
    assertThat(myProfilers.stage).isInstanceOf(MainMemoryProfilerStage::class.java)
  }

  @Test
  fun testLoadTaskWithNonNullTaskArgs() {
    // Before enter + loadTask, the stage should not be set yet.
    assertThat(myProfilers.stage).isNotInstanceOf(MainMemoryProfilerStage::class.java)

    val allocationsSessionArtifact = createAllocationSessionArtifact(myProfilers, Common.Session.getDefaultInstance(), 1L, 100L)
    val allocationsTaskArgs = JavaKotlinAllocationsTaskArgs(artifact = allocationsSessionArtifact)
    val argsSuccessfullyUsed = myJavaKotlinAllocationsTaskHandler.loadTask(allocationsTaskArgs)
    assertThat(argsSuccessfullyUsed).isTrue()

    // Verify that the artifact doSelect behavior was called by checking if the stage was set to MainMemoryProfilerStage.
    assertThat(myProfilers.stage).isInstanceOf(AllocationStage::class.java)
  }

  @Test
  fun testLoadTaskWithNonNullLegacyTaskArgs() {
    // Before enter + loadTask, the stage should not be set yet.
    assertThat(myProfilers.stage).isNotInstanceOf(MainMemoryProfilerStage::class.java)

    val legacyAllocationsSessionArtifact = createLegacyAllocationsSessionArtifact(myProfilers, Common.Session.getDefaultInstance(), 1L,
                                                                                  100L)
    val legacyAllocationsTaskArgs = LegacyJavaKotlinAllocationsTaskArgs(artifact = legacyAllocationsSessionArtifact)
    val argsSuccessfullyUsed = myJavaKotlinAllocationsTaskHandler.loadTask(legacyAllocationsTaskArgs)
    assertThat(argsSuccessfullyUsed).isTrue()

    // Verify that the artifact doSelect behavior was called by checking if the stage was set to MainMemoryProfilerStage.
    assertThat(myProfilers.stage).isInstanceOf(MainMemoryProfilerStage::class.java)
  }

  @Test
  fun testLoadTaskWithNullTaskArgsArtifact() {
    // Before enter + loadTask, the stage should not be set yet.
    assertThat(myProfilers.stage).isNotInstanceOf(MainMemoryProfilerStage::class.java)

    val exception = assertFailsWith<Throwable> {
      myJavaKotlinAllocationsTaskHandler.loadTask(JavaKotlinAllocationsTaskArgs(false, null))
    }

    assertThat(exception.message).isEqualTo(
      "There was an error with the Java/Kotlin Allocations task. Error message: The task arguments (AllocationsTaskArgs) supplied do " +
      "not contains a valid artifact to load.")

    // Verify that the artifact doSelect behavior was not called by checking if the stage was not set to MainMemoryProfilerStage.
    assertThat(myProfilers.stage).isNotInstanceOf(MainMemoryProfilerStage::class.java)
  }

  @Test
  fun testCreateArgsSuccessfully() {
    val selectedSession = Common.Session.newBuilder().setSessionId(1).setEndTimestamp(100).build()
    val sessionIdToSessionItems = mapOf(
      1L to createSessionItem(myProfilers, selectedSession, 1, listOf(
        createAllocationSessionArtifact(myProfilers, selectedSession, 1, 100))),
    )

    val allocationsTaskArgs = myJavaKotlinAllocationsTaskHandler.createArgs(false, sessionIdToSessionItems, selectedSession)
    assertThat(allocationsTaskArgs).isNotNull()
    assertThat(allocationsTaskArgs).isInstanceOf(JavaKotlinAllocationsTaskArgs::class.java)
    allocationsTaskArgs as JavaKotlinAllocationsTaskArgs
    assertThat(allocationsTaskArgs.getAllocationSessionArtifact()).isNotNull()
    assertThat(allocationsTaskArgs.getAllocationSessionArtifact()!!.artifactProto.startTime).isEqualTo(1L)
    assertThat(allocationsTaskArgs.getAllocationSessionArtifact()!!.artifactProto.endTime).isEqualTo(100L)
  }

  @Test
  fun testCreateArgsSuccessfullyWithLegacyArtifact() {
    val selectedSession = Common.Session.newBuilder().setSessionId(1).setEndTimestamp(100).build()
    val sessionIdToSessionItems = mapOf(
      1L to createSessionItem(myProfilers, selectedSession, 1, listOf(
        createLegacyAllocationsSessionArtifact(myProfilers, selectedSession, 1, 100))),
    )

    val legacyAllocationsTaskArgs = myJavaKotlinAllocationsTaskHandler.createArgs(false, sessionIdToSessionItems, selectedSession)
    assertThat(legacyAllocationsTaskArgs).isNotNull()
    assertThat(legacyAllocationsTaskArgs).isInstanceOf(LegacyJavaKotlinAllocationsTaskArgs::class.java)
    legacyAllocationsTaskArgs as LegacyJavaKotlinAllocationsTaskArgs
    assertThat(legacyAllocationsTaskArgs.getAllocationSessionArtifact()).isNotNull()
    assertThat(legacyAllocationsTaskArgs.getAllocationSessionArtifact()!!.artifactProto.startTime).isEqualTo(1L)
    assertThat(legacyAllocationsTaskArgs.getAllocationSessionArtifact()!!.artifactProto.endTime).isEqualTo(100L)
  }

  @Test
  fun testCreateArgsFailsToFindArtifact() {
    // By setting a session id that does not match any of the session items, the task artifact will not be found in the call to createArgs
    // will fail to be constructed.
    val selectedSession = Common.Session.newBuilder().setSessionId(0).setEndTimestamp(100).build()
    val sessionIdToSessionItems = mapOf(
      1L to createSessionItem(myProfilers, selectedSession, 1, listOf(
        createAllocationSessionArtifact(myProfilers, selectedSession, 1, 100))),
    )

    assertThrows(IllegalStateException::class.java) {
      myJavaKotlinAllocationsTaskHandler.createArgs(false, sessionIdToSessionItems, selectedSession)
    }
  }

  @Test
  fun testCreateStartTaskArgsNonLegacy() {
    // The non-legacy Java/Kotlin allocations task is used when the device feature level >= O.
    TaskHandlerTestUtils.startSession(ExposureLevel.DEBUGGABLE, AndroidVersion.VersionCodes.O, myProfilers, myTransportService, myTimer,
                                      Common.ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    val sessionsManager = myProfilers.sessionsManager
    val legacyArgs = myJavaKotlinAllocationsTaskHandler.createArgs(false, sessionsManager.sessionIdToSessionItems,
                                                                   sessionsManager.selectedSession)
    assertThat(legacyArgs).isInstanceOf(JavaKotlinAllocationsTaskArgs::class.java);
  }

  @Test
  fun testCreateStartTaskArgsLegacy() {
    // The legacy Java/Kotlin allocations task is used when the device feature level < O.
    TaskHandlerTestUtils.startSession(ExposureLevel.DEBUGGABLE, AndroidVersion.VersionCodes.N, myProfilers, myTransportService, myTimer,
                                      Common.ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    val sessionsManager = myProfilers.sessionsManager
    val legacyArgs = myJavaKotlinAllocationsTaskHandler.createArgs(false, sessionsManager.sessionIdToSessionItems,
                                                                   sessionsManager.selectedSession)
    assertThat(legacyArgs).isInstanceOf(LegacyJavaKotlinAllocationsTaskArgs::class.java);
  }

  @Test
  fun testSupportsDeviceAndProcess() {
    // Java/Kotlin Allocations task only checks the process support, so device used does not matter in call to supportsDeviceAndProcess.
    val device = TaskHandlerTestUtils.createDevice(1)

    val profileableProcess = TaskHandlerTestUtils.createProcess(isProfileable = true)
    assertThat(myJavaKotlinAllocationsTaskHandler.supportsDeviceAndProcess(device, profileableProcess)).isFalse()

    val debuggableProcess = TaskHandlerTestUtils.createProcess(isProfileable = false)
    assertThat(myJavaKotlinAllocationsTaskHandler.supportsDeviceAndProcess(device, debuggableProcess)).isTrue()
  }

  @Test
  fun testGetTaskName() {
    assertThat(myJavaKotlinAllocationsTaskHandler.getTaskName()).isEqualTo("Java/Kotlin Allocations")
  }
}