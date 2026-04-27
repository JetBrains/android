/*
 * Copyright (C) 2026 The Android Open Source Project
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
import com.android.tools.adtui.model.Range
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.leakcanarylib.data.Analysis
import com.android.tools.leakcanarylib.data.AnalysisSuccess
import com.android.tools.leakcanarylib.data.GcRootType
import com.android.tools.leakcanarylib.data.Leak
import com.android.tools.leakcanarylib.data.LeakTrace
import com.android.tools.leakcanarylib.data.LeakTraceNodeType
import com.android.tools.leakcanarylib.data.LeakType
import com.android.tools.leakcanarylib.data.LeakingStatus
import com.android.tools.leakcanarylib.data.Node
import com.android.tools.leakcanarylib.data.ReferencingField
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.LeakCanary
import com.android.tools.profiler.proto.LeakCanary.LeakCanaryAnalysisStatus
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.WithFakeTimer
import com.android.tools.profilers.cpu.config.LeakCanaryConfiguration
import com.android.tools.profilers.cpu.config.LeakCanaryMode
import com.android.tools.profilers.cpu.config.ProfilingConfiguration
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class LeakCanaryModelTest : WithFakeTimer {
  override val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)

  /**
   * Executes a blocking block of code (like stage.startListening()) on a background thread while continuously polling the FakeTimer and
   * transportPoller on the main thread. This prevents deadlocks when the background task awaits a transport event.
   */
  private fun runWithPolling(block: () -> Unit) {
    val executor = Executors.newSingleThreadExecutor()
    val future = executor.submit { block() }
    while (!future.isDone) {
      timer.tick(FakeTimer.ONE_SECOND_IN_NS)
      profilers.transportPoller.poll()
      Thread.sleep(10)
    }
    future.get()
    executor.shutdown()
  }

  @Rule @JvmField val grpcChannel = FakeGrpcChannel("LeakCanaryModelTestChannel", transportService)
  private lateinit var profilers: StudioProfilers
  private lateinit var stage: LeakCanaryModel
  private lateinit var ideProfilerServices: FakeIdeProfilerServices
  private lateinit var mockHeapDumper: LeakCanaryHeapDumper
  private val extraConfigs = mutableListOf<ProfilingConfiguration>()

  @Before
  fun setup() {
    ideProfilerServices =
      object : FakeIdeProfilerServices() {
        override fun getTaskCpuProfilerConfigs(apiLevel: Int): List<ProfilingConfiguration> {
          return super.getTaskCpuProfilerConfigs(apiLevel) + extraConfigs
        }
      }
    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideProfilerServices, timer)

    // Spy on profilers to mock the current process and session, which are required
    // by the LeakCanary model to attach the JVMTI agent and fetch thresholds.
    profilers = org.mockito.Mockito.spy(profilers)
    org.mockito.Mockito.`when`(profilers.process).thenReturn(FakeTransportService.FAKE_PROCESS)
    val mockSession =
      Common.Session.newBuilder()
        .setPid(FakeTransportService.FAKE_PROCESS.pid)
        .setStreamId(FakeTransportService.FAKE_DEVICE_ID)
        .setSessionId(1L)
        .setStartTimestamp(timer.currentTimeNs)
        .setEndTimestamp(Long.MAX_VALUE)
        .build()
    org.mockito.Mockito.`when`(profilers.session).thenReturn(mockSession)

    // Register default command handlers for the JVMTI agent attachment sequence
    // and device mode setup to prevent tests from failing during pre-flight checks.
    val handler = FakeLeakCanaryCommandHandler(timer, profilers, listOf(), 0)
    transportService.setCommandHandler(Commands.Command.CommandType.ATTACH_AGENT, handler)
    transportService.setCommandHandler(Commands.Command.CommandType.SET_STUDIO_LEAKCANARY_MODE, handler)
    transportService.setCommandHandler(Commands.Command.CommandType.FORCE_DUMP_LEAKCANARY_ON_DEVICE, handler)

    mockHeapDumper = mock(LeakCanaryHeapDumper::class.java)
    stage = LeakCanaryModel(profilers, mockHeapDumper)
  }

  @Test
  fun `Leak canary stage enter - success case with multiple events`() {
    val startTime = System.currentTimeMillis()
    transportService.setCommandHandler(
      Commands.Command.CommandType.START_LEAKCANARY_TASK,
      FakeLeakCanaryCommandHandler(
        timer,
        profilers,
        listOf(
          "SingleApplicationLeak.txt", // 1 application leak
          "SingleApplicationLeakAnalyzeCmd.txt", // 1 application leak
          "MultiApplicationLeak.txt", // 2 application leak with different signature
          "NoLeak.txt",
        ),
        startTime,
      ),
    )
    transportService.setCommandHandler(
      Commands.Command.CommandType.GET_LEAKCANARY_THRESHOLD,
      FakeLeakCanaryCommandHandler(timer, profilers, listOf(), startTime),
    )
    transportService.setCommandHandler(
      Commands.Command.CommandType.STOP_LEAKCANARY_TASK,
      FakeLeakCanaryCommandHandler(timer, profilers, listOf(), startTime),
    )
    transportService.setCommandHandler(
      Commands.Command.CommandType.START_LEAKCANARY_TASK,
      FakeLeakCanaryCommandHandler(
        timer,
        profilers,
        listOf(
          "SingleApplicationLeak.txt", // 1 application leak
          "SingleApplicationLeakAnalyzeCmd.txt", // 1 application leak
          "MultiApplicationLeak.txt", // 2 application leak with different signature
          "NoLeak.txt",
        ),
        startTime,
      ),
    )
    transportService.setCommandHandler(
      Commands.Command.CommandType.GET_LEAKCANARY_THRESHOLD,
      FakeLeakCanaryCommandHandler(timer, profilers, listOf(), startTime),
    )
    transportService.setCommandHandler(
      Commands.Command.CommandType.STOP_LEAKCANARY_TASK,
      FakeLeakCanaryCommandHandler(timer, profilers, listOf(), startTime),
    )
    runWithPolling { stage.startListening() }
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
    transportService.setCommandHandler(
      Commands.Command.CommandType.START_LEAKCANARY_TASK,
      FakeLeakCanaryCommandHandler(
        timer,
        profilers,
        listOf(
          "SingleApplicationLeak.txt", // 1 application leak
          "SingleApplicationLeakAnalyzeCmd.txt", // 1 application leak
          "MultiApplicationLeak.txt", // 2 application leak with different signature
          "NoLeak.txt",
        ),
        startTime,
      ),
    )
    transportService.setCommandHandler(
      Commands.Command.CommandType.GET_LEAKCANARY_THRESHOLD,
      FakeLeakCanaryCommandHandler(timer, profilers, listOf(), startTime),
    )
    transportService.setCommandHandler(
      Commands.Command.CommandType.STOP_LEAKCANARY_TASK,
      FakeLeakCanaryCommandHandler(timer, profilers, listOf(), startTime),
    )
    runWithPolling { stage.startListening() }
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
    transportService.setCommandHandler(
      Commands.Command.CommandType.START_LEAKCANARY_TASK,
      FakeLeakCanaryCommandHandler(timer, profilers, listOf("SingleApplicationLeak.txt", "InValidLeak.txt"), startTime),
    )
    transportService.setCommandHandler(
      Commands.Command.CommandType.GET_LEAKCANARY_THRESHOLD,
      FakeLeakCanaryCommandHandler(timer, profilers, listOf(), startTime),
    )
    transportService.setCommandHandler(
      Commands.Command.CommandType.STOP_LEAKCANARY_TASK,
      FakeLeakCanaryCommandHandler(timer, profilers, listOf(), startTime),
    )
    runWithPolling { stage.startListening() }
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
    transportService.setCommandHandler(
      Commands.Command.CommandType.START_LEAKCANARY_TASK,
      FakeLeakCanaryCommandHandler(timer, profilers, listOf(), startTime),
    )
    transportService.setCommandHandler(
      Commands.Command.CommandType.GET_LEAKCANARY_THRESHOLD,
      FakeLeakCanaryCommandHandler(timer, profilers, listOf(), startTime),
    )
    transportService.setCommandHandler(
      Commands.Command.CommandType.STOP_LEAKCANARY_TASK,
      FakeLeakCanaryCommandHandler(timer, profilers, listOf(), startTime),
    )
    runWithPolling { stage.startListening() }
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
    transportService.setCommandHandler(
      Commands.Command.CommandType.START_LEAKCANARY_TASK,
      FakeLeakCanaryCommandHandler(
        timer,
        profilers,
        listOf("InValidLeak.txt", "InValidLeak.txt", "InValidLeak.txt", "InValidLeak.txt"),
        startTime,
      ),
    )
    transportService.setCommandHandler(
      Commands.Command.CommandType.GET_LEAKCANARY_THRESHOLD,
      FakeLeakCanaryCommandHandler(timer, profilers, listOf(), startTime),
    )
    transportService.setCommandHandler(
      Commands.Command.CommandType.STOP_LEAKCANARY_TASK,
      FakeLeakCanaryCommandHandler(timer, profilers, listOf(), startTime),
    )
    runWithPolling { stage.startListening() }
    // Wait for listener to receive events
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    // Verify leakEvents
    assertEmpty(stage.leaks.value) // 0 event are sent
    assertEquals(true, stage.isRecording.value)
    stage.stopListening()
    val endTime = System.currentTimeMillis()
    // After stage exit we get all events
    assertEmpty(stage.leaks.value) // 0 event are sent

    val leakInfoEvents =
      LeakCanaryModel.getLeakCanaryAnalysisInfo(profilers.client, profilers.session, Range(startTime.toDouble(), endTime.toDouble()))
    assertEquals(1, leakInfoEvents.size) // Fetching only ended events
    assertEquals(Common.Event.Kind.LEAKCANARY_ANALYSIS_STATUS, leakInfoEvents[0].kind)
    assertTrue(leakInfoEvents[0].isEnded)
    assertEquals(LeakCanary.LeakCanaryAnalysisEnded.Status.SUCCESS, leakInfoEvents[0].leakCanaryAnalysisStatus.analysisEnded.status)
    assertFalse(stage.isRecording.value)
  }

  @Test
  fun `checkLeakCanaryThreshold updates state`() {
    ideProfilerServices.temporaryProfilerPreferences.setInt("LEAKCANARY_THRESHOLD", 10)

    transportService.setCommandHandler(
      Commands.Command.CommandType.START_LEAKCANARY_TASK,
      FakeLeakCanaryCommandHandler(timer, profilers, listOf(), 0),
    )
    transportService.setCommandHandler(
      Commands.Command.CommandType.STOP_LEAKCANARY_TASK,
      FakeLeakCanaryCommandHandler(timer, profilers, listOf(), 0),
    )

    stage.setLeakCanaryMode(Commands.StartLeakCanaryTaskData.LeakCanaryMode.ON_DEVICE)
    runWithPolling { stage.startListening() }
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertEquals(10, stage.retainedObjectThreshold.value)
    assertEquals(-1, ideProfilerServices.temporaryProfilerPreferences.getInt("LEAKCANARY_THRESHOLD", -1))
  }

  @Test
  fun `checkLeakCanaryThreshold fetches from command when missing`() {
    ideProfilerServices.temporaryProfilerPreferences.setInt("LEAKCANARY_THRESHOLD", -1)

    transportService.setCommandHandler(
      Commands.Command.CommandType.START_LEAKCANARY_TASK,
      FakeLeakCanaryCommandHandler(timer, profilers, listOf(), 0),
    )
    transportService.setCommandHandler(
      Commands.Command.CommandType.GET_LEAKCANARY_THRESHOLD,
      FakeLeakCanaryCommandHandler(timer, profilers, listOf(), 0, retainedObjectThreshold = 7),
    )
    transportService.setCommandHandler(
      Commands.Command.CommandType.STOP_LEAKCANARY_TASK,
      FakeLeakCanaryCommandHandler(timer, profilers, listOf(), 0),
    )

    stage.setLeakCanaryMode(Commands.StartLeakCanaryTaskData.LeakCanaryMode.ON_DEVICE)
    runWithPolling { stage.startListening() }
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)

    assertEquals(7, stage.retainedObjectThreshold.value)
    assertEquals(-1, ideProfilerServices.temporaryProfilerPreferences.getInt("LEAKCANARY_THRESHOLD", -1))
  }

  @Test
  fun `checkLeakCanaryThreshold reads from config in Studio mode`() {
    val config = LeakCanaryConfiguration("LeakCanary")
    config.source = LeakCanaryMode.STUDIO
    config.threshold = 10
    extraConfigs.add(config)

    stage.updateModeFromSettings()
    assertEquals(10, stage.retainedObjectThreshold.value)

    transportService.setCommandHandler(
      Commands.Command.CommandType.START_LEAKCANARY_TASK,
      FakeLeakCanaryCommandHandler(timer, profilers, listOf(), 0),
    )
    transportService.setCommandHandler(
      Commands.Command.CommandType.GET_LEAKCANARY_THRESHOLD,
      FakeLeakCanaryCommandHandler(timer, profilers, listOf(), 0, retainedObjectThreshold = 5),
    )
    transportService.setCommandHandler(
      Commands.Command.CommandType.STOP_LEAKCANARY_TASK,
      FakeLeakCanaryCommandHandler(timer, profilers, listOf(), 0),
    )

    runWithPolling { stage.startListening() }
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)

    // Threshold should still be 10 (from config), not 5 (from command which shouldn't run)
    assertEquals(10, stage.retainedObjectThreshold.value)
  }

  // A leaking node is found in the leak trace.
  @Test
  fun `test getLeakClassName when leaking node is found`() {
    val previousNode =
      createTestNode(className = "MainActivity", leakingStatus = LeakingStatus.NO, referenceName = "mLeakyView", isLikelyCause = true)
    val leakingNode = createTestNode(className = "LeakyView", leakingStatus = LeakingStatus.YES)
    val leakTrace = LeakTrace(GcRootType.NATIVE_STACK, nodes = listOf(previousNode, leakingNode))
    val leak =
      Leak(
        type = LeakType.APPLICATION_LEAKS,
        retainedByteSize = 1024,
        signature = "leak_signature_123",
        leakTraceCount = 1,
        displayedLeakTrace = listOf(leakTrace),
      )
    val result = LeakCanaryModel.getLeakClassName(leak)
    assertEquals("MainActivity.mLeakyView", result)
  }

  // Leaking node is UNKNOWN and it's the last node
  @Test
  fun `test getLeakClassName when leaking node is UNKNOWN and it is the last node`() {
    val node1 = createTestNode(className = "IntermediateClass", leakingStatus = LeakingStatus.NO, referenceName = "mReference")
    val unknownNode =
      createTestNode(className = "UnknownLeaker", leakingStatus = LeakingStatus.UNKNOWN, referenceName = "ReferenceName_UNKNOWN")
    val leakTrace = LeakTrace(GcRootType.NATIVE_STACK, nodes = listOf(node1, unknownNode))
    val leak =
      Leak(
        type = LeakType.APPLICATION_LEAKS,
        retainedByteSize = 700,
        signature = "leak_signature_789",
        leakTraceCount = 1,
        displayedLeakTrace = listOf(leakTrace),
      )
    val result = LeakCanaryModel.getLeakClassName(leak)
    assertEquals("IntermediateClass.mReference", result)
  }

  // The input leak object is null.
  @Test
  fun `test getLeakClassName when leak is null`() {
    val leak: Leak? = null
    val result = LeakCanaryModel.getLeakClassName(leak)
    assertEquals("", result)
  }

  // Leaking node is UNKNOWN and there's a previous NO node
  @Test
  fun `test getLeakClassName when leaking node is UNKNOWN and there is a previous NO node`() {
    val noNode = createTestNode(className = "noNode", leakingStatus = LeakingStatus.NO, referenceName = "someField")
    val yesNode = createTestNode(className = "yesNode", leakingStatus = LeakingStatus.YES)
    val unknownNode =
      createTestNode(className = "UncertainLeaker", leakingStatus = LeakingStatus.UNKNOWN, referenceName = "ReferenceName_UNKNOWN")

    val leakTrace = LeakTrace(GcRootType.NATIVE_STACK, nodes = listOf(noNode, yesNode, unknownNode))
    val leak =
      Leak(
        type = LeakType.LIBRARY_LEAKS,
        retainedByteSize = 900,
        signature = "leak_signature_abc",
        leakTraceCount = 1,
        displayedLeakTrace = listOf(leakTrace),
      )
    val result = LeakCanaryModel.getLeakClassName(leak)
    assertEquals("noNode.someField", result)
  }

  // Leaking node is UNKNOWN and there's a YES node after UNKNOWN node
  @Test
  fun `test getLeakClassName when leaking node is UNKNOWN and there is a YES node after UNKNOWN`() {
    val previousNoNode = createTestNode(className = "AnotherPrevious", leakingStatus = LeakingStatus.NO, referenceName = "someField")
    val unknownNode =
      createTestNode(className = "UncertainLeaker", leakingStatus = LeakingStatus.UNKNOWN, referenceName = "ReferenceName_UNKNOWN")
    val nextNode = createTestNode(className = "NextNodeInTrace", leakingStatus = LeakingStatus.YES)
    val leakTrace = LeakTrace(GcRootType.NATIVE_STACK, nodes = listOf(previousNoNode, unknownNode, nextNode))
    val leak =
      Leak(
        type = LeakType.LIBRARY_LEAKS,
        retainedByteSize = 900,
        signature = "leak_signature_abc",
        leakTraceCount = 1,
        displayedLeakTrace = listOf(leakTrace),
      )
    val result = LeakCanaryModel.getLeakClassName(leak)
    assertEquals("AnotherPrevious.someField", result)
  }

  @Test
  fun `test getLeakingFullClassName finds node with YES status`() {
    val node1 = createTestNode(className = "NotLeaking", leakingStatus = LeakingStatus.NO)
    val node2 = createTestNode(className = "LeakingVictim", leakingStatus = LeakingStatus.YES)
    val node3 = createTestNode(className = "AlsoLeaking", leakingStatus = LeakingStatus.YES)
    val leakTrace = LeakTrace(GcRootType.NATIVE_STACK, nodes = listOf(node1, node2, node3))
    val leak =
      Leak(
        type = LeakType.APPLICATION_LEAKS,
        retainedByteSize = 100,
        signature = "sig",
        leakTraceCount = 1,
        displayedLeakTrace = listOf(leakTrace),
      )

    assertEquals("LeakingVictim", LeakCanaryModel.getLeakingFullClassName(leak))
  }

  @Test
  fun `test getAnchorFullClassName finds last node with NO status`() {
    val node1 = createTestNode(className = "Root", leakingStatus = LeakingStatus.NO)
    val node2 = createTestNode(className = "Anchor", leakingStatus = LeakingStatus.NO)
    val node3 = createTestNode(className = "Unknown", leakingStatus = LeakingStatus.UNKNOWN)
    val node4 = createTestNode(className = "Leaking", leakingStatus = LeakingStatus.YES)
    val leakTrace = LeakTrace(GcRootType.NATIVE_STACK, nodes = listOf(node1, node2, node3, node4))
    val leak =
      Leak(
        type = LeakType.APPLICATION_LEAKS,
        retainedByteSize = 100,
        signature = "sig",
        leakTraceCount = 1,
        displayedLeakTrace = listOf(leakTrace),
      )

    assertEquals("Anchor", LeakCanaryModel.getAnchorFullClassName(leak))
  }

  @Test
  fun `test getAnchorFullClassName returns empty if no NO status nodes`() {
    val node1 = createTestNode(className = "Unknown", leakingStatus = LeakingStatus.UNKNOWN)
    val node2 = createTestNode(className = "Leaking", leakingStatus = LeakingStatus.YES)
    val leakTrace = LeakTrace(GcRootType.NATIVE_STACK, nodes = listOf(node1, node2))
    val leak =
      Leak(
        type = LeakType.APPLICATION_LEAKS,
        retainedByteSize = 100,
        signature = "sig",
        leakTraceCount = 1,
        displayedLeakTrace = listOf(leakTrace),
      )

    assertEquals("", LeakCanaryModel.getAnchorFullClassName(leak))
  }

  @Test
  fun `analyzeLeakWithStudioBot delegates to ideServices`() {
    val leak = mock(Leak::class.java)
    `when`(leak.toString()).thenReturn("trace")
    stage.analyzeLeakWithStudioBot(leak)
    assertEquals("trace", ideProfilerServices.lastLeakRawTrace)
    assertEquals<Leak?>(leak, ideProfilerServices.lastLeak)
  }

  @Test
  fun `manual leak parsing and adding to leaks`() {
    val file = TestUtils.resolveWorkspacePath("${FakeLeakCanaryCommandHandler.TEST_DATA_PATH}/SingleApplicationLeak.txt").toFile()
    val rawTrace = file.readText()

    val analysis = Analysis.fromString(rawTrace)
    assertTrue(analysis is AnalysisSuccess)
    stage.addLeaks(analysis.leaks)

    assertEquals(1, stage.leaks.value.size)
    assertEquals("androidx.constraintlayout.widget.ConstraintLayout", stage.leaks.value[0].displayedLeakTrace[0].nodes.last().className)
  }

  @Test
  fun `requestStopRecording with retained objects triggers dump and waits`() {
    // Setup command handlers to avoid errors
    transportService.setCommandHandler(
      Commands.Command.CommandType.START_LEAKCANARY_TASK,
      FakeLeakCanaryCommandHandler(timer, profilers, listOf(), 0),
    )
    transportService.setCommandHandler(
      Commands.Command.CommandType.STOP_LEAKCANARY_TASK,
      FakeLeakCanaryCommandHandler(timer, profilers, listOf(), 0),
    )
    transportService.setCommandHandler(
      Commands.Command.CommandType.GET_LEAKCANARY_THRESHOLD,
      FakeLeakCanaryCommandHandler(timer, profilers, listOf(), 0),
    )

    runWithPolling { stage.startListening() }
    stage.setObjectRetainedCount(1)
    stage.setAnalysisProgress(0)

    stage.leakcanaryMode = Commands.StartLeakCanaryTaskData.LeakCanaryMode.ON_HOST

    // Request stop
    stage.requestStopRecording()

    // Verify triggerAndAnalyze was called
    verify(mockHeapDumper).triggerAndAnalyze()

    // Verify stopping state
    assertTrue(stage.isStopping.value)
    assertTrue(stage.isRecording.value)

    // Simulate analysis success event
    val analysisEvent = FakeLeakCanaryCommandHandler.getLeakCanaryEvent(profilers, "SingleApplicationLeak.txt")
    transportService.addEventToStream(profilers.session.streamId, analysisEvent)

    // Tick to process event
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)

    // Verify recording stopped
    assertFalse(stage.isRecording.value)
    assertFalse(stage.isStopping.value)
  }

  @Test
  fun `requestStopRecording with no retained objects stops immediately`() {
    // Setup
    transportService.setCommandHandler(
      Commands.Command.CommandType.START_LEAKCANARY_TASK,
      FakeLeakCanaryCommandHandler(timer, profilers, listOf(), 0),
    )
    transportService.setCommandHandler(
      Commands.Command.CommandType.STOP_LEAKCANARY_TASK,
      FakeLeakCanaryCommandHandler(timer, profilers, listOf(), 0),
    )
    transportService.setCommandHandler(
      Commands.Command.CommandType.GET_LEAKCANARY_THRESHOLD,
      FakeLeakCanaryCommandHandler(timer, profilers, listOf(), 0),
    )

    runWithPolling { stage.startListening() }
    stage.setObjectRetainedCount(0)

    stage.requestStopRecording()

    // Should stop immediately
    assertFalse(stage.isRecording.value)
    assertFalse(stage.isStopping.value)
  }

  private fun createTestNode(
    className: String,
    leakingStatus: LeakingStatus = LeakingStatus.UNKNOWN,
    referenceName: String? = null,
    isLikelyCause: Boolean = false,
  ): Node {
    val referencingField =
      referenceName?.let {
        ReferencingField(
          className = className,
          type = ReferencingField.ReferencingFieldType.STATIC_FIELD,
          isLikelyCause = isLikelyCause,
          referenceName = it,
        )
      }
    return Node(
      nodeType = LeakTraceNodeType.INSTANCE,
      className = className,
      leakingStatus = leakingStatus,
      leakingStatusReason = "",
      retainedHeapSize = "2 KB",
      retainedObjectCount = 2024,
      notes = emptyList(),
      referencingField = referencingField,
    )
  }
}

