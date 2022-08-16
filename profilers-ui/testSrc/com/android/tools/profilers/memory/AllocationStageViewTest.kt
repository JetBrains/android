package com.android.tools.profilers.memory

import com.android.sdklib.AndroidVersion
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.formatter.TimeFormatter
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_ID
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_NAME
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS
import com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS_NAME
import com.android.tools.idea.transport.faketransport.commands.MemoryAllocTracking
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Memory
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.cpu.FakeCpuService
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.FULL
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.NONE
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.SAMPLED
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import icons.StudioIcons
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.awt.geom.Rectangle2D
import java.util.concurrent.TimeUnit

@RunWith(Parameterized::class)
class AllocationStageViewTest(private val isLive: Boolean) {
  private val timer = FakeTimer()
  private val service = FakeMemoryService()
  private val transportService = FakeTransportService(timer)

  @Rule
  @JvmField
  val grpcChannel = FakeGrpcChannel("LiveAllocationStageTestChannel", service, transportService,
                                    FakeProfilerService(timer), FakeCpuService(), FakeEventService())

  @get:Rule
  val applicationRule = ApplicationRule()

  private lateinit var profilers: StudioProfilers
  private lateinit var stage: AllocationStage
  private lateinit var mockLoader: FakeCaptureObjectLoader
  private lateinit var observer: MemoryAspectObserver
  private lateinit var ideProfilerServices: FakeIdeProfilerServices
  private lateinit var profilersView: StudioProfilersView
  private lateinit var stageView: AllocationStageView

  @Before
  fun setupBase() {
    ideProfilerServices = FakeIdeProfilerServices()
    profilers = StudioProfilers(ProfilerClient(grpcChannel.channel), ideProfilerServices, timer)
    profilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null)
    ideProfilerServices.enableEventsPipeline(true)
    mockLoader = FakeCaptureObjectLoader()
    stage =
      if (isLive) AllocationStage.makeLiveStage(profilers, mockLoader)
      else AllocationStage.makeStaticStage(profilers, minTrackingTimeUs = 1.0, maxTrackingTimeUs = 5.0)
    observer = MemoryAspectObserver(stage.aspect, stage.captureSelection.aspect)
    profilersView = StudioProfilersView(profilers, FakeIdeProfilerComponents())
    stageView = AllocationStageView(profilersView, stage)

