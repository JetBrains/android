package com.android.tools.profilers.memory

import com.android.testutils.MockitoKt
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_NAME
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS_NAME
import com.android.tools.idea.transport.faketransport.commands.MemoryAllocTracking
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Memory
import com.android.tools.profiler.proto.Memory.AllocationsInfo
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerAspect
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.WithFakeTimer
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.FULL
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.SAMPLED
import com.android.tools.profilers.taskbased.home.TaskHomeTabModel.ProfilingProcessStartingPoint
import com.android.tools.profilers.taskbased.home.TaskHomeTabModel.SelectionStateOnTaskEnter
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.taskhandlers.singleartifact.memory.JavaKotlinAllocationsTaskHandler
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mockito.spy
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class AllocationStageTest(private val isLive: Boolean): WithFakeTimer {
  override val timer = FakeTimer()
  private val transportService = FakeTransportService(timer)
  @Rule @JvmField
  val grpcChannel = FakeGrpcChannel("LiveAllocationStageTestChannel", transportService)
  private lateinit var profilers: StudioProfilers
  private lateinit var stage: AllocationStage
  private lateinit var mockLoader: FakeCaptureObjectLoader
  private lateinit var observer: MemoryAspectObserver
  private lateinit var ideProfilerServices: FakeIdeProfilerServices

  @Before
  fun setup() {
    ideProfilerServices = FakeIdeProfilerServices()
    // The Task-Based UX flag will be disabled for the call to setPreferredProcess, then re-enabled. This is because the setPreferredProcess
    // method changes behavior based on the flag's value, and some of the tests depend on the behavior with the flag turned off.
    ideProfilerServices.enableTaskBasedUx(false)
    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideProfilerServices, timer)
    profilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null)
    ideProfilerServices.enableTaskBasedUx(true)
    mockLoader = FakeCaptureObjectLoader()
    stage = if (isLive) spy(AllocationStage.makeLiveStage(profilers, mockLoader))
            else AllocationStage.makeStaticStage(profilers, minTrackingTimeUs = 1.0, maxTrackingTimeUs = 5.0)
    observer = MemoryAspectObserver(stage.aspect, stage.captureSelection.aspect)
  }

  private fun setupDeviceAndStage() {
    // Advance the clock to make sure StudioProfilers has a chance to select device + process.
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    profilers.stage = stage

    if (isLive) {
      stage.liveAllocationSamplingMode = FULL
      stage.startLiveDataTimeline()
    }
  }

  @Test
  fun `agent not attached during stage enter will not start tracking in taskBasedUx`() {
    assumeTrue(isLive)
    (transportService.getRegisteredCommand(Commands.Command.CommandType.START_ALLOC_TRACKING) as MemoryAllocTracking).apply {
      trackStatus = Memory.TrackStatus.newBuilder().setStatus(Memory.TrackStatus.Status.SUCCESS).build()
    }
    // Mark agent attached as false
    MockitoKt.whenever(stage.isAgentAttached).thenReturn(false)
    ideProfilerServices.enableTaskBasedUx(true)

    // Wait for the 'update' of the studio profiler to be done
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    stage.liveAllocationSamplingMode = SAMPLED
    profilers.stage = stage
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    // Verify allocation not started for taskBasedux when the agent is not attached
    assertFalse { stage.hasStartedTracking }

    // Call stopTrackingDueToUnattachableAgent to mark agent has error and end tracking
    stage.stopTrackingDueToUnattachableAgent()
    assertTrue { stage.hasEndedTracking }
    assertTrue { stage.hasAgentError }
  }

  @Test
  fun `agent not attached during stage enter will not start tracking in taskBasedUx, on agent change tracking has started`() {
    assumeTrue(isLive)
    (transportService.getRegisteredCommand(Commands.Command.CommandType.START_ALLOC_TRACKING) as MemoryAllocTracking).apply {
      trackStatus = Memory.TrackStatus.newBuilder().setStatus(Memory.TrackStatus.Status.SUCCESS).build()
    }
    // Mark agent attached as false
    MockitoKt.whenever(stage.isAgentAttached).thenReturn(false)
    ideProfilerServices.enableTaskBasedUx(true)
    // For taskBasedUx, set the current selected task, so the process is set
    profilers.taskHomeTabModel.selectionStateOnTaskEnter = SelectionStateOnTaskEnter(
      ProfilingProcessStartingPoint.PROCESS_START, ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS)
    profilers.addTaskHandler(ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS,
                             JavaKotlinAllocationsTaskHandler(profilers.sessionsManager))

    // Wait for the 'update' of the studio profiler to be done
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    // Set the selectedTask
    stage.liveAllocationSamplingMode = SAMPLED
    profilers.stage = stage
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    // Verify allocation not started, when agent is not attached
    assertFalse { stage.hasStartedTracking }

    // Mark agent attached as true
    MockitoKt.whenever(stage.isAgentAttached).thenReturn(true)
    profilers.changed(ProfilerAspect.AGENT)
    // Wait for the transport poller to finish
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    // Allocation tracking is started
    assertTrue { stage.hasStartedTracking }
  }

  @Test
  fun `agent not attached still tracking in nonTaskBasedUx`() {
    ideProfilerServices.enableTaskBasedUx(false)

    assumeTrue(isLive)
    (transportService.getRegisteredCommand(Commands.Command.CommandType.START_ALLOC_TRACKING) as MemoryAllocTracking).apply {
      trackStatus = Memory.TrackStatus.newBuilder().setStatus(Memory.TrackStatus.Status.SUCCESS).build()
    }
    // Mark agent attached as false
    MockitoKt.whenever(stage.isAgentAttached).thenReturn(false)
    ideProfilerServices.enableTaskBasedUx(false)

    // Wait for the 'update' of the studio profiler to be done
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    stage.liveAllocationSamplingMode = SAMPLED
    profilers.stage = stage
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    // Verify if allocation started for non taskBasedUx even-though agent is not started
    assertTrue { stage.hasStartedTracking }
  }

  @Test
  fun `stage starts tracking when entered then stops when stopped`() {
    setupDeviceAndStage()
    assumeTrue(isLive)
    stage.liveAllocationSamplingMode = SAMPLED
    val handler = transportService.getRegisteredCommand(Commands.Command.CommandType.STOP_ALLOC_TRACKING) as MemoryAllocTracking
    val prevCommand = handler.lastCommand
    assertThat(stage.hasEndedTracking).isFalse()
    assertThat(stage.confirmExitMessage).isNotNull()

    stage.stopTracking()
    assertThat(stage.hasEndedTracking).isTrue()
    assertThat(stage.confirmExitMessage).isNull()
    tickOneSec()
    assertThat(stage.liveAllocationSamplingMode).isEqualTo(SAMPLED)  // Stop command doesn't change sampling mode
    assertThat(handler.lastCommand.type).isEqualTo(Commands.Command.CommandType.STOP_ALLOC_TRACKING)
    assertThat(handler.lastCommand.commandId).isNotEqualTo(prevCommand.commandId)
  }

  @Test
  fun `implicit selection of allocation artifact proto is made post recording`() {
    ideProfilerServices.enableTaskBasedUx(false)
    setupDeviceAndStage()
    // Capture a Java/Kotlin Allocation Trace
    MemoryProfilerTestUtils.startTrackingHelper(stage.parentStage, transportService, timer, 0, Memory.TrackStatus.Status.SUCCESS, false)
    MemoryProfilerTestUtils.stopTrackingHelper(stage.parentStage, transportService, timer, 0, Memory.TrackStatus.Status.SUCCESS, false)

    // Make sure the resulting artifact's proto is of AllocationsInfo type
    assertThat(profilers.sessionsManager.selectedArtifactProto).isInstanceOf(AllocationsInfo::class.java)
  }

  @Test
  fun `selected range expands over time until stopped`() {
    setupDeviceAndStage()
    assumeTrue(isLive)
    val (lo1, hi1) = tickOneSecThen(::getSelectedRange)
    val (lo2, hi2) = tickOneSecThen(::getSelectedRange)
    assertThat(lo1).isEqualTo(stage.minTrackingTimeUs)
    assertThat(lo2).isEqualTo(lo1)
    assertThat(hi2).isGreaterThan(hi1)

    stage.stopTracking()
    val (lo3, hi3) = tickOneSecThen(::getSelectedRange)
    assertThat(lo3).isEqualTo(lo1)
    assertThat(hi3).isEqualTo(hi2)
  }

  @Test
  fun `selecting all satisfies that almost all are selected`() {
    setupDeviceAndStage()
    tickOneSecThen(stage::selectAll)
    assertThat(stage.isAlmostAllSelected()).isTrue()
  }

  @Test
  fun `exiting stage disables live allocations and issues stop command`() {
    setupDeviceAndStage()
    val handler = transportService.getRegisteredCommand(Commands.Command.CommandType.STOP_ALLOC_TRACKING) as MemoryAllocTracking
    val prevCommand = handler.lastCommand
    stage.exit()
    tickOneSec()
    assertThat(handler.lastCommand.type).isEqualTo(Commands.Command.CommandType.STOP_ALLOC_TRACKING)
    assertThat(handler.lastCommand.commandId).isNotEqualTo(prevCommand.commandId)
  }

  private fun getSelectedRange() = with(stage.timeline.selectionRange) { Pair(min, max) }

  companion object {
    @Parameterized.Parameters @JvmStatic
    fun isLiveAllocationStage() = listOf(false, true)
  }
}