class FakeLeakCanaryCommandHandler(
  timer: FakeTimer,
  val profilers: StudioProfilers,
  val leaksToSendFiles: List<String>,
  val startTimestamp: Long,
  val isLeakCanaryPresent: Boolean = true,
  val retainedObjectThreshold: Int = 5,
) : CommandHandler(timer) {
  override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {

    when (command.type) {
      Commands.Command.CommandType.START_LEAKCANARY_TASK -> {
        // Start tracking info event
        events.add(
          Common.Event.newBuilder()
            .setGroupId(profilers.session.pid.toLong())
            .setPid(profilers.session.pid)
            .setIsEnded(false)
            .setKind(Common.Event.Kind.LEAKCANARY_ANALYSIS_STATUS)
            .setLeakCanaryAnalysisStatus(
              LeakCanaryAnalysisStatus.newBuilder()
                .setAnalysisStarted(LeakCanary.LeakCanaryAnalysisStarted.newBuilder().setTimestamp(startTimestamp).build())
                .build()
            )
            .setTimestamp(startTimestamp)
            .build()
        )
        leaksToSendFiles.forEach { leakToSendFile -> events.add(getLeakCanaryEvent(profilers, leakToSendFile)) }
      }

      Commands.Command.CommandType.STOP_LEAKCANARY_TASK -> {
        // Stop tracking info event
        events.add(
          Common.Event.newBuilder()
            .setGroupId(profilers.session.pid.toLong())
            .setPid(profilers.session.pid)
            .setIsEnded(true)
            .setKind(Common.Event.Kind.LEAKCANARY_ANALYSIS_STATUS)
            .setLeakCanaryAnalysisStatus(
              LeakCanaryAnalysisStatus.newBuilder()
                .setAnalysisEnded(
                  LeakCanary.LeakCanaryAnalysisEnded.newBuilder()
                    .setStatus(LeakCanary.LeakCanaryAnalysisEnded.Status.SUCCESS)
                    .setStartTimestamp(startTimestamp)
                    .setEndTimestamp(System.currentTimeMillis())
                    .build()
                )
                .build()
            )
            .setTimestamp(System.currentTimeMillis())
            .build()
        )
      }

      Commands.Command.CommandType.GET_LEAKCANARY_THRESHOLD -> {
        events.add(
          Common.Event.newBuilder()
            .setPid(profilers.session.pid)
            .setCommandId(command.commandId)
            .setTimestamp(System.currentTimeMillis())
            .setKind(Common.Event.Kind.LEAKCANARY_THRESHOLD)
            .setLeakcanaryThreshold(Common.LeakCanaryThresholdData.newBuilder().setThreshold(retainedObjectThreshold).build())
            .build()
        )
      }

      Commands.Command.CommandType.ATTACH_AGENT -> {
        events.add(
          Common.Event.newBuilder()
            .setGroupId(profilers.session.pid.toLong())
            .setPid(profilers.session.pid)
            .setKind(Common.Event.Kind.AGENT)
            .setAgentData(Common.AgentData.newBuilder().setStatus(Common.AgentData.Status.ATTACHED).build())
            .setTimestamp(System.currentTimeMillis())
            .build()
        )
      }

      else -> {}
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
        .setKind(Common.Event.Kind.LEAKCANARY_ANALYSIS)
        .setLeakcanaryAnalysis(LeakCanary.LeakCanaryAnalysisData.newBuilder().setData(fileContent).build())
        .setTimestamp(currentTime)
        .build()
    }
  }
}