    // Advance the clock to make sure StudioProfilers has a chance to select device + process.
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    profilers.stage = stage
  }

  @Test
  fun `stage view has a timeline and important buttons`() {
    stageView.apply {
      val descendants = (TreeWalker(component).descendants() + TreeWalker(toolbar).descendants()).toSet()
      val expected =
        if (isLive) listOf(timelineComponent, samplingMenu, selectAllButton, forceGcButton, stopButton)
        else listOf(timelineComponent, selectAllButton)
      val unexpected =
        if (isLive) listOf()
        else listOf(samplingMenu, forceGcButton, stopButton)
      assertThat(descendants).containsAllIn(expected)
      assertThat(expected.all { it.isVisible }).isTrue()
      assertThat(unexpected.all { !it.isVisible || it !in descendants })
    }
  }

  @Test
  fun `loading panel is shown only for live recording`() {
    stageView.apply {
     if (isLive) {
       assertThat(loadingPanel).isNotNull()
     } else {
       assertThat(loadingPanel).isNull()
     }
    }
  }

  @Test
  fun `sampling menu updates text when sampling mode changes`() {
    stage.liveAllocationSamplingMode = FULL
    val info = Memory.AllocationsInfo.newBuilder().setStartTime(AllocationSessionArtifactTest.TIMESTAMP1).setEndTime(
      Long.MAX_VALUE).setLegacy(false)
    val session = stage.studioProfilers.session
    transportService.addEventToStream(
      session.streamId,
      ProfilersTestData.generateMemoryAllocationInfoData(AllocationSessionArtifactTest.TIMESTAMP1, session.pid, info.build()).setIsEnded(
        false).build())
    tick()
    assertThat(stageView.samplingMenu.combobox.selectedItem).isEqualTo(FULL)

    requestSamplingRate(SAMPLED.value)
    tick()
    assertThat(stageView.samplingMenu.combobox.selectedItem).isEqualTo(SAMPLED)
  }

  @Test
  fun `back button leads to main memory stage`() {
    stage.stopTracking()
    profilersView.backButton.doClick()
    assertThat(profilers.stage).isInstanceOf(MainMemoryProfilerStage::class.java)
  }

  @Test
  fun `back button issues stop alloc tracking command`() {
    val handler = transportService.getRegisteredCommand(Commands.Command.CommandType.STOP_ALLOC_TRACKING) as MemoryAllocTracking
    val prevCommandId = handler.lastCommand.commandId
    stage.stopTracking()
    profilersView.backButton.doClick()
    assertThat(handler.lastCommand.type).isEqualTo(Commands.Command.CommandType.STOP_ALLOC_TRACKING)
    assertThat(handler.lastCommand.commandId).isGreaterThan(prevCommandId)
  }

  fun `test allocation sampling rate attachment`() {
    val device = Common.Device.newBuilder().setDeviceId(1).setFeatureLevel(AndroidVersion.VersionCodes.O).setState(
      Common.Device.State.ONLINE).build()
    val process = Common.Process.newBuilder().setDeviceId(1).setPid(2).setState(Common.Process.State.ALIVE).build()

    // Set up test data from range 0us-10us. Note that the proto timestamps are in nanoseconds.
    transportService.addEventToStream(
      device.deviceId, ProfilersTestData.generateMemoryAllocStatsData(process.pid, 0, 0).build())
    transportService.addEventToStream(
      device.deviceId, ProfilersTestData.generateMemoryAllocStatsData(process.pid, 10, 100).build())
    transportService.addEventToStream(
      device.deviceId, ProfilersTestData.generateMemoryAllocSamplingData(process.pid, 1, FULL.value).build())
    transportService.addEventToStream(
      device.deviceId, ProfilersTestData.generateMemoryAllocSamplingData(process.pid, 5, SAMPLED.value).build())
    transportService.addEventToStream(
      device.deviceId, ProfilersTestData.generateMemoryAllocSamplingData(process.pid, 8, NONE.value).build())
    transportService.addEventToStream(
      device.deviceId, ProfilersTestData.generateMemoryAllocSamplingData(process.pid, 10, FULL.value).build())

    // Set up the correct agent and session state so that the MemoryProfilerStageView can be initialized properly.
    transportService.setAgentStatus(Common.AgentData.newBuilder().setStatus(Common.AgentData.Status.ATTACHED).build())
    startSessionHelper(device, process)

    // Reset the timeline so that both data range and view range stays at (0,10) on the next tick.
    stage.timeline.reset(0, TimeUnit.MICROSECONDS.toNanos(10))
    stage.timeline.viewRange.set(0.0, 10.0)
    val view = AllocationStageView(profilersView, stage)
    // Tick a large enough time so that the renders interpolates to the final positions
    timer.tick(FakeTimer.ONE_SECOND_IN_NS * 10)
    val durationDataRenderer = view.timelineComponent.allocationSamplingRateRenderer
    val renderedRegions = durationDataRenderer.clickRegionCache
    assertThat(renderedRegions.size).isEqualTo(4)
    val iconWidth = StudioIcons.Profiler.Events.ALLOCATION_TRACKING_NONE.iconWidth.toFloat()
    val iconHeight = StudioIcons.Profiler.Events.ALLOCATION_TRACKING_NONE.iconHeight.toFloat()
    // Point should be attached due to start of FULL mode
    validateRegion(renderedRegions[0], 0.1f, 0.9f, iconWidth, iconHeight)
    // Point should be attached due to end of FULL mode
    validateRegion(renderedRegions[1], 0.5f, 0.5f, iconWidth, iconHeight)
    // Point should be detached because it's between SAMPLED and NONE modes
    validateRegion(renderedRegions[2], 0.8f, 1f, iconWidth, iconHeight)
    // Point should be attached due to start of FULL mode
    validateRegion(renderedRegions[3], 1f, 0f, iconWidth, iconHeight)
  }

  @Test
  fun `stage shows session timestamp`() {
    val elapsed = stage.minTrackingTimeUs.toLong() - TimeUnit.NANOSECONDS.toMicros(stage.studioProfilers.session.startTimestamp)
    assertThat(stageView.captureElapsedTimeLabel.text).endsWith(TimeFormatter.getSimplifiedClockString(elapsed))
  }

  @Test
  fun `controls reflect dead session`() {
    stage.studioProfilers.sessionsManager.endCurrentSession()
    stageView.apply {
      val descendants = (TreeWalker(component).descendants() + TreeWalker(toolbar).descendants()).toSet()
      listOf(stopButton, forceGcButton, samplingMenu).forEach {
        assertThat(it !in descendants || !it.isVisible)
      }
    }
  }

  private fun tick() = timer.tick(FakeTimer.ONE_SECOND_IN_NS)

  private fun requestSamplingRate(rate: Int) =
    transportService.addEventToStream(
      FAKE_DEVICE_ID, ProfilersTestData.generateMemoryAllocSamplingData(FAKE_PROCESS.pid, 1, rate).build())



  private fun startSessionHelper(device: Common.Device, process: Common.Process) {
    profilers.sessionsManager.endCurrentSession()
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    profilers.sessionsManager.beginSession(device.deviceId, device, process)
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    profilers.stage = stage
  }

  companion object {
    @Parameterized.Parameters @JvmStatic
    fun isLiveAllocationStage() = listOf(false, true)

    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }
}

private fun validateRegion(rect: Rectangle2D.Float, xStart: Float, yStart: Float, width: Float, height: Float) {
  val epsilon = 1e-6f
  assertThat(rect.x).isWithin(epsilon).of(xStart)
  assertThat(rect.y).isWithin(epsilon).of(yStart)
  assertThat(rect.width).isWithin(epsilon).of(width)
  assertThat(rect.height).isWithin(epsilon).of(height)
}